import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { hashInviteToken } from "../_shared/crypto.ts";
import { sendPush } from "../_shared/fcm.ts";

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const { inviteToken } = await req.json().catch(() => ({}));
    if (!inviteToken) return Errors.invalidInput("Invalid invite.");

    const admin = adminClient();
    const tokenHash = await hashInviteToken(inviteToken);
    const { data: invitation } = await admin.from("invitations").select("*")
      .eq("token_hash", tokenHash).eq("invitation_type", "USER").maybeSingle();
    if (!invitation || invitation.revoked) return Errors.notFound("This QR code is not valid.");
    const targetId = invitation.target_id as string;
    if (targetId === user.id) return Errors.invalidInput("You can't connect with yourself.");

    const { data: existing } = await admin
      .from("connections")
      .select("connection_id,status")
      .or(
          `and(requester_id.eq.${user.id},addressee_id.eq.${targetId}),` +
          `and(requester_id.eq.${targetId},addressee_id.eq.${user.id})`,
      )
      .maybeSingle();

    if (existing) {
      if (existing.status === "ACCEPTED") return Errors.conflict("You're already connected.");
      if (existing.status === "BLOCKED") return Errors.forbidden("Unable to connect.");
      return jsonResponse({ ok: true, status: "PENDING" }); // already pending — idempotent
    }

    const { error: connectionError } = await admin.from("connections").insert({
      requester_id: user.id,
      addressee_id: targetId,
      status: "PENDING",
    });
    if (connectionError) {
      // A concurrent reverse request is treated as an existing pending request.
      if (connectionError.code === "23505") return jsonResponse({ ok: true, status: "PENDING" });
      return Errors.server("Could not send connection request.");
    }
    await admin.from("audit_logs").insert({
      actor_id: user.id, action: "CONNECTION_REQUEST", target_id: targetId,
    });

    // Notify the addressee with a push (best-effort; never fails the request).
    try {
      const { data: me } = await admin.from("users").select("display_name").eq("id", user.id).single();
      const requesterName = me?.display_name ?? "Someone";
      const { data: devices } = await admin.from("devices").select("fcm_token").eq("user_id", targetId);
      const tokens = (devices ?? []).map((d) => d.fcm_token as string);
      if (tokens.length > 0) {
        const results = await sendPush(tokens, {
          title: "New connection request",
          body: `${requesterName} wants to connect with you`,
          data: { type: "connection" },
        });
        const dead = results.filter((r) => r.unregistered).map((r) => r.token);
        if (dead.length > 0) await admin.from("devices").delete().in("fcm_token", dead);
      }
    } catch (_notifyErr) {
      // ignore push failures
    }

    return jsonResponse({ ok: true, status: "PENDING" });
  } catch (_e) {
    return Errors.server();
  }
});
