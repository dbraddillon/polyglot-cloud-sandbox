package dev.sandbox.lab.searchapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.docker.Container;
import com.pulumi.docker.ContainerArgs;
import com.pulumi.docker.inputs.ContainerPortArgs;

// Originally this provisioned a real aws.opensearch.Domain against Floci - and Floci does run
// a genuine OpenSearch container for that (confirmed by watching it pull and start the real
// opensearchproject/opensearch image). The problem: Floci's control-plane never flips the
// domain's `Created` status to true, and Pulumi's AWS provider waits on exactly that before
// considering the create finished - so `pulumi up` hung indefinitely even though the actual
// engine was healthy within about two minutes. That's a confirmed Floci limitation (v1.5.31),
// not a Pulumi or Java issue.
//
// Rather than fight it, this runs the same OpenSearch image directly via Pulumi's Docker
// provider instead - the exact pattern claims-api already uses for Postgres instead of Floci's
// RDS emulation. Same engine, same REST API, no AWS-shaped control-plane in the way.
public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var opensearch = new Container("searchOpensearch", ContainerArgs.builder()
                    .name("search-api-opensearch")
                    .image("opensearchproject/opensearch:2.11.1")
                    .envs(
                            "discovery.type=single-node",
                            "DISABLE_SECURITY_PLUGIN=true",
                            "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m")
                    .ports(ContainerPortArgs.builder()
                            .internal(9200)
                            .external(9201)
                            .build())
                    .restart("unless-stopped")
                    .build());

            ctx.export("containerName", opensearch.name());
            ctx.export("baseUrl", Output.of("http://localhost:9201"));
        });
    }
}
