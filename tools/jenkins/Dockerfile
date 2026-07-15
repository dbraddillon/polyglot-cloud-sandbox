FROM jenkins/jenkins:lts-jdk21

# Debian base (bookworm) under the hood - apt, not apk/yum. Maven isn't bundled with the base
# image even though the JDK is; add it so the pipeline can run `mvn` directly on Jenkins' own
# built-in node without needing a separate build-agent container.
USER root
RUN apt-get update && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*
USER jenkins

# Skip the interactive setup wizard entirely - Configuration as Code (JCasC) below defines the
# admin user and seeds the pipeline job instead, so `deploy.sh` produces a fully working Jenkins
# with zero browser clicking, same "single command, no manual last-mile step" bar as every other
# sample's deploy.sh in this repo.
# Also disables CSRF crumb-checking - this Jenkins is local-only, never internet-exposed, and
# deploy.sh triggers the seeded build over the REST API immediately after boot; crumb handling
# adds a real security control that's actively counterproductive for a throwaway local sandbox.
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true"
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc.yaml

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY casc.yaml /var/jenkins_home/casc.yaml
