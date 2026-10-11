import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { hashInviteToken } from "../_shared/crypto.ts";

function invitationUsable(inv: { revoked: boolean; expires_at: string | null; max_uses: number | null; usage_count: number }): boolean {
  if (inv.revoked) return false;
  if (inv.expires_at && new Date(inv.expires_at) < new Date()) return false;
  if (inv.max_uses != null && inv.usage_count >= inv.max_uses) return false;
  return true;
}

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const { token } = await req.json().catch(() => ({}));
    if (!token) return Errors.invalidInput("Invalid QR code.");

    const admin = adminClient();

    const tokenHash = await hashInviteToken(token);
    const { data: inv } = await admin
      .from("invitations").select("*").eq("token_hash", tokenHash).maybeSingle();
    if (!inv || !invitationUsable(inv)) {
      return Errors.notFound("This invite has expired or is no longer valid.");
    }

    // 1) Personal connection token?
    if (inv.invitation_type === "USER") {
      const { data: personal } = await admin.from("users")
        .select("id,display_name").eq("id", inv.target_id).maybeSingle();
      if (!personal) return Errors.notFound("This user no longer exists.");
      const { data: existing } = await admin
        .from("connections").select("status")
        .or(
          `and(requester_id.eq.${user.id},addressee_id.eq.${personal.id}),` +
            `and(requester_id.eq.${personal.id},addressee_id.eq.${user.id})`,
        ).maybeSingle();
      return jsonResponse({
        invitationId: "",
        type: "USER",
        targetId: personal.id,
        displayName: personal.display_name,
        requiresApproval: false,
        alreadyMember: existing?.status === "ACCEPTED",
      });
    }

    // 2) Group invite token?
    if (inv.invitation_type !== "GROUP") return Errors.invalidInput("Unsupported invite type.");

    const { data: group } = await admin
      .from("groups").select("name,requires_approval").eq("group_id", inv.target_id).single();
    const { count } = await admin
      .from("group_members").select("*", { count: "exact", head: true })
      .eq("group_id", inv.target_id).eq("status", "ACTIVE");
    const { data: membership } = await admin
      .from("group_members").select("status")
      .eq("group_id", inv.target_id).eq("user_id", user.id).maybeSingle();

    return jsonResponse({
      invitationId: inv.invitation_id,
      type: "GROUP",
      targetId: inv.target_id,
      displayName: group?.name ?? "Group",
      memberCount: count ?? 0,
      requiresApproval: group?.requires_approval ?? false,
      alreadyMember: !!membership,
    });
  } catch (_e) {
    return Errors.server();
  }
});
