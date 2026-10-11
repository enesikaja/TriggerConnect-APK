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

    const { invitationId } = await req.json().catch(() => ({}));
    if (!invitationId) return Errors.invalidInput("Missing invite.");

    const admin = adminClient();
    // The RPC locks the group row, enforces the 100-person cap, writes the
    // membership, and consumes the invite in one transaction.
    const { data: joinedRows, error: joinError } = await admin.rpc("join_group_from_invitation", {
      p_invitation_id: invitationId,
      p_user_id: user.id,
    });
    if (joinError || !joinedRows || joinedRows.length === 0) {
      if (joinError?.message?.includes("GROUP_FULL")) {
        return Errors.conflict("This group already has 100 members.");
      }
      return Errors.notFound("Invite is no longer valid.");
    }

    const joinedResult = joinedRows[0];
    const groupId = joinedResult.result_group_id as string;
    const { data: group } = await admin
      .from("groups").select("*").eq("group_id", groupId).single();
    if (!group) return Errors.notFound("Group not found.");

    const memberCount = async () => {
      const { count } = await admin.from("group_members")
        .select("*", { count: "exact", head: true })
        .eq("group_id", groupId).eq("status", "ACTIVE");
      return count ?? 0;
    };

    const buildGroup = async (role: string) => ({
      group_id: group.group_id,
      name: group.name,
      description: group.description,
      image_url: group.image_url,
      owner_id: group.owner_id,
      role,
      member_count: await memberCount(),
      requires_approval: group.requires_approval,
    });

    const status = joinedResult.membership_status as string;
    if (joinedResult.was_new) {
      await admin.from("audit_logs").insert({
        actor_id: user.id, action: "GROUP_JOIN", target_id: groupId,
      });
    }

    return jsonResponse({
      group: await buildGroup("MEMBER"),
      joined: status === "ACTIVE" && joinedResult.was_new,
      pendingApproval: status === "PENDING",
    });
  } catch (_e) {
    return Errors.server();
  }
});
