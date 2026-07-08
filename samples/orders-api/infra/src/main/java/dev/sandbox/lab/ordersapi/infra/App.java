package dev.sandbox.lab.ordersapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.docker.Container;
import com.pulumi.docker.ContainerArgs;
import com.pulumi.docker.Image;
import com.pulumi.docker.ImageArgs;
import com.pulumi.docker.Network;
import com.pulumi.docker.NetworkArgs;
import com.pulumi.docker.inputs.ContainerNetworksAdvancedArgs;
import com.pulumi.docker.inputs.ContainerPortArgs;
import com.pulumi.docker.inputs.DockerBuildArgs;
import com.pulumi.resources.CustomResourceOptions;

import java.util.List;

// Two containers this time, not one - the app and its database, on their own Docker network so
// they can reach each other by container name instead of by IP or the host's published ports.
// This is the closest local-sandbox equivalent of a Deployment + a database StatefulSet sharing
// a Kubernetes Service DNS name, or two services in the same docker-compose.yml.
public class App {
    private static final String DB_NAME = "orders";
    private static final String DB_USER = "orders";
    private static final String DB_PASSWORD = "orders";

    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // The default Docker "bridge" network doesn't do DNS resolution by container name -
            // only a user-defined network like this one does. Passing network.name() (an
            // Output) into the containers below, rather than the literal string, is what tells
            // Pulumi those containers depend on the network existing first.
            var network = new Network("ordersNetwork", NetworkArgs.builder()
                    .name("orders-network")
                    .build());

            var db = new Container("ordersDb", ContainerArgs.builder()
                    .name("orders-db")
                    .image("postgres:16-alpine")
                    .envs(
                            "POSTGRES_DB=" + DB_NAME,
                            "POSTGRES_USER=" + DB_USER,
                            "POSTGRES_PASSWORD=" + DB_PASSWORD)
                    .networksAdvanced(ContainerNetworksAdvancedArgs.builder()
                            .name(network.name())
                            .build())
                    .ports(ContainerPortArgs.builder()
                            .internal(5432)
                            .external(5433)
                            .build())
                    .restart("unless-stopped")
                    .build());

            var image = new Image("ordersApiImage", ImageArgs.builder()
                    .imageName("orders-api:local")
                    .build(DockerBuildArgs.builder()
                            .context("../app")
                            .dockerfile("../app/Dockerfile")
                            .build())
                    .skipPush(true)
                    .build());

            // Threading db.name() through into these env vars (rather than the literal
            // "orders-db") is what makes Pulumi start the database before the app - the same
            // dependency-via-Output trick as the network above.
            //
            // Known race, left as-is on purpose: Postgres takes a few seconds to actually
            // accept connections after its container starts, and nothing here blocks the app
            // container until that happens - the Terraform-bridged Docker provider Pulumi uses
            // has no "wait until healthy" dependency the way docker-compose's
            // `depends_on: condition: service_healthy` does. Spring Boot will fail fast if the
            // database isn't reachable yet, but `restart: unless-stopped` below means Docker
            // just restarts the app container a few seconds later, by which point Postgres is
            // up. This is the same class of problem Kubernetes readiness probes exist to solve.
            var appEnvs = db.name().applyValue(dbName -> List.of(
                    "SPRING_DATASOURCE_URL=jdbc:postgresql://" + dbName + ":5432/" + DB_NAME,
                    "SPRING_DATASOURCE_USERNAME=" + DB_USER,
                    "SPRING_DATASOURCE_PASSWORD=" + DB_PASSWORD));

            var app = new Container("ordersApiApp", ContainerArgs.builder()
                    .name("orders-api-app")
                    // repoDigest, not imageName: imageName is the mutable tag ("orders-api:local")
                    // - rebuilding produces new content under the same tag, so Pulumi sees no
                    // diff on this field and won't recreate the container. repoDigest changes
                    // with the content, so a rebuild actually triggers a container replace.
                    // (Found by rebuilding after a code fix and watching the old code keep
                    // running - `docker restart` doesn't help either, since that reuses the
                    // already-extracted container, not the newly built image.)
                    .image(image.repoDigest())
                    .envs(appEnvs)
                    .networksAdvanced(ContainerNetworksAdvancedArgs.builder()
                            .name(network.name())
                            .build())
                    .ports(ContainerPortArgs.builder()
                            .internal(8083)
                            .external(8083)
                            .build())
                    .restart("unless-stopped")
                    .build(), CustomResourceOptions.builder()
                    .dependsOn(db)
                    .build());

            ctx.export("appUrl", Output.of("http://localhost:8083/orders"));
        });
    }
}
