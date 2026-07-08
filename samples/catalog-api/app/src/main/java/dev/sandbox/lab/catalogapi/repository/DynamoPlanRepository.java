package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Plan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

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
    public void deleteById(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
