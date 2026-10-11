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

    const { connectionId, action, accept } = await req.json().catch(() => ({}));
    if (!connectionId) return Errors.invalidInput("Missing request.");

    const admin = adminClient();
    const { data: conn } = await admin
      .from("connections")
      .select("connection_id,requester_id,addressee_id,status")
      .eq("connection_id", connectionId)
      .maybeSingle();

    if (!conn) return Errors.notFound("Connection not found.");
    const isParticipant = conn.requester_id === user.id || conn.addressee_id === user.id;
    if (!isParticipant) return Errors.forbidden();

    if (action === "remove") {
      const { error } = await admin.from("connections").delete()
        .eq("connection_id", connectionId);
      if (error) return Errors.server("Could not remove connection.");
      await admin.from("audit_logs").insert({
        actor_id: user.id, action: "CONNECTION_REMOVE", target_id: connectionId,
      });
      return jsonResponse({ ok: true });
    }

    if (action === "block") {
      const { error } = await admin.from("connections")
        .update({ status: "BLOCKED", updated_at: new Date().toISOString() })
        .eq("connection_id", connectionId);
      if (error) return Errors.server("Could not block connection.");
      await admin.from("audit_logs").insert({
        actor_id: user.id, action: "CONNECTION_BLOCK", target_id: connectionId,
      });
      return jsonResponse({ ok: true });
    }

    // Only the addressee of a PENDING request may accept/reject it.
    if (action !== "respond" || conn.addressee_id !== user.id || conn.status !== "PENDING") {
      return Errors.forbidden("This request can't be updated.");
    }

    if (accept) {
      const { error } = await admin.from("connections")
        .update({ status: "ACCEPTED", updated_at: new Date().toISOString() })
        .eq("connection_id", connectionId);
      if (error) return Errors.server("Could not accept connection.");
    } else {
      const { error } = await admin.from("connections").delete().eq("connection_id", connectionId);
      if (error) return Errors.server("Could not reject connection.");
    }
    await admin.from("audit_logs").insert({
      actor_id: user.id,
      action: accept ? "CONNECTION_ACCEPT" : "CONNECTION_REJECT",
      target_id: connectionId,
    });

    return jsonResponse({ ok: true });
  } catch (_e) {
    return Errors.server();
  }
});
