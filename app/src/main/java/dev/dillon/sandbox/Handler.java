package dev.dillon.sandbox;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import java.time.Instant;
import java.util.Map;

// Java rule that trips up C# devs: the public class name MUST match the file name
// (Handler.java -> class Handler). C# has no such constraint between file and type name.
public class Handler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    // implements RequestHandler<In, Out> ~ implementing an interface like IRequestHandler<TIn, TOut>.
    // @Override here is an annotation (metadata Java reads at compile time), not a modifier like
    // C#'s `override` keyword — and it's optional, the compiler won't force you to write it.
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String name = "world";
        // No null-conditional operator in Java (no `?.` like C#) — this null check is the idiom.
        if (event.getQueryStringParameters() != null) {
            // getOrDefault ~ C# Dictionary.GetValueOrDefault(key, default)
            name = event.getQueryStringParameters().getOrDefault("name", name);
        }

        // String.format ~ string.Format, but Java has no interpolated strings ($"...") — that's
        // still preview-only in the JDK, so String.format/concatenation is the normal idiom.
        String body = String.format(
                "{\"message\":\"hello, %s\",\"timestamp\":\"%s\",\"requestId\":\"%s\"}",
                name, Instant.now(), context.getAwsRequestId());

        // Builder pattern fills the gap C# covers with object initializers (`new Foo { X = 1 }`)
        // — Java has no equivalent syntax, so fluent builders like this are the common idiom.
        // Map.of(...) ~ an immutable dictionary literal (closer to ImmutableDictionary than Dictionary).
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body)
                .build();
    }
}
