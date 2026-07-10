package dev.sandbox.lab.eventsapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.aws.sns.Topic;
import com.pulumi.aws.sns.TopicArgs;
import com.pulumi.aws.sns.TopicSubscription;
import com.pulumi.aws.sns.TopicSubscriptionArgs;
import com.pulumi.aws.sqs.Queue;
import com.pulumi.aws.sqs.QueueArgs;
import com.pulumi.aws.sqs.QueuePolicy;
import com.pulumi.aws.sqs.QueuePolicyArgs;
import com.pulumi.aws.sqs.inputs.PolicyDocumentArgs;
import com.pulumi.core.Either;
import com.pulumi.core.Output;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var topic = new Topic("eventsTopic", TopicArgs.builder()
                    .name("events-topic")
                    .build());

            // Dead-letter queue: a message that fails processing enough times (maxReceiveCount)
            // lands here instead of retrying forever or vanishing. No consumer reads it in this
            // sandbox - the point is demonstrating that a redrive target exists at all, the same
            // way you'd want one on any real at-least-once consumer.
            var dlq = new Queue("eventsDlq", QueueArgs.builder()
                    .name("events-dlq")
                    .build());

            Output<String> redrivePolicy = dlq.arn().applyValue(dlqArn -> """
                    {"deadLetterTargetArn":"%s","maxReceiveCount":3}
                    """.formatted(dlqArn).strip());

            var queue = new Queue("eventsQueue", QueueArgs.builder()
                    .name("events-queue")
                    .redrivePolicy(redrivePolicy)
                    .build());

            // A real AWS account needs the queue's own policy to explicitly allow the topic to
            // deliver to it - without this, SNS publishes would silently never reach the queue.
            // Floci may not actually enforce IAM policies like this (most local emulators skip
            // that layer entirely), but leaving it out would make this incorrect IaC for real
            // AWS, and the point here is writing it the way it actually has to work in
            // production, not the minimum that happens to work against Floci.
            // .policy(...) wants Output<Either<String, PolicyDocumentArgs>> - Either is Pulumi's
            // "this field accepts a raw JSON string OR a structured args object" type, since the
            // underlying Terraform-bridged resource genuinely allows both. We're always giving
            // it the String side, so Either.<String, PolicyDocumentArgs>ofLeft(...) every time.
            Output<Either<String, PolicyDocumentArgs>> queuePolicyJson =
                    Output.tuple(queue.arn(), topic.arn()).applyValue(t -> Either.ofLeft("""
                            {
                              "Version": "2012-10-17",
                              "Statement": [{
                                "Effect": "Allow",
                                "Principal": { "Service": "sns.amazonaws.com" },
                                "Action": "sqs:SendMessage",
                                "Resource": "%s",
                                "Condition": { "ArnEquals": { "aws:SourceArn": "%s" } }
                              }]
                            }
                            """.formatted(t.t1, t.t2)));

            new QueuePolicy("eventsQueuePolicy", QueuePolicyArgs.builder()
                    .queueUrl(queue.url())
                    .policy(queuePolicyJson)
                    .build());

            new TopicSubscription("eventsSubscription", TopicSubscriptionArgs.builder()
                    .topic(topic.arn())
                    .protocol("sqs")
                    .endpoint(queue.arn())
                    .build());

            ctx.export("topicArn", topic.arn());
            ctx.export("queueUrl", queue.url());
        });
    }
}
