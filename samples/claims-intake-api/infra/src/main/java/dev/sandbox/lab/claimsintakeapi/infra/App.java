package dev.sandbox.lab.claimsintakeapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.aws.kinesis.Stream;
import com.pulumi.aws.kinesis.StreamArgs;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // Single shard, provisioned (not on-demand) mode - plenty for a sandbox CSV firehose,
            // and provisioned mode is the classic/well-supported Kinesis API shape most likely to
            // behave predictably against an emulator (confirmed against Floci directly before
            // building the rest of this sample - see the README).
            var stream = new Stream("claimsIntakeStream", StreamArgs.builder()
                    .name("claims-intake-stream")
                    .shardCount(1)
                    .build());

            ctx.export("streamName", stream.name());
        });
    }
}
