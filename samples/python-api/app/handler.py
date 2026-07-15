import json
from datetime import datetime, timezone


# No handler interface to implement here, unlike Java's Handler implements
# RequestHandler<In, Out> in hello-api - AWS Lambda's Python runtime just calls whatever
# module.function_name string is configured on the function (see infra/.../App.java's
# .handler("handler.handler")), no base class or interface required.
def handler(event, context):
    query_params = event.get("queryStringParameters") or {}
    name = query_params.get("name", "world")

    body = {
        "message": f"hello, {name}",
        # AWS's Python runtime hands the Lambda context as a plain object with attributes
        # (context.aws_request_id), not a dict - easy to trip over the first time, since the
        # incoming `event` right next to it *is* a plain dict.
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "requestId": context.aws_request_id,
    }

    return {
        "statusCode": 200,
        "headers": {"Content-Type": "application/json"},
        "body": json.dumps(body),
    }
