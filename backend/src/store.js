// Tiny JSON-file persistence. Fine for a single-instance dev backend.
// For production, swap this module for a real database (Postgres, SQLite, etc.).
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";

const DATA_FILE = process.env.DATA_FILE || "./data/store.json";

const empty = () => ({
  // id -> { id, email, salt, hash, createdAt }
  users: {},
  // token -> { userId, createdAt }
  tokens: {},
  // E.164 number -> { sid, ownerId, provisionedAt }
  numbers: {},
  // array of { id, to, from, body, receivedAt }
  messages: [],
});

let state = empty();

function load() {
  try {
    if (existsSync(DATA_FILE)) {
      state = { ...empty(), ...JSON.parse(readFileSync(DATA_FILE, "utf8")) };
    }
  } catch (err) {
    console.error("[store] failed to read, starting fresh:", err.message);
    state = empty();
  }
}

function persist() {
  try {
    mkdirSync(dirname(DATA_FILE), { recursive: true });
    writeFileSync(DATA_FILE, JSON.stringify(state, null, 2));
  } catch (err) {
    console.error("[store] failed to persist:", err.message);
  }
}

load();

export const store = {
  // ---------- users ----------
  createUser({ email, salt, hash }) {
    const normalized = email.trim().toLowerCase();
    const id = `u_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
    const user = { id, email: normalized, salt, hash, createdAt: new Date().toISOString() };
    state.users[id] = user;
    persist();
    return user;
  },

  getUserByEmail(email) {
    const normalized = String(email).trim().toLowerCase();
    return Object.values(state.users).find((u) => u.email === normalized) || null;
  },

  getUserById(id) {
    return state.users[id] || null;
  },

  // ---------- tokens ----------
  addToken(token, userId) {
    state.tokens[token] = { userId, createdAt: new Date().toISOString() };
    persist();
  },

  userIdForToken(token) {
    return state.tokens[token]?.userId || null;
  },

  removeToken(token) {
    delete state.tokens[token];
    persist();
  },

  // ---------- numbers ----------
  addNumber(e164, sid, ownerId) {
    state.numbers[e164] = {
      sid,
      ownerId,
      provisionedAt: new Date().toISOString(),
    };
    persist();
    return state.numbers[e164];
  },

  removeNumber(e164) {
    delete state.numbers[e164];
    persist();
  },

  getNumber(e164) {
    return state.numbers[e164] || null;
  },

  hasNumber(e164) {
    return Boolean(state.numbers[e164]);
  },

  // Numbers owned by one user (or all when ownerId is omitted).
  listNumbers(ownerId) {
    return Object.entries(state.numbers)
      .filter(([, meta]) => !ownerId || meta.ownerId === ownerId)
      .map(([phoneNumber, meta]) => ({ phoneNumber, ...meta }));
  },

  // ---------- messages ----------
  addMessage({ to, from, body }) {
    const msg = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      to,
      from,
      body,
      receivedAt: new Date().toISOString(),
    };
    state.messages.unshift(msg);
    if (state.messages.length > 5000) state.messages.length = 5000;
    persist();
    return msg;
  },

  // Messages delivered to numbers the given user owns. Optionally narrow to one
  // number, but only if that user actually owns it.
  listMessages(ownerId, forNumber) {
    const owned = new Set(
      Object.entries(state.numbers)
        .filter(([, meta]) => meta.ownerId === ownerId)
        .map(([phoneNumber]) => phoneNumber),
    );
    return state.messages.filter((m) => {
      if (!owned.has(m.to)) return false;
      if (forNumber && m.to !== forNumber) return false;
      return true;
    });
  },
};
