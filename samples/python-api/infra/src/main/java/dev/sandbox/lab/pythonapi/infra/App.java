package dev.sandbox.lab.pythonapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.asset.FileArchive;
import com.pulumi.aws.apigatewayv2.Api;
import com.pulumi.aws.apigatewayv2.ApiArgs;
import com.pulumi.aws.apigatewayv2.Integration;
import com.pulumi.aws.apigatewayv2.IntegrationArgs;
import com.pulumi.aws.apigatewayv2.Route;
import com.pulumi.aws.apigatewayv2.RouteArgs;
import com.pulumi.aws.apigatewayv2.Stage;
import com.pulumi.aws.apigatewayv2.StageArgs;
import com.pulumi.aws.iam.Role;
import com.pulumi.aws.iam.RoleArgs;
import com.pulumi.aws.iam.RolePolicyAttachment;
import com.pulumi.aws.iam.RolePolicyAttachmentArgs;
import com.pulumi.aws.lambda.Function;
import com.pulumi.aws.lambda.FunctionArgs;
import com.pulumi.aws.lambda.Permission;
import com.pulumi.aws.lambda.PermissionArgs;
import com.pulumi.aws.lambda.enums.Runtime;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            var lambdaRole = new Role("lambdaExecRole", RoleArgs.builder()
                    .assumeRolePolicy("""
                            {
                              "Version": "2012-10-17",
                              "Statement": [{
                                "Action": "sts:AssumeRole",
                                "Effect": "Allow",
                                "Principal": { "Service": "lambda.amazonaws.com" }
                              }]
                            }
                            """)
                    .build());

            new RolePolicyAttachment("lambdaBasicExecution", RolePolicyAttachmentArgs.builder()
                    .role(lambdaRole.name())
                    .policyArn("arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole")
                    .build());

            // The whole point of this sample: this infra program is still Java/Pulumi, same as
            // every other sample here, but the Lambda's actual workload is Python. FileArchive
            // zips a plain source directory on the fly when given one directly (as opposed to
            // hello-api's `new FileArchive("../app/target/app.jar")`, which points at an
            // already-built jar) - no compile step exists or is needed for ../app at all.
            var handler = new Function("pythonHelloHandler", FunctionArgs.builder()
                    .code(new FileArchive("../app"))
                    .handler("handler.handler")
                    .runtime(Runtime.Python3d12)
                    .role(lambdaRole.arn())
                    .timeout(15)
                    .memorySize(256)
                    .build());

            var api = new Api("pythonApi", ApiArgs.builder()
                    .protocolType("HTTP")
                    .build());

            var integration = new Integration("pythonIntegration", IntegrationArgs.builder()
                    .apiId(api.id())
                    .integrationType("AWS_PROXY")
                    .integrationUri(handler.arn())
                    .payloadFormatVersion("2.0")
                    .build());

            new Route("pythonRoute", RouteArgs.builder()
                    .apiId(api.id())
                    .routeKey("GET /hello")
                    .target(integration.id().applyValue(id -> "integrations/" + id))
                    .build());

            new Stage("pythonStage", StageArgs.builder()
                    .apiId(api.id())
                    .name("$default")
                    .autoDeploy(true)
                    .build());

            new Permission("apiGatewayInvoke", PermissionArgs.builder()
                    .action("lambda:InvokeFunction")
                    .function(handler.name())
                    .principal("apigateway.amazonaws.com")
                    .sourceArn(api.executionArn().applyValue(arn -> arn + "/*/*"))
                    .build());

            ctx.export("apiUrl", api.apiEndpoint().applyValue(url -> url + "/hello"));
        });
    }
}
