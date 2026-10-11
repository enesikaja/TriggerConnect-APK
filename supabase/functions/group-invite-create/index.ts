import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { hashInviteToken, stableInviteToken } from "../_shared/crypto.ts";

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const { groupId } = await req.json().catch(() => ({}));
    if (!groupId) return Errors.invalidInput("Missing group.");

    const admin = adminClient();
    const { data: membership } = await admin
      .from("group_members").select("role,status")
      .eq("group_id", groupId).eq("user_id", user.id).maybeSingle();
    if (!membership || membership.status !== "ACTIVE" ||
      !["OWNER", "ADMIN"].includes(membership.role)) {
      return Errors.forbidden("Only group admins can create invites.");
    }

    // A group has one stable QR. The opaque token is deterministic only on the
    // server, keyed by INVITE_HASH_SECRET, and differs for every group.
    const token = await stableInviteToken("GROUP", groupId);
    const tokenHash = await hashInviteToken(token);
    const { data: invitationId, error } = await admin.rpc("ensure_group_invitation", {
      p_group_id: groupId,
      p_token_hash: tokenHash,
      p_created_by: user.id,
    });
    if (error || !invitationId) return Errors.server("Could not load group QR.");

    return jsonResponse({
      invitationId,
      token,
      expiresAt: null,
      maxUses: null,
      usageCount: 0,
    });
  } catch (_e) {
    return Errors.server();
  }
});
