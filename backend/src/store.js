// Tiny JSON-file persistence. Fine for a single-instance dev backend.
// For production, swap this module for a real database (Postgres, SQLite, etc.).
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";

const DATA_FILE = process.env.DATA_FILE || "./data/store.json";

const empty = () => ({
  // E.164 number -> { sid, provisionedAt }
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
  addNumber(e164, sid) {
    state.numbers[e164] = { sid, provisionedAt: new Date().toISOString() };
    persist();
    return state.numbers[e164];
  },

  removeNumber(e164) {
    delete state.numbers[e164];
    persist();
  },

  listNumbers() {
    return Object.entries(state.numbers).map(([phoneNumber, meta]) => ({
      phoneNumber,
      ...meta,
    }));
  },

  hasNumber(e164) {
    return Boolean(state.numbers[e164]);
  },

  addMessage({ to, from, body }) {
    const msg = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      to,
      from,
      body,
      receivedAt: new Date().toISOString(),
    };
    state.messages.unshift(msg);
    // keep the log bounded
    if (state.messages.length > 5000) state.messages.length = 5000;
    persist();
    return msg;
  },

  listMessages(forNumber) {
    if (!forNumber) return state.messages;
    return state.messages.filter((m) => m.to === forNumber);
  },
};
