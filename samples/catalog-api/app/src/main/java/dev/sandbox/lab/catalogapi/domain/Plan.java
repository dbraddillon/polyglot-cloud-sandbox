package dev.sandbox.lab.catalogapi.domain;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.math.BigDecimal;

// @DynamoDbBean + @DynamoDbPartitionKey is the enhanced client's answer to a JPA @Entity/@Id -
// same idea (map a POJO to a table), different mechanics: DynamoDB has no schema beyond its key
// attributes, so this mapping only governs how Java <-> item attributes translate, not a table
// structure the database enforces (see claims-api's JPA entities for the contrast).
//
// The enhanced client is reflection-based and needs a public no-arg constructor and plain
// get/set pairs, the same requirement JPA entities have - can't use a record here the way the
// web-layer DTOs do.
@DynamoDbBean
public class Plan {
    private String id;
    private String name;
    private BigDecimal monthlyPremium;

    public Plan() {
    }

    public Plan(String id, String name, BigDecimal monthlyPremium) {
        this.id = id;
        this.name = name;
        this.monthlyPremium = monthlyPremium;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMonthlyPremium() {
        return monthlyPremium;
    }

    public void setMonthlyPremium(BigDecimal monthlyPremium) {
        this.monthlyPremium = monthlyPremium;
    }
}
