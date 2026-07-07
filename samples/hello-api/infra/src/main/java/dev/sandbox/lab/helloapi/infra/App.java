package dev.sandbox.lab.helloapi.infra;

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
        // Pulumi.run(ctx -> {...}) ~ a lambda/Action<T>, same shape as C#. Fun fact: the Pulumi
        // .NET SDK's API is nearly identical method-for-method (Output<T>.Apply, ctx.Export vs
        // ctx.export) — just PascalCase methods in C# vs camelCase here, which is the general
        // Java-vs-C# naming convention split (Java: camelCase methods; C#: PascalCase methods,
        // both use PascalCase for class names).
        Pulumi.run(ctx -> {
            var lambdaRole = new Role("lambdaExecRole", RoleArgs.builder()
                    // """...""" is a Java text block (Java 15+) — same idea as C#'s raw string
                    // literals ("""..."""), both landed as a "finally, multi-line strings" fix
                    // around the same era in each language.
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

            var handler = new Function("helloHandler", FunctionArgs.builder()
                    .code(new FileArchive("../app/target/app.jar"))
                    .handler("dev.sandbox.lab.helloapi.Handler::handleRequest")
                    .runtime(Runtime.Java21)
                    .role(lambdaRole.arn())
                    .timeout(15)
                    .memorySize(256)
                    .build());

            var api = new Api("helloApi", ApiArgs.builder()
                    .protocolType("HTTP")
                    .build());

            var integration = new Integration("helloIntegration", IntegrationArgs.builder()
                    .apiId(api.id())
                    .integrationType("AWS_PROXY")
                    .integrationUri(handler.arn())
                    .payloadFormatVersion("2.0")
                    .build());

            // .applyValue(id -> ...) is Pulumi's Output<T> monad — same concept as chaining off a
            // Task<T>/promise, but it's Pulumi's own type, not java.util.concurrent. Matches the
            // .NET SDK's Output<T>.Apply(id => ...) call for call.
            new Route("helloRoute", RouteArgs.builder()
                    .apiId(api.id())
                    .routeKey("GET /hello")
                    .target(integration.id().applyValue(id -> "integrations/" + id))
                    .build());

            new Stage("helloStage", StageArgs.builder()
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
