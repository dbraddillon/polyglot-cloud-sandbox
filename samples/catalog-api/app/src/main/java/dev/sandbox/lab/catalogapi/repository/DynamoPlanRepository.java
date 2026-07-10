package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class DynamoPlanRepository implements PlanRepository {
    private final DynamoDbTable<Plan> table;

    public DynamoPlanRepository(DynamoDbEnhancedClient client, @Value("${catalog.table-name}") String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(Plan.class));
    }

    @Override
    public List<Plan> findAll() {
        // .scan() reads every item in the table - fine for a sandbox, but a real production
        // table would use .query() against a known key instead. DynamoDB has no equivalent of
        // SQL's "SELECT * is usually fine" - a scan's cost scales with table size no matter how
        // few items you actually want back.
        return table.scan().items().stream().collect(Collectors.toList());
    }

    @Override
    public Optional<Plan> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build()));
    }

    @Override
    public Plan save(Plan plan) {
        table.putItem(plan);
        return plan;
    }

    @Override
    public Optional<Plan> updateIfExists(Plan plan) {
        // A conditional put replaces the get-then-save pattern the service used to do: that was
        // two round-trips with a race between them (another writer could delete the item in
        // between), and this is one round-trip with the existence check enforced atomically by
        // DynamoDB itself via the condition expression. ConditionalCheckFailedException is
        // DynamoDB's equivalent of a SQL `UPDATE ... WHERE id = ?` matching zero rows - the call
        // didn't fail, the precondition just wasn't met, so it's caught and turned into an empty
        // Optional rather than propagated as an error.
        try {
            table.putItem(PutItemEnhancedRequest.builder(Plan.class)
                    .item(plan)
                    .conditionExpression(Expression.builder()
                            .expression("attribute_exists(id)")
                            .build())
                    .build());
            return Optional.of(plan);
        } catch (ConditionalCheckFailedException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteById(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
