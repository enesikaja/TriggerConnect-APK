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

    const admin = adminClient();
    await admin.from("audit_logs").insert({
      actor_id: user.id, action: "ACCOUNT_DELETE", target_id: user.id,
    });

    // Deleting the auth user cascades to public.users and all owned data
    // (connections, groups, memberships, devices, messages, events) via the
    // ON DELETE CASCADE foreign keys.
    const { error } = await admin.auth.admin.deleteUser(user.id);
    if (error) return Errors.server("Could not delete account.");

    return jsonResponse({ ok: true });
  } catch (_e) {
    return Errors.server();
  }
});
