import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const { fcmToken, platform, action } = await req.json().catch(() => ({}));
    if (!fcmToken || typeof fcmToken !== "string") return Errors.invalidInput("Missing token.");

    const admin = adminClient();
    if (action === "unregister") {
      const { error } = await admin.from("devices").delete()
        .eq("user_id", user.id).eq("fcm_token", fcmToken);
      if (error) return Errors.server("Could not unregister device.");
      return jsonResponse({ ok: true });
    }
    // A token is unique to one device/user: reassign it to the current user.
    const { error: deleteError } = await admin.from("devices").delete().eq("fcm_token", fcmToken);
    if (deleteError) return Errors.server("Could not register device.");
    const { error: insertError } = await admin.from("devices").insert({
      user_id: user.id,
      fcm_token: fcmToken,
      platform: platform ?? "android",
      last_active_at: new Date().toISOString(),
    });
    if (insertError) return Errors.server("Could not register device.");

    return jsonResponse({ ok: true });
  } catch (_e) {
    return Errors.server();
  }
});
