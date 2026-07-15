package dev.sandbox.lab.taskapi.infra;

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

// This sample's infra is deliberately different from hello-api's: no Floci/AWS emulation here
// at all. Pulumi's Docker provider talks straight to the local Docker daemon (the same one
// Colima already exposes) - build an image, run a container, done. This is the "containerize
// and run it like you would an ASP.NET Core Web API" story; wiring the same built image into a
// real Kubernetes Deployment/Service is the natural next step once a local cluster exists (see
// this sample's README).
public class App {
    // Datadog OSS has no free-tier local mode - the agent always needs an API key to boot, and
    // real metrics delivery to an actual dashboard needs a real one. This dummy key lets the
    // agent run and DogStatsD genuinely receive/count metrics locally either way (confirmed
    // directly - `agent status` shows real packet counts even with a fake key); a real key just
    // gets you a live dashboard on top of the same wiring. Opt-in via env var, same convention as
    // this repo's "Real AWS as an opt-in path" - never assumed, never hardcoded.
    private static final String DEFAULT_DD_API_KEY = "0000000000000000000000000000dead";

    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // The default Docker "bridge" network doesn't do DNS resolution by container name -
            // only a user-defined network like this one does. See claims-api's infra for the
            // same pattern (there: app + Postgres; here: app + the Datadog agent).
            var network = new Network("taskApiNetwork", NetworkArgs.builder()
                    .name("task-api-network")
                    .build());

            String ddApiKey = System.getenv().getOrDefault("DD_API_KEY", DEFAULT_DD_API_KEY);
            var datadogAgent = new Container("taskApiDatadogAgent", ContainerArgs.builder()
                    .name("task-api-datadog-agent")
                    .image("gcr.io/datadoghq/agent:latest")
                    .envs(
                            "DD_API_KEY=" + ddApiKey,
                            "DD_SITE=datadoghq.com",
                            // Without an explicit hostname the agent fails outright inside a
                            // container ("unable to reliably determine the host name") - found by
                            // reading its own logs, not documented anywhere obvious.
                            "DD_HOSTNAME=task-api-sandbox",
                            // DogStatsD only accepts traffic from its own container by default;
                            // this is what lets task-api's separate container reach it.
                            "DD_DOGSTATSD_NON_LOCAL_TRAFFIC=true")
                    .networksAdvanced(ContainerNetworksAdvancedArgs.builder()
                            .name(network.name())
                            .build())
                    .restart("unless-stopped")
                    .build());

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

            var appEnvs = datadogAgent.name().applyValue(agentName -> List.of(
                    "DD_AGENT_HOST=" + agentName));

            var container = new Container("taskApiContainer", ContainerArgs.builder()
                    // repoDigest, not imageName: imageName is the mutable tag ("task-api:local")
                    // - rebuilding produces new content under the same tag, so Pulumi sees no
                    // diff on this field and won't recreate the container. repoDigest changes
                    // with the content, so a rebuild actually triggers a container replace.
                    .image(image.repoDigest())
                    .envs(appEnvs)
                    .networksAdvanced(ContainerNetworksAdvancedArgs.builder()
                            .name(network.name())
                            .build())
                    .ports(ContainerPortArgs.builder()
                            .internal(8080)
                            .external(8080)
                            .build())
                    .restart("unless-stopped")
                    .build(), CustomResourceOptions.builder()
                    .dependsOn(datadogAgent)
                    .build());

            ctx.export("containerName", container.name());
            ctx.export("url", Output.of("http://localhost:8080/tasks"));
        });
    }
}
