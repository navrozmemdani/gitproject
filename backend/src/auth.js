import crypto from "node:crypto";

// Constant-time comparison so the API key can't be guessed by timing.
function safeEqual(a, b) {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ab.length !== bb.length) return false;
  return crypto.timingSafeEqual(ab, bb);
}

// Protects the client-facing API (not the Twilio webhook, which is verified
// separately by Twilio's request signature).
export function requireApiKey(req, res, next) {
  const expected = process.env.APP_API_KEY;
  if (!expected || expected === "change_me_to_a_long_random_string") {
    return res
      .status(500)
      .json({ error: "Server misconfigured: APP_API_KEY is not set." });
  }
  const provided = req.get("X-API-Key");
  if (!provided || !safeEqual(provided, expected)) {
    return res.status(401).json({ error: "Invalid or missing X-API-Key." });
  }
  next();
}
