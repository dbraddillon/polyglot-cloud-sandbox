const express = require("express");
const { randomUUID } = require("crypto");

const app = express();
app.use(express.json());

// In-memory only, same idea as task-api's ConcurrentHashMap-backed repository - a real service
// would swap this for a database without touching the routes below. A plain Map is fine here:
// Node is single-threaded per process, so there's no concurrent-map story to worry about the
// way task-api's ConcurrentHashMap exists specifically to handle multiple request threads.
const notices = new Map();

const STATUS = Object.freeze({ PENDING: "PENDING", SENT: "SENT" });

app.get("/notices", (req, res) => {
  res.json([...notices.values()]);
});

app.get("/notices/:id", (req, res) => {
  const notice = notices.get(req.params.id);
  if (!notice) {
    return res.status(404).json({ error: `Notice not found: ${req.params.id}` });
  }
  res.json(notice);
});

app.post("/notices", (req, res) => {
  const message = (req.body?.message ?? "").trim();
  if (!message) {
    return res.status(400).json({ error: "message must not be blank" });
  }

  const notice = {
    id: randomUUID(),
    message,
    status: STATUS.PENDING,
    createdAt: new Date().toISOString(),
  };
  notices.set(notice.id, notice);
  res.status(201).location(`/notices/${notice.id}`).json(notice);
});

// The one business rule, matching task-api's single-status-transition shape: a notice can only
// be sent once. No PENDING/SENT enum type the way Java's TaskStatus is one - a plain string
// constant is the idiomatic amount of type safety to reach for here, since there's no compiler
// to hold a real enum accountable at every call site the way there is in Java or C#.
app.patch("/notices/:id/send", (req, res) => {
  const notice = notices.get(req.params.id);
  if (!notice) {
    return res.status(404).json({ error: `Notice not found: ${req.params.id}` });
  }
  if (notice.status !== STATUS.PENDING) {
    return res.status(409).json({ error: `Notice ${notice.id} is already ${notice.status}` });
  }
  notice.status = STATUS.SENT;
  res.json(notice);
});

app.delete("/notices/:id", (req, res) => {
  notices.delete(req.params.id);
  res.status(204).send();
});

const port = process.env.PORT || 8086;

// Only start listening when run directly (`node index.js`), not when required by the test
// suite - require.main === module is Node's version of Python's `if __name__ == "__main__":`,
// or C#'s Main() only running for the actual entry assembly.
if (require.main === module) {
  app.listen(port, () => {
    console.log(`node-api listening on port ${port}`);
  });
}

module.exports = app;
