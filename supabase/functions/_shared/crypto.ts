// Keyed hashing + CSPRNG helpers. Secrets come from Edge Function env vars
// (CODE_HASH_SECRET, INVITE_HASH_SECRET) and are never shipped in the APK.

const encoder = new TextEncoder();

async function hmacHex(secretEnv: string, value: string): Promise<string> {
  const secret = Deno.env.get(secretEnv);
  if (!secret) throw new Error(`Missing secret ${secretEnv}`);
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(value));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export const hashCode = (code: string) => hmacHex("CODE_HASH_SECRET", code);
export const hashInviteToken = (token: string) => hmacHex("INVITE_HASH_SECRET", token);

/** Stable opaque token for resources that must keep one reusable QR. */
export const stableInviteToken = (scope: string, id: string) =>
  hmacHex("INVITE_HASH_SECRET", `${scope}:${id}`);

/** Opaque, URL-safe random token for QR payloads / bootstrap passwords. */
export function randomToken(bytes = 24): string {
  const arr = new Uint8Array(bytes);
  crypto.getRandomValues(arr);
  return [...arr].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Cryptographically secure, uniformly-distributed six-digit code. */
export function randomSixDigitCode(): string {
  const max = 1_000_000;
  const limit = Math.floor(0xffffffff / max) * max; // rejection sampling
  const buf = new Uint32Array(1);
  let n: number;
  do {
    crypto.getRandomValues(buf);
    n = buf[0];
  } while (n >= limit);
  return (n % max).toString().padStart(6, "0");
}
