package dev.sandbox.lab.attachmentsapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.aws.s3.BucketV2;
import com.pulumi.aws.s3.BucketV2Args;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // BucketV2 is the current aws.s3 resource shape (the provider's older Bucket
            // resource bundled versioning/lifecycle/CORS/etc. as inline arguments; BucketV2
            // splits those into their own separate resources instead - not needed for this
            // sample, so just the bucket itself).
            // forceDestroy(true): without it, `pulumi destroy` fails outright if any object was
            // ever left in the bucket (e.g. an interrupted deploy.sh run) - S3 buckets refuse to
            // delete non-empty, and Pulumi doesn't empty one for you by default. Since this
            // bucket's whole content is disposable demo data, forcing it is the right call here
            // (a real production bucket holding anything worth keeping would want the opposite).
            var bucket = new BucketV2("attachmentsBucket", BucketV2Args.builder()
                    .bucket("claim-attachments-sandbox")
                    .forceDestroy(true)
                    .build());

            ctx.export("bucketName", bucket.bucket());
        });
    }
}
