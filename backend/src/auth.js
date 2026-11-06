import crypto from "node:crypto";
import { store } from "./store.js";

// Constant-time comparison so secrets can't be guessed by timing.
function safeEqual(a, b) {
  const ab = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ab.length !== bb.length) return false;
  return crypto.timingSafeEqual(ab, bb);
}

// Gate on registration. Unless ALLOW_OPEN_SIGNUP=true, a client must present the
// shared signup key (APP_API_KEY) in X-Signup-Key to create an account. This
// keeps a public backend from being open to the world while still allowing you
// to invite several users.
export function requireSignupKey(req, res, next) {
  if (String(process.env.ALLOW_OPEN_SIGNUP).toLowerCase() === "true") {
    return next();
  }
  const expected = process.env.APP_API_KEY;
  if (!expected || expected === "change_me_to_a_long_random_string") {
    return res.status(500).json({
      error: "Server misconfigured: set APP_API_KEY or ALLOW_OPEN_SIGNUP=true.",
    });
  }
  const provided = req.get("X-Signup-Key");
  if (!provided || !safeEqual(provided, expected)) {
    return res.status(403).json({ error: "Invalid or missing signup key." });
  }
  next();
}

// Authenticates a client request by bearer token and attaches req.user.
export function requireAuth(req, res, next) {
  const header = req.get("Authorization") || "";
  const match = header.match(/^Bearer\s+(.+)$/i);
  if (!match) {
    return res.status(401).json({ error: "Missing bearer token." });
  }
  const userId = store.userIdForToken(match[1].trim());
  const user = userId && store.getUserById(userId);
  if (!user) {
    return res.status(401).json({ error: "Invalid or expired token." });
  }
  req.user = user;
  req.token = match[1].trim();
  next();
}
