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

    const { eventId } = await req.json().catch(() => ({}));
    if (!eventId) return Errors.invalidInput("Missing event.");

    const admin = adminClient();
    const { data: recipient } = await admin.from("event_recipients")
      .select("id").eq("event_id", eventId).eq("recipient_id", user.id).maybeSingle();
    if (!recipient) return Errors.forbidden("You can only acknowledge triggers sent to you.");

    const { error } = await admin.from("event_recipients")
      .update({ acknowledgment_status: "ACKNOWLEDGED", acknowledged_at: new Date().toISOString() })
      .eq("event_id", eventId).eq("recipient_id", user.id);
    if (error) return Errors.server("Could not acknowledge event.");

    return jsonResponse({ ok: true });
  } catch (_e) {
    return Errors.server();
  }
});
