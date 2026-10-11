import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { hashInviteToken } from "../_shared/crypto.ts";

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

    return jsonResponse({ ok: true, status: "PENDING" });
  } catch (_e) {
    return Errors.server();
  }
});
