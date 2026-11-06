import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";

// Test mode: skips Twilio signature check and app.listen().
process.env.NODE_ENV = "test";
process.env.APP_API_KEY = "signup-key-123"; // doubles as the signup key
process.env.DATA_FILE = "./data/test-store.json";

const { app } = await import("../src/server.js");
const { store } = await import("../src/store.js");

function listen() {
  return new Promise((resolve) => {
    const server = app.listen(0, () => resolve(server));
  });
}

function request(server, { method, path, headers = {}, body }) {
  const { port } = server.address();
  const form = headers["content-type"]?.includes("urlencoded");
  const payload = form
    ? new URLSearchParams(body).toString()
    : body
      ? JSON.stringify(body)
      : null;
  if (payload && !headers["content-type"]) headers["content-type"] = "application/json";
  return new Promise((resolve, reject) => {
    const req = http.request({ port, method, path, headers }, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () =>
        resolve({ status: res.statusCode, json: data ? safeParse(data) : null }),
      );
    });
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}
const safeParse = (s) => {
  try {
    return JSON.parse(s);
  } catch {
    return s;
  }
};

const SIGNUP = { "X-Signup-Key": "signup-key-123" };

test("health check is open", async () => {
  const server = await listen();
  const res = await request(server, { method: "GET", path: "/health" });
  assert.equal(res.status, 200);
  assert.deepEqual(res.json, { ok: true });
  server.close();
});

test("registration requires the signup key", async () => {
  const server = await listen();
  const res = await request(server, {
    method: "POST",
    path: "/api/auth/register",
    body: { email: "nokey@example.com", password: "password123" },
  });
  assert.equal(res.status, 403);
  server.close();
});

test("register, duplicate guard, and login", async () => {
  const server = await listen();

  const reg = await request(server, {
    method: "POST",
    path: "/api/auth/register",
    headers: { ...SIGNUP },
    body: { email: "alice@example.com", password: "password123" },
  });
  assert.equal(reg.status, 201);
  assert.ok(reg.json.token);
  assert.equal(reg.json.user.email, "alice@example.com");

  const dup = await request(server, {
    method: "POST",
    path: "/api/auth/register",
    headers: { ...SIGNUP },
    body: { email: "alice@example.com", password: "password123" },
  });
  assert.equal(dup.status, 409);

  const bad = await request(server, {
    method: "POST",
    path: "/api/auth/login",
    body: { email: "alice@example.com", password: "wrong" },
  });
  assert.equal(bad.status, 401);

  const ok = await request(server, {
    method: "POST",
    path: "/api/auth/login",
    body: { email: "alice@example.com", password: "password123" },
  });
  assert.equal(ok.status, 200);
  assert.ok(ok.json.token);
  server.close();
});

test("client API rejects a missing token", async () => {
  const server = await listen();
  const res = await request(server, { method: "GET", path: "/api/numbers" });
  assert.equal(res.status, 401);
  server.close();
});

test("messages are isolated per user", async () => {
  const server = await listen();

  const tokenFor = async (email) => {
    const r = await request(server, {
      method: "POST",
      path: "/api/auth/register",
      headers: { ...SIGNUP },
      body: { email, password: "password123" },
    });
    return { token: r.json.token, id: r.json.user.id };
  };
  const bob = await tokenFor("bob@example.com");
  const carol = await tokenFor("carol@example.com");

  // Assign numbers directly (buying hits Twilio).
  store.addNumber("+15550001111", "PNbob", bob.id);
  store.addNumber("+15550002222", "PNcarol", carol.id);

  // Inbound SMS to Bob's number.
  const inbound = await request(server, {
    method: "POST",
    path: "/sms",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: { To: "+15550001111", From: "+15559998888", Body: "Your code is 123456" },
  });
  assert.equal(inbound.status, 200);

  // Untracked number -> dropped.
  await request(server, {
    method: "POST",
    path: "/sms",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: { To: "+15559990000", From: "+15559998888", Body: "ignored" },
  });

  // Bob sees his message.
  const bobMsgs = await request(server, {
    method: "GET",
    path: "/api/messages",
    headers: { Authorization: `Bearer ${bob.token}` },
  });
  assert.equal(bobMsgs.status, 200);
  assert.equal(bobMsgs.json.messages.length, 1);
  assert.equal(bobMsgs.json.messages[0].body, "Your code is 123456");

  // Carol sees none of Bob's messages.
  const carolMsgs = await request(server, {
    method: "GET",
    path: "/api/messages",
    headers: { Authorization: `Bearer ${carol.token}` },
  });
  assert.equal(carolMsgs.status, 200);
  assert.equal(carolMsgs.json.messages.length, 0);

  // Carol cannot query Bob's number directly.
  const cross = await request(server, {
    method: "GET",
    path: "/api/messages?number=%2B15550001111",
    headers: { Authorization: `Bearer ${carol.token}` },
  });
  assert.equal(cross.status, 404);

  // Each user's /numbers shows only their own.
  const bobNums = await request(server, {
    method: "GET",
    path: "/api/numbers",
    headers: { Authorization: `Bearer ${bob.token}` },
  });
  assert.equal(bobNums.json.numbers.length, 1);
  assert.equal(bobNums.json.numbers[0].phoneNumber, "+15550001111");

  server.close();
});
