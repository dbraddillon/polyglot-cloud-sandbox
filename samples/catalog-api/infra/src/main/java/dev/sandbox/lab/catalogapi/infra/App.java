package dev.sandbox.lab.catalogapi.infra;

import com.pulumi.Pulumi;
import com.pulumi.aws.dynamodb.Table;
import com.pulumi.aws.dynamodb.TableArgs;
import com.pulumi.aws.dynamodb.inputs.TableAttributeArgs;

public class App {
    public static void main(String[] args) {
        Pulumi.run(ctx -> {
            // Only the key attribute needs declaring up front - DynamoDB has no fixed schema
            // for anything else an item might carry, unlike a SQL table's column list.
            var table = new Table("plansTable", TableArgs.builder()
                    .name("plans")
                    .billingMode("PAY_PER_REQUEST")
                    .hashKey("id")
                    .attributes(TableAttributeArgs.builder()
                            .name("id")
                            .type("S")
                            .build())
                    .build());

            ctx.export("tableName", table.name());
        });
    }
}
