#!/usr/bin/env bash
# Runs a local JFrog Artifactory OSS + Postgres, then actually publishes task-api's real jar to
# it via `mvn deploy:deploy-file` and pulls it back down to verify byte-for-byte, the same
# "prove it, don't assume it" bar as every other sample/tool in this repo. Local tool setup, not
# a "sample" - see tools/jenkins/ for the same distinction.
set -euo pipefail
cd "$(dirname "$0")"

NETWORK="polyglot-sandbox-artifactory-net"
DB_CONTAINER="polyglot-sandbox-artifactory-db"
APP_CONTAINER="polyglot-sandbox-artifactory"
DB_PASSWORD="${ARTIFACTORY_DB_PASSWORD:-artifactory-sandbox}"
ADMIN_PASSWORD="password" # Artifactory OSS's fixed default admin password - see README

docker network create "$NETWORK" >/dev/null 2>&1 || true
docker rm -f "$DB_CONTAINER" "$APP_CONTAINER" >/dev/null 2>&1 || true

# Current Artifactory OSS (7.111.x) refuses to start on its own embedded Derby DB at all - real
# Postgres is mandatory now, not optional. See README.
docker run -d --name "$DB_CONTAINER" \
  --network "$NETWORK" \
  -e POSTGRES_DB=artifactory \
  -e POSTGRES_USER=artifactory \
  -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  postgres:16 >/dev/null

# Artifactory refuses to boot at all without a pre-existing master key - generate one fresh each
# run, matching this tool's "destroy.sh wipes everything, deploy.sh starts clean" convention.
rm -rf var
mkdir -p var/etc/security
printf '%s' "$(openssl rand -hex 16)" > var/etc/security/master.key
chmod -R 777 var

docker run -d --name "$APP_CONTAINER" \
  --network "$NETWORK" \
  -p 8081:8081 -p 8082:8082 \
  -v "$(pwd)/var":/var/opt/jfrog/artifactory \
  -e JF_SHARED_DATABASE_TYPE=postgresql \
  -e JF_SHARED_DATABASE_DRIVER=org.postgresql.Driver \
  -e JF_SHARED_DATABASE_URL="jdbc:postgresql://$DB_CONTAINER:5432/artifactory" \
  -e JF_SHARED_DATABASE_USERNAME=artifactory \
  -e JF_SHARED_DATABASE_PASSWORD="$DB_PASSWORD" \
  releases-docker.jfrog.io/jfrog/artifactory-oss:latest >/dev/null

echo "Waiting for Artifactory to come up (first boot takes a couple of minutes)..."
until curl -sf -o /dev/null http://localhost:8082/artifactory/api/system/ping 2>/dev/null; do
  sleep 5
done
echo "Artifactory running at http://localhost:8082/artifactory (user: admin / password: $ADMIN_PASSWORD)"

# OSS's repository-management REST API is Pro-gated (confirmed directly - every repo create/
# update call 400s with "available only in Artifactory Pro"), so this uses the repo OSS actually
# ships with out of the box (example-repo-local) rather than fighting that wall. It's a generic-
# type repo, not Maven-typed, but that only affects UI/metadata niceties - it accepts a real
# Maven-layout deploy/retrieve identically. See README.
REPO="example-repo-local"

echo "Building task-api's jar..."
( cd ../../samples/task-api/app && mvn -q clean package -DskipTests )

JAR=../../samples/task-api/app/target/app.jar
SETTINGS=var/settings.xml
cat > "$SETTINGS" <<EOF
<settings>
  <servers>
    <server>
      <id>sandbox-artifactory</id>
      <username>admin</username>
      <password>$ADMIN_PASSWORD</password>
    </server>
  </servers>
</settings>
EOF

echo "Publishing task-api's jar to Artifactory..."
mvn --settings "$SETTINGS" -q deploy:deploy-file \
  -Dfile="$JAR" \
  -DgroupId=dev.sandbox.lab.taskapi \
  -DartifactId=app \
  -Dversion=1.0.0 \
  -Dpackaging=jar \
  -DrepositoryId=sandbox-artifactory \
  -Durl="http://localhost:8082/artifactory/$REPO"

echo "Pulling it back down to verify..."
curl -s -u "admin:$ADMIN_PASSWORD" -o var/downloaded-app.jar \
  "http://localhost:8082/artifactory/$REPO/dev/sandbox/lab/taskapi/app/1.0.0/app-1.0.0.jar"

LOCAL_SHA=$(shasum -a 256 "$JAR" | cut -d' ' -f1)
DOWNLOADED_SHA=$(shasum -a 256 var/downloaded-app.jar | cut -d' ' -f1)

if [ "$LOCAL_SHA" = "$DOWNLOADED_SHA" ]; then
  echo "Verified: downloaded jar matches the built jar byte-for-byte (sha256 $LOCAL_SHA)."
else
  echo "MISMATCH: local sha256 $LOCAL_SHA != downloaded sha256 $DOWNLOADED_SHA" >&2
  exit 1
fi
