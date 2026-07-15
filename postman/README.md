# Request collection

`polyglot-cloud-sandbox.postman_collection.json` — one folder per sample, covering every
endpoint. Each folder is a self-contained, runnable flow (create → read → update → delete, in
that order); the "create" request in each one has a Tests script that captures the new id into a
collection variable, so the requests after it just work without manual copy-pasting.

## Using it

**Postman**: File → Import → select this file.

**Insomnia**: Insomnia's own importer reads Postman v2.1 collections directly — Import → From
File → select this file. No conversion needed.

1. Start whichever sample you want to try (`cd samples/<name> && ./deploy.sh`).
2. Open that sample's folder in the collection and run the requests top to bottom (or use
   Postman's "Run collection" / Insomnia's equivalent to run a whole folder in sequence — this
   is exactly how the collection was verified while building it, via Newman, Postman's CLI
   runner: `npx newman run polyglot-cloud-sandbox.postman_collection.json --folder task-api`).
3. `hello-api` and `python-api` are the two exceptions — Lambda + API Gateway via Floci means the
   invoke URL has a Pulumi-generated API id that's different on every deploy. Copy the "Local
   invoke URL" line `deploy.sh` prints and paste it into that sample's collection variable
   (`hello_api_invoke_url` / `python_api_invoke_url`) before running its one request.
4. The two file-upload endpoints (`claims-intake-api`'s CSV upload, `attachments-api`'s file
   upload) need a file picked manually in the GUI — Postman/Insomnia's formdata file field can't
   be pre-filled with a path portably across machines, so those requests are wired up correctly
   but need one click to attach a file before sending.

Every other sample's base URL is a fixed port (see the collection variables), since only the two
Lambda samples route through Floci's per-deploy API Gateway id.
