package dev.sandbox.lab.catalogapi.repository;

import dev.sandbox.lab.catalogapi.domain.Product;
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
public class DynamoProductRepository implements ProductRepository {
    private final DynamoDbTable<Product> table;

    public DynamoProductRepository(DynamoDbEnhancedClient client, @Value("${catalog.table-name}") String tableName) {
        this.table = client.table(tableName, TableSchema.fromBean(Product.class));
    }

    @Override
    public List<Product> findAll() {
        // .scan() reads every item in the table - fine for a sandbox, but a real production
        // table would use .query() against a known key instead. DynamoDB has no equivalent of
        // SQL's "SELECT * is usually fine" - a scan's cost scales with table size no matter how
        // few items you actually want back.
        return table.scan().items().stream().collect(Collectors.toList());
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(id).build()));
    }

    @Override
    public Product save(Product product) {
        table.putItem(product);
        return product;
    }

    @Override
    public void deleteById(String id) {
        table.deleteItem(Key.builder().partitionValue(id).build());
    }
}
