import { test } from "node:test";
import assert from "node:assert/strict";

// Run the server in test mode: skips Twilio signature check and app.listen().
process.env.NODE_ENV = "test";
process.env.APP_API_KEY = "test-key-123";
process.env.DATA_FILE = "./data/test-store.json";

const { app } = await import("../src/server.js");

// Minimal in-process HTTP helper so we don't need a client dependency.
import http from "node:http";

function listen() {
  return new Promise((resolve) => {
    const server = app.listen(0, () => resolve(server));
  });
}

function request(server, { method, path, headers = {}, body }) {
  const { port } = server.address();
  const payload =
    body && headers["content-type"]?.includes("urlencoded")
      ? new URLSearchParams(body).toString()
      : body
        ? JSON.stringify(body)
        : null;
  if (payload && !headers["content-type"]) headers["content-type"] = "application/json";
  return new Promise((resolve, reject) => {
    const req = http.request(
      { port, method, path, headers },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () =>
          resolve({ status: res.statusCode, body: data }),
        );
      },
    );
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

test("health check is open", async () => {
  const server = await listen();
  const res = await request(server, { method: "GET", path: "/health" });
  assert.equal(res.status, 200);
  assert.deepEqual(JSON.parse(res.body), { ok: true });
  server.close();
});

test("client API rejects a missing key", async () => {
  const server = await listen();
  const res = await request(server, { method: "GET", path: "/api/numbers" });
  assert.equal(res.status, 401);
  server.close();
});

test("inbound SMS is stored only for tracked numbers, then readable via API", async () => {
  const server = await listen();

  // Provision a number directly in the store (buying hits Twilio).
  const { store } = await import("../src/store.js");
  store.addNumber("+15550001111", "PNtest");

  // Simulate Twilio posting an inbound SMS.
  const inbound = await request(server, {
    method: "POST",
    path: "/sms",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: { To: "+15550001111", From: "+15559998888", Body: "Your code is 123456" },
  });
  assert.equal(inbound.status, 200);

  // A message to an untracked number must be ignored.
  await request(server, {
    method: "POST",
    path: "/sms",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: { To: "+15550002222", From: "+15559998888", Body: "ignored" },
  });

  const read = await request(server, {
    method: "GET",
    path: "/api/messages?number=%2B15550001111",
    headers: { "X-API-Key": "test-key-123" },
  });
  assert.equal(read.status, 200);
  const { messages } = JSON.parse(read.body);
  assert.equal(messages.length, 1);
  assert.equal(messages[0].body, "Your code is 123456");

  server.close();
});
