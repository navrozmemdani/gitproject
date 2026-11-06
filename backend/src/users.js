import { scryptSync, randomBytes, timingSafeEqual } from "node:crypto";

// scrypt is in Node's stdlib, so no extra dependency for password hashing.
const KEYLEN = 64;

export function hashPassword(password) {
  const salt = randomBytes(16).toString("hex");
  const hash = scryptSync(password, salt, KEYLEN).toString("hex");
  return { salt, hash };
}

export function verifyPassword(password, salt, expectedHashHex) {
  const computed = scryptSync(password, salt, KEYLEN);
  const expected = Buffer.from(expectedHashHex, "hex");
  if (computed.length !== expected.length) return false;
  return timingSafeEqual(computed, expected);
}

export function newToken() {
  return randomBytes(32).toString("hex");
}

// Basic validation shared by register/login.
export function validateCredentials(email, password) {
  if (typeof email !== "string" || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) {
    return "A valid email is required.";
  }
  if (typeof password !== "string" || password.length < 8) {
    return "Password must be at least 8 characters.";
  }
  return null;
}
