import { handleOptions } from "../_shared/cors.ts";
import { Errors, errorResponse, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { clientIp } from "../_shared/auth.ts";
import { hashCode, randomToken } from "../_shared/crypto.ts";

const WINDOW_MS = 15 * 60 * 1000;
const MAX_FAILURES_PER_IP = 10;
const MAX_FAILURES_PER_DEVICE = 8;

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const admin = adminClient();
    const ip = clientIp(req);
    const requestBody = await req.json().catch(() => ({}));
    const deviceId = typeof requestBody.deviceId === "string"
      ? requestBody.deviceId.slice(0, 128)
      : "unknown";

    // Rate limit: too many recent failures from this IP -> lock out.
    const windowStart = new Date(Date.now() - WINDOW_MS).toISOString();
    const { count } = await admin
      .from("auth_attempts")
      .select("*", { count: "exact", head: true })
      .eq("ip", ip)
      .eq("success", false)
      .gte("created_at", windowStart);
    if ((count ?? 0) >= MAX_FAILURES_PER_IP) return Errors.rateLimited();

    const { count: deviceFailures } = await admin
      .from("auth_attempts")
      .select("*", { count: "exact", head: true })
      .eq("device_id", deviceId)
      .eq("success", false)
      .gte("created_at", windowStart);
    if ((deviceFailures ?? 0) >= MAX_FAILURES_PER_DEVICE) return Errors.rateLimited();

    const { code } = requestBody;
    const digits = (code ?? "").toString().trim();
    if (!/^\d{6}$/.test(digits)) {
      await admin.from("auth_attempts").insert({ ip, device_id: deviceId, success: false });
      return Errors.invalidInput("Enter the six-digit code.");
    }

    const codeHash = await hashCode(digits);
    const { data: cred } = await admin
      .from("user_credentials")
      .select("user_id")
      .eq("code_hash", codeHash)
      .maybeSingle();

    if (!cred) {
      await admin.from("auth_attempts").insert({ ip, device_id: deviceId, success: false });
      return errorResponse("INVALID_CODE", "That code wasn't recognised.", 401);
    }

    const userId = cred.user_id as string;
    const bootstrapToken = randomToken(24);

    const { error: updErr } = await admin.auth.admin.updateUserById(userId, {
      password: bootstrapToken,
    });
    if (updErr) return Errors.server("Could not start a session.");

    const { data: authUser } = await admin.auth.admin.getUserById(userId);
    const { data: profile } = await admin
      .from("users").select("display_name").eq("id", userId).single();

    await admin.from("auth_attempts").insert({ ip, device_id: deviceId, success: true });
    await admin.from("audit_logs").insert({ actor_id: userId, action: "LOGIN", target_id: userId });

    return jsonResponse({
      userId,
      displayName: profile?.display_name ?? "",
      authEmail: authUser.user?.email ?? "",
      bootstrapToken,
    });
  } catch (_e) {
    return Errors.server();
  }
});
