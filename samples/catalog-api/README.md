# catalog-api

A Spring Boot CRUD API over a real DynamoDB table (via Floci), using the AWS SDK v2 "enhanced"
DynamoDB client — annotation-driven object mapping, the DynamoDB equivalent of what an ORM
gives you over raw JDBC/ADO.NET. Contrast with orders-api's relational modeling: here there's no
schema beyond the partition key, and listing everything is a `scan` (cost scales with table
size), not a cheap `SELECT *`.

## Endpoints

| Method | Path             | Notes                                    |
|--------|------------------|--------------------------------------------|
| GET    | `/products`      | full table scan - see the repository comment |
| GET    | `/products/{id}` | 404 if missing                             |
| POST   | `/products`      | `{"name": "...", "price": 49.99}`, price must be positive |
| PUT    | `/products/{id}` | full replace                               |
| DELETE | `/products/{id}` | 204                                         |

## Structure

```
app/
  domain/Product.java        @DynamoDbBean - the enhanced client's answer to a JPA @Entity
  config/DynamoDbConfig.java DynamoDbClient/DynamoDbEnhancedClient beans, no Floci-specific code
  repository/                interface + DynamoProductRepository (hand-written, unlike
                              orders-api's Spring Data JPA repository - no Spring Data module
                              does this part for DynamoDB the way it does for JPA)
  service/, web/              same layering as the other samples
infra/                        Pulumi program: one aws.dynamodb.Table, via Floci
```

## Why Floci works fine here (unlike search-api's first attempt)

DynamoDB's create-table operation is synchronous and fast in Floci - confirmed by testing it
directly with the AWS CLI before writing any code (`create-table`, `put-item`, `get-item` all
worked immediately, no waiting). No repeat of the OpenSearch domain issue where Floci's
control-plane status field never resolved.

## A real bug found while wiring this up: the wrong region env var

The Spring app crashed on startup with `Unable to load region from any of the providers in the
chain`, even though `AWS_DEFAULT_REGION` was already set and Pulumi/the AWS CLI were both
working fine against Floci. The AWS CLI and Pulumi's AWS provider read `AWS_DEFAULT_REGION`;
the **AWS SDK for Java v2**'s default region provider chain specifically wants `AWS_REGION`.
`deploy.sh` now sets both - worth remembering if a real AWS Java project ever mysteriously can't
find its region despite `AWS_DEFAULT_REGION` being set correctly everywhere else.

## Running it

```
./deploy.sh    # create the table, run the app, create/list a product
./destroy.sh   # tear down the table; kills the app process too
```

Runs on `http://localhost:8082`, same "Apple's stub `java`" caveat as search-api applies here
too (`deploy.sh` handles it the same way).
