# attachments-api

CRUD over claim attachments (supporting documents for a claim) stored in a real S3 bucket via
Floci — upload a file, list what's there, download one back, delete it. The most
"straightforwardly what it says on the tin" sample in the repo: no messaging pattern, no
decision tree, just the four core S3 operations (`PutObject`, `ListObjectsV2`, `GetObject`,
`DeleteObject`) through the AWS SDK v2 client, wired up the same way `catalog-api` wires up
DynamoDB.

## Endpoints

| Method | Path                | Notes                                                        |
|--------|---------------------|-----------------------------------------------------------------|
| GET    | `/attachments`      | list all — cheap, from `ListObjectsV2` alone (see below)         |
| POST   | `/attachments`      | multipart `file`; 400 if empty; 201 + key/filename/contentType/size |
| GET    | `/attachments/{key}` | streams the raw bytes back with the original filename/content-type; 404 if missing |
| DELETE | `/attachments/{key}` | 204 always (S3's own `DeleteObject` doesn't error on a missing key either) |

## Why the list endpoint doesn't return filename/content-type

`ListObjectsV2` gives you key, size, and last-modified — not any custom metadata you attached at
upload time. Getting the original filename back for every item in a list would mean a
`HeadObject` call per object (an N+1 pattern), so this sample keeps `GET /attachments` honest
about what it actually costs: `AttachmentSummary` (key/size/uploadedAt) for the list, a richer
`AttachmentDetail`/`AttachmentContent` (adds filename/content-type) only for a single upload or
download, where that data comes back "for free" as part of the one call already being made.

## Why Floci

S3 is one of the most reliable AWS-shaped services to emulate — confirmed directly with the AWS
CLI (create-bucket, put/list/get/delete-object) before writing any app code, same discipline as
every other Floci-based addition in this repo. No gotchas found; it just works.

## Structure

```
app/
  domain/         AttachmentSummary (list shape), AttachmentDetail (upload response),
                  AttachmentContent (download payload), AttachmentNotFoundException
  config/         S3ClientConfig - note forcePathStyle(true), see below
  repository/     AttachmentRepository interface + S3AttachmentRepository (the Streams example
                  lives here - see below)
  service/        AttachmentService - thin, same shape as catalog-api's PlanService
  web/            controller, exception handler
infra/            Pulumi program: one aws.s3.BucketV2, forceDestroy(true) (see below)
```

## Don't miss these

- `config/S3ClientConfig.java` — `forcePathStyle(true)`. Floci's endpoint doesn't do
  subdomain-based routing (the same limitation already documented for API Gateway elsewhere in
  this repo), so path-style bucket addressing (`http://endpoint/bucket-name/key`) is what
  actually works against it, instead of the virtual-hosted style AWS defaults to in production.
- `repository/S3AttachmentRepository.java`'s `findAll()` — the Java Streams example: `.stream()
  .map(...).toList()` projects `ListObjectsV2`'s raw `S3Object` results into this API's own
  `AttachmentSummary` shape in one pass, the same idea as C#'s LINQ `.Select(...).ToList()`
  (`.toList()` itself is a Java 16+ shortcut for `.collect(Collectors.toList())`, returning an
  immutable list rather than building a mutable `ArrayList` by hand with a for loop).
- `infra/.../App.java` — `forceDestroy(true)` on the bucket. Without it, `pulumi destroy` refuses
  outright if any object was ever left behind (an interrupted `deploy.sh` run, say) — S3 buckets
  won't delete non-empty, and Pulumi doesn't empty one for you by default. Fine here since this
  bucket's entire contents are disposable demo data; a real bucket holding anything worth keeping
  would want the opposite setting.
- `web/AttachmentController.java`'s `download` — streams bytes back with the original filename
  restored via `Content-Disposition: attachment; filename="..."`, reconstructed from S3 object
  metadata set at upload time, not from the key itself (the key is just a UUID).

## Running it

```
./deploy.sh    # provision the bucket, upload/list/download/delete a demo attachment
./destroy.sh   # tear the bucket down
```

Runs on `http://localhost:8088`.
