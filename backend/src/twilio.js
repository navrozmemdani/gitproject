import twilio from "twilio";

let client = null;

export function getClient() {
  if (client) return client;
  const sid = process.env.TWILIO_ACCOUNT_SID;
  const token = process.env.TWILIO_AUTH_TOKEN;
  if (!sid || !token) {
    throw new Error("TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN are not set.");
  }
  client = twilio(sid, token);
  return client;
}

// Search for numbers available to rent in a given country/area.
export async function searchAvailableNumbers({ country = "US", areaCode, limit = 10 }) {
  const opts = { smsEnabled: true, limit };
  if (areaCode) opts.areaCode = areaCode;
  const list = await getClient()
    .availablePhoneNumbers(country)
    .local.list(opts);
  return list.map((n) => ({
    phoneNumber: n.phoneNumber,
    friendlyName: n.friendlyName,
    locality: n.locality,
    region: n.region,
  }));
}

// Rent (purchase) a specific number and point its SMS webhook at this backend.
export async function buyNumber({ phoneNumber, smsUrl }) {
  const incoming = await getClient().incomingPhoneNumbers.create({
    phoneNumber,
    smsUrl, // Twilio POSTs here when an SMS arrives
    smsMethod: "POST",
  });
  return { sid: incoming.sid, phoneNumber: incoming.phoneNumber };
}

// Release a number so you stop paying for it.
export async function releaseNumber(sid) {
  await getClient().incomingPhoneNumbers(sid).remove();
}

// Validates that an inbound webhook really came from Twilio.
export function validateTwilioRequest(req, fullUrl) {
  const token = process.env.TWILIO_AUTH_TOKEN;
  const signature = req.get("X-Twilio-Signature") || "";
  return twilio.validateRequest(token, signature, fullUrl, req.body || {});
}
