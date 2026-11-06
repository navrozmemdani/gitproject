import "dotenv/config";
import express from "express";

import { store } from "./store.js";
import { requireApiKey } from "./auth.js";
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
// Twilio POSTs here whenever an SMS arrives on one of your rented numbers.
// Verified by Twilio's request signature, NOT the app API key.
app.post("/sms", (req, res) => {
  const fullUrl =
    (process.env.PUBLIC_BASE_URL?.replace(/\/$/, "") || "") + "/sms";

  if (process.env.NODE_ENV !== "test" && !validateTwilioRequest(req, fullUrl)) {
    console.warn("[/sms] rejected: bad Twilio signature");
    return res.status(403).send("Forbidden");
  }

  const { To: to, From: from, Body: body } = req.body;
  if (to && store.hasNumber(to)) {
    const msg = store.addMessage({ to, from, body });
    console.log(`[/sms] stored message for ${to} from ${from}`);
    void msg;
  } else {
    console.warn(`[/sms] message for untracked number ${to}, ignoring`);
  }

  // Empty TwiML = received, no auto-reply.
  res.type("text/xml").send("<Response></Response>");
});

// ---------- Authenticated client API ----------
const api = express.Router();
api.use(requireApiKey);

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

// List numbers you've rented.
api.get("/numbers", (_req, res) => {
  res.json({ numbers: store.listNumbers() });
});

// Rent a specific number.
api.post("/numbers", async (req, res) => {
  const { phoneNumber } = req.body || {};
  if (!phoneNumber) {
    return res.status(400).json({ error: "phoneNumber is required" });
  }
  try {
    const base = process.env.PUBLIC_BASE_URL?.replace(/\/$/, "");
    if (!base) {
      return res
        .status(500)
        .json({ error: "PUBLIC_BASE_URL must be set to receive SMS." });
    }
    const bought = await buyNumber({ phoneNumber, smsUrl: `${base}/sms` });
    store.addNumber(bought.phoneNumber, bought.sid);
    res.status(201).json(bought);
  } catch (err) {
    res.status(502).json({ error: err.message });
  }
});

// Release a number (stop paying for it).
api.delete("/numbers/:phoneNumber", async (req, res) => {
  const phoneNumber = req.params.phoneNumber;
  const entry = store.listNumbers().find((n) => n.phoneNumber === phoneNumber);
  if (!entry) return res.status(404).json({ error: "Number not found" });
  try {
    await releaseNumber(entry.sid);
    store.removeNumber(phoneNumber);
    res.status(204).end();
  } catch (err) {
    res.status(502).json({ error: err.message });
  }
});

// List received SMS, optionally filtered to one number: /messages?number=+1555...
api.get("/messages", (req, res) => {
  res.json({ messages: store.listMessages(req.query.number) });
});

app.use("/api", api);

// Only listen when run directly (so tests can import the app).
if (process.env.NODE_ENV !== "test") {
  app.listen(PORT, () => {
    console.log(`Temp Number backend listening on :${PORT}`);
    console.log(`Twilio webhook path: POST /sms`);
  });
}

export { app };
