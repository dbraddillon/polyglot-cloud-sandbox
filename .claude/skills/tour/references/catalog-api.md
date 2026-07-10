# catalog-api

## ELI5

Not every database is a table of rows with a fixed schema — DynamoDB is a key-value/document
store where you look things up by a key you chose ahead of time, and there's no schema beyond
that key. This sample is CRUD over a real DynamoDB table (an insurance plan catalog) using the
AWS SDK v2 "enhanced" client, which does for DynamoDB roughly what an ORM does for SQL:
annotation-driven mapping from a Java object to a table item.

The best way to feel the contrast is to read this right next to `claims-api`: same CRUD shape,
completely different data-modeling story underneath.

## Deploy shape, and why

Deploys to Floci, and it just works — DynamoDB's `create-table` is synchronous and fast, no
repeat of `search-api`'s OpenSearch `Created`-status hang. Confirmed directly with the AWS CLI
(`create-table`, `put-item`, `get-item`) before any app code was written.

## Reading order

1. `app/src/main/java/.../domain/Plan.java` — the `@DynamoDbBean`-mapped entity.
2. `app/src/main/java/.../repository/DynamoPlanRepository.java` — hand-written repository (no
   Spring Data module does for DynamoDB what it does for JPA), including a real conditional-
   update fix.
3. `app/src/main/java/.../config/DynamoDbConfig.java` — the client bean, ~5 lines.
4. `infra/src/main/java/.../App.java` — one `aws.dynamodb.Table` resource.

## Don't miss these

- `domain/Plan.java:8-15` — `@DynamoDbBean` + `@DynamoDbPartitionKey` is the enhanced client's
  answer to a JPA `@Entity`/`@Id`, but the mechanics differ: DynamoDB has no schema beyond its
  key attributes, so this mapping only governs Java ⇄ item-attribute translation, not a table
  structure the database itself enforces the way a SQL table does.
- `repository/DynamoPlanRepository.java:28-31` — `.scan()` reads every item in the table; there's
  no DynamoDB equivalent of "`SELECT *` is usually fine on a small table" — a scan's cost scales
  with table size no matter how few items you actually want back. `GET /plans` in this sample
  is doing exactly that scan; worth knowing before assuming it behaves like a cheap `SELECT *`.
- `repository/DynamoPlanRepository.java:46-64` — the best "found a real bug" story in this
  sample: `updateIfExists` replaces what used to be a get-then-save pattern (two round-trips,
  with a race window where another writer could delete the item in between) with a single
  conditional `PutItem` — the existence check enforced atomically by DynamoDB itself via a
  condition expression. `ConditionalCheckFailedException` is DynamoDB's version of a SQL
  `UPDATE ... WHERE id = ?` matching zero rows: the call didn't fail, the precondition just
  wasn't met.

## A real bug found while wiring this up

The Spring app crashed at startup with `Unable to load region from any of the providers in the
chain` — even with `AWS_DEFAULT_REGION` already set and Pulumi/the AWS CLI both working fine
against Floci. The AWS SDK for Java v2's region provider chain specifically wants `AWS_REGION`,
not `AWS_DEFAULT_REGION`. `deploy.sh` now sets both — a detail that generalizes to any real AWS
Java project that mysteriously can't find its region.

## Running it

```
./deploy.sh    # create the table, run the app, create/list a plan
./destroy.sh   # tear down the table; kills the app process too
```

Runs on `http://localhost:8082`.
