package dev.sandbox.lab.taskapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.core.Output;
import com.pulumi.docker.Container;
import com.pulumi.docker.ContainerArgs;
import com.pulumi.docker.Image;
import com.pulumi.docker.ImageArgs;
import com.pulumi.docker.inputs.ContainerPortArgs;
import com.pulumi.docker.inputs.DockerBuildArgs;

// This sample's infra is deliberately different from hello-api's: no Floci/AWS emulation here
// at all. Pulumi's Docker provider talks straight to the local Docker daemon (the same one
// Colima already exposes) - build an image, run a container, done. This is the "containerize
// and run it like you would an ASP.NET Core Web API" story; wiring the same built image into a
// real Kubernetes Deployment/Service is the natural next step once a local cluster exists (see
// this sample's README).
public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // Builds ../app's Dockerfile straight into the local Docker daemon, no registry
            // involved. skipPush(true) is what makes that local-only - without it the provider
            // assumes you're pushing somewhere and fails without a registry configured.
            // Note the two same-named `.build(...)` calls below: one sets the build config
            // (takes a DockerBuildArgs), the other (no args) finalizes the ImageArgs builder
            // itself - Java resolves them by argument count, but it reads oddly the first time.
            var image = new Image("taskApiImage", ImageArgs.builder()
                    .imageName("task-api:local")
                    .build(DockerBuildArgs.builder()
                            .context("../app")
                            .dockerfile("../app/Dockerfile")
                            .build())
                    .skipPush(true)
                    .build());

            var container = new Container("taskApiContainer", ContainerArgs.builder()
                    .image(image.imageName())
                    .ports(ContainerPortArgs.builder()
                            .internal(8080)
                            .external(8080)
                            .build())
                    .restart("unless-stopped")
                    .build());

            ctx.export("containerName", container.name());
            ctx.export("url", Output.of("http://localhost:8080/tasks"));
        });
    }
}
