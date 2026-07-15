package dev.sandbox.lab.nodeapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.docker.Container;
import com.pulumi.docker.ContainerArgs;
import com.pulumi.docker.Image;
import com.pulumi.docker.ImageArgs;
import com.pulumi.docker.inputs.ContainerPortArgs;
import com.pulumi.docker.inputs.DockerBuildArgs;

// Same shape as task-api's infra, just building a Node image instead of a Java one - Pulumi's
// Docker provider doesn't care what's inside the Dockerfile it's building. No Floci/AWS
// emulation here at all, same reasoning as task-api: this is just a container listening on a
// port, it needs a container runtime, not an AWS stand-in.
public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var image = new Image("nodeApiImage", ImageArgs.builder()
                    .imageName("node-api:local")
                    .build(DockerBuildArgs.builder()
                            .context("../app")
                            .dockerfile("../app/Dockerfile")
                            .build())
                    .skipPush(true)
                    .build());

            var container = new Container("nodeApiContainer", ContainerArgs.builder()
                    // repoDigest, not imageName - see task-api's identical comment on why:
                    // imageName is a stable tag, rebuilding under the same tag produces no diff
                    // Pulumi can see, so the old container silently keeps running otherwise.
                    .image(image.repoDigest())
                    .ports(ContainerPortArgs.builder()
                            .internal(8086)
                            .external(8086)
                            .build())
                    .restart("unless-stopped")
                    .build());

            ctx.export("containerName", container.name());
            ctx.export("url", Output.of("http://localhost:8086/notices"));
        });
    }
}
