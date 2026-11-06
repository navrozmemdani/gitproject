import "dotenv/config";
import express from "express";

import { store } from "./store.js";
import { requireAuth, requireSignupKey } from "./auth.js";
import { hashPassword, verifyPassword, newToken, validateCredentials } from "./users.js";
import {
  searchAvailableNumbers,
  buyNumber,
  releaseNumber,
  validateTwilioRequest,
} from "./twilio.js";

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.urlencoded({ extended: false })); // Twilio posts form-encoded

// --- Health check (no auth) ---
app.get("/health", (_req, res) => res.json({ ok: true }));

// --- Twilio inbound SMS webhook ---
// Verified by Twilio's request signature, NOT a user token. Messages are keyed
// by the destination number; ownership is resolved at read time.
app.post("/sms", (req, res) => {
  const fullUrl =
    (process.env.PUBLIC_BASE_URL?.replace(/\/$/, "") || "") + "/sms";

  if (process.env.NODE_ENV !== "test" && !validateTwilioRequest(req, fullUrl)) {
    console.warn("[/sms] rejected: bad Twilio signature");
    return res.status(403).send("Forbidden");
  }

  const { To: to, From: from, Body: body } = req.body;
  if (to && store.hasNumber(to)) {
    store.addMessage({ to, from, body });
    console.log(`[/sms] stored message for ${to} from ${from}`);
  } else {
    console.warn(`[/sms] message for untracked number ${to}, ignoring`);
  }

  res.type("text/xml").send("<Response></Response>");
});

// ---------- Auth ----------
const auth = express.Router();

auth.post("/register", requireSignupKey, (req, res) => {
  const { email, password } = req.body || {};
  const problem = validateCredentials(email, password);
  if (problem) return res.status(400).json({ error: problem });
  if (store.getUserByEmail(email)) {
    return res.status(409).json({ error: "An account with that email already exists." });
  }
  const { salt, hash } = hashPassword(password);
  const user = store.createUser({ email, salt, hash });
  const token = newToken();
  store.addToken(token, user.id);
  res.status(201).json({ token, user: { id: user.id, email: user.email } });
});

auth.post("/login", (req, res) => {
  const { email, password } = req.body || {};
  const user = email && store.getUserByEmail(email);
  // Verify even on a missing user to avoid leaking which emails exist.
  const ok =
    user && typeof password === "string"
      ? verifyPassword(password, user.salt, user.hash)
      : (hashPassword(String(password ?? "")), false);
  if (!ok) return res.status(401).json({ error: "Invalid email or password." });
  const token = newToken();
  store.addToken(token, user.id);
  res.json({ token, user: { id: user.id, email: user.email } });
});

app.use("/api/auth", auth);

// ---------- Authenticated client API ----------
const api = express.Router();
api.use(requireAuth);

api.get("/me", (req, res) => {
  res.json({ user: { id: req.user.id, email: req.user.email } });
});

api.post("/logout", (req, res) => {
  store.removeToken(req.token);
  res.status(204).end();
});

// Search numbers available to rent.
api.get("/available-numbers", async (req, res) => {
  try {
    const numbers = await searchAvailableNumbers({
      country: req.query.country || "US",
      areaCode: req.query.areaCode,
      limit: Math.min(Number(req.query.limit) || 10, 30),
    });
    res.json({ numbers });
  } catch (err) {
    res.status(502).json({ error: err.message });
  }
});

// List numbers the logged-in user has rented.
api.get("/numbers", (req, res) => {
  res.json({ numbers: store.listNumbers(req.user.id) });
});

// Rent a specific number, owned by the logged-in user.
api.post("/numbers", async (req, res) => {
  const { phoneNumber } = req.body || {};
  if (!phoneNumber) {
    return res.status(400).json({ error: "phoneNumber is required" });
  }
  if (store.hasNumber(phoneNumber)) {
    return res.status(409).json({ error: "That number is already tracked." });
  }
  try {
    const base = process.env.PUBLIC_BASE_URL?.replace(/\/$/, "");
    if (!base) {
      return res.status(500).json({ error: "PUBLIC_BASE_URL must be set to receive SMS." });
    }
    const bought = await buyNumber({ phoneNumber, smsUrl: `${base}/sms` });
    store.addNumber(bought.phoneNumber, bought.sid, req.user.id);
    res.status(201).json(bought);
  } catch (err) {
    res.status(502).json({ error: err.message });
  }
});

// Release a number — only if the logged-in user owns it.
api.delete("/numbers/:phoneNumber", async (req, res) => {
  const phoneNumber = req.params.phoneNumber;
  const entry = store.getNumber(phoneNumber);
  if (!entry || entry.ownerId !== req.user.id) {
    return res.status(404).json({ error: "Number not found" });
  }
  try {
    await releaseNumber(entry.sid);
    store.removeNumber(phoneNumber);
    res.status(204).end();
  } catch (err) {
    res.status(502).json({ error: err.message });
  }
});

// List received SMS for the user's numbers, optionally one: /messages?number=+1...
api.get("/messages", (req, res) => {
  const forNumber = req.query.number;
  if (forNumber) {
    const entry = store.getNumber(forNumber);
    if (!entry || entry.ownerId !== req.user.id) {
      return res.status(404).json({ error: "Number not found" });
    }
  }
  res.json({ messages: store.listMessages(req.user.id, forNumber) });
});

app.use("/api", api);

if (process.env.NODE_ENV !== "test") {
  app.listen(PORT, () => {
    console.log(`Temp Number backend listening on :${PORT}`);
    console.log(`Twilio webhook path: POST /sms`);
  });
}

export { app };
