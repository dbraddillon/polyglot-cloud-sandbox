# attachments-api

## ELI5

CRUD over claim attachments stored in a real S3 bucket — upload a file, list what's there,
download one back, delete it. The most "just the core AWS SDK operations" sample in the repo, no
messaging pattern or decision tree layered on top: `PutObject`, `ListObjectsV2`, `GetObject`,
`DeleteObject`, wired up the same way `catalog-api` wires up DynamoDB.

## Deploy shape, and why

Deploys to Floci. S3 is one of the most reliably-emulated AWS services — confirmed directly with
the AWS CLI (create-bucket, put/list/get/delete-object) before writing any app code, same
discipline as every Floci-based addition in this repo. No gotchas found; it just works.

## Reading order

1. `app/.../domain/AttachmentSummary.java`, `AttachmentDetail.java`, `AttachmentContent.java` —
   three different shapes for three different amounts of information already known.
2. `app/.../config/S3ClientConfig.java` — one line worth pausing on (see below).
3. `app/.../repository/S3AttachmentRepository.java` — the whole S3 integration, and the deliberate
   Java Streams example.
4. `app/.../service/AttachmentService.java`, `web/AttachmentController.java` — thin, same shape
   as `catalog-api`'s.
5. `infra/.../App.java` — one `aws.s3.BucketV2`.

## Don't miss these

- `config/S3ClientConfig.java` — `forcePathStyle(true)`. Floci's endpoint doesn't do
  subdomain-based routing (the same limitation as API Gateway elsewhere in this repo), so
  path-style bucket addressing is what actually works against it instead of the virtual-hosted
  style AWS defaults to in production.
- `repository/S3AttachmentRepository.java`'s `findAll()` — **the deliberate Java Streams
  example**: `.stream().map(...).toList()` projects raw `ListObjectsV2` results into this API's
  own `AttachmentSummary` shape in one pass. Same idea as C#'s LINQ `.Select(...).ToList()`;
  `.toList()` (Java 16+) is itself a shortcut for `.collect(Collectors.toList())`, an immutable
  list rather than a hand-built mutable `ArrayList` with a for loop.
- Why `GET /attachments` only returns key/size/uploaded-at, not filename/content-type:
  `ListObjectsV2` doesn't return custom object metadata — getting the filename back for every
  item in a list would mean an N+1 `HeadObject` call per object. The list endpoint stays honest
  about what it actually costs; the richer shape only shows up where that data comes back "for
  free" as part of a call already being made (upload, single download).
- `infra/.../App.java` — `forceDestroy(true)` on the bucket. Without it, `pulumi destroy` refuses
  outright if any object was ever left behind (e.g. an interrupted `deploy.sh` run) — S3 won't
  delete a non-empty bucket, and Pulumi doesn't empty one for you by default.
- `web/AttachmentController.java`'s `download` — restores the original filename via
  `Content-Disposition: attachment; filename="..."`, reconstructed from S3 object metadata set at
  upload time, not from the key (the key is just a UUID).

## Running it

```
./deploy.sh    # provision the bucket, upload/list/download/delete a demo attachment
./destroy.sh   # tear the bucket down
```

Runs on `http://localhost:8088`.
