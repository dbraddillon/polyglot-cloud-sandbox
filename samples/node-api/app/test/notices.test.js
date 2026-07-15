// node:test + node:assert are Node's own built-in test runner (stable since Node 20, no
// framework dependency needed) - the closest Node equivalent to JUnit being bundled with the
// JDK's own tooling expectations, though JUnit itself is still a separate library dependency.
// supertest is the one third-party addition, for making real HTTP requests against the Express
// app without actually binding a port - conceptually similar to Spring's MockMvc/WebTestClient.
const test = require("node:test");
const assert = require("node:assert/strict");
const request = require("supertest");
const app = require("../index");

test("POST /notices creates a pending notice", async () => {
  const res = await request(app).post("/notices").send({ message: "Annual wellness reminder" });

  assert.equal(res.status, 201);
  assert.equal(res.body.status, "PENDING");
  assert.equal(res.body.message, "Annual wellness reminder");
  assert.equal(res.headers.location, `/notices/${res.body.id}`);
});

test("POST /notices rejects a blank message", async () => {
  const res = await request(app).post("/notices").send({ message: "   " });

  assert.equal(res.status, 400);
});

test("GET /notices/:id 404s for an unknown id", async () => {
  const res = await request(app).get("/notices/does-not-exist");

  assert.equal(res.status, 404);
});

test("PATCH /notices/:id/send moves PENDING to SENT", async () => {
  const created = await request(app).post("/notices").send({ message: "Refill reminder" });

  const sent = await request(app).patch(`/notices/${created.body.id}/send`);

  assert.equal(sent.status, 200);
  assert.equal(sent.body.status, "SENT");
});

// Regression-shaped test for the one business rule: sending twice must not silently succeed.
test("PATCH /notices/:id/send twice returns 409 the second time", async () => {
  const created = await request(app).post("/notices").send({ message: "Checkup reminder" });
  await request(app).patch(`/notices/${created.body.id}/send`);

  const secondSend = await request(app).patch(`/notices/${created.body.id}/send`);

  assert.equal(secondSend.status, 409);
});

test("DELETE /notices/:id removes it", async () => {
  const created = await request(app).post("/notices").send({ message: "Temporary notice" });

  const del = await request(app).delete(`/notices/${created.body.id}`);
  const get = await request(app).get(`/notices/${created.body.id}`);

  assert.equal(del.status, 204);
  assert.equal(get.status, 404);
});
