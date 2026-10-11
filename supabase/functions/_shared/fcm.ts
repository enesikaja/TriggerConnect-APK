// FCM HTTP v1 sender using a Firebase service account. FCM is free (no Blaze
// plan required). The service account JSON is provided via the FCM_SERVICE_ACCOUNT
// Edge Function secret; it is never bundled in the APK.

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

interface PushResult {
  token: string;
  ok: boolean;
  unregistered: boolean;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

function serviceAccount(): ServiceAccount | null {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ServiceAccount;
  } catch {
    return null;
  }
}

function b64url(data: ArrayBuffer | string): string {
  const bytes = typeof data === "string"
    ? new TextEncoder().encode(data)
    : new Uint8Array(data);
  let str = "";
  for (const b of bytes) str += String.fromCharCode(b);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

async function getAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.expiresAt - 60 > now) return cachedToken.value;

  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claim))}`;
  const key = await importPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(signingInput),
  );
  const jwt = `${signingInput}.${b64url(sig)}`;

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`OAuth token error: ${res.status}`);
  const data = await res.json();
  cachedToken = { value: data.access_token, expiresAt: now + (data.expires_in ?? 3600) };
  return cachedToken.value;
}

/**
 * Send a data-only push to each token. Data values must be strings; the
 * Android client renders the notification so delivery works in all app states.
 * Returns per-token results so callers can clean up unregistered tokens.
 */
export async function sendPush(
  tokens: string[],
  payload: { title: string; body: string; data: Record<string, string> },
): Promise<PushResult[]> {
  const sa = serviceAccount();
  if (!sa || tokens.length === 0) {
    // No FCM configured (or no devices) — report as skipped, not delivered.
    return tokens.map((t) => ({ token: t, ok: false, unregistered: false }));
  }

  const accessToken = await getAccessToken(sa);
  const url = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
  const dataPayload = { ...payload.data, title: payload.title, body: payload.body };

  const results = await Promise.all(tokens.map(async (token): Promise<PushResult> => {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const res = await fetch(url, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token,
            data: dataPayload,
            android: {
              // Every trigger is a time-sensitive, visible user notification.
              priority: "high",
              ttl: payload.data.priority === "URGENT" ? "300s" : "3600s",
            },
          },
        }),
        });
        if (res.ok) return { token, ok: true, unregistered: false };
        const text = await res.text();
        const unregistered = res.status === 404 ||
          text.includes("UNREGISTERED") ||
          text.includes("registration-token-not-registered");
        if (unregistered) return { token, ok: false, unregistered: true };
        const retryable = res.status === 429 || res.status >= 500;
        if (!retryable || attempt === 1) return { token, ok: false, unregistered: false };
      } catch (_e) {
        if (attempt === 1) return { token, ok: false, unregistered: false };
      }
      await new Promise((resolve) => setTimeout(resolve, 250 * (attempt + 1)));
    }
    return { token, ok: false, unregistered: false };
  }));

  return results;
}
