import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import type { SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

async function roleOf(admin: SupabaseClient, groupId: string, userId: string): Promise<string | null> {
  const { data } = await admin.from("group_members").select("role,status")
    .eq("group_id", groupId).eq("user_id", userId).maybeSingle();
  return data && data.status === "ACTIVE" ? data.role : null;
}

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const body = await req.json().catch(() => ({}));
    const action = body.action as string;
    const admin = adminClient();

    switch (action) {
      case "create": {
        const name = (body.name ?? "").toString().trim();
        if (name.length < 2 || name.length > 60) return Errors.invalidInput("Group name must be 2–60 characters.");
        const imageUrl = (body.imageUrl ?? "").toString().trim();
        if (imageUrl && (!imageUrl.startsWith("https://") || imageUrl.length > 500)) {
          return Errors.invalidInput("Group image must be a valid HTTPS URL.");
        }
        const { data: group, error } = await admin.from("groups").insert({
          name,
          description: body.description ?? null,
          image_url: imageUrl || null,
          owner_id: user.id,
          requires_approval: body.requiresApproval ?? false,
        }).select("*").single();
        if (error || !group) return Errors.server("Could not create group.");
        const { error: memberError } = await admin.from("group_members").insert({
          group_id: group.group_id, user_id: user.id, role: "OWNER", status: "ACTIVE",
        });
        if (memberError) {
          await admin.from("groups").delete().eq("group_id", group.group_id);
          return Errors.server("Could not create group membership.");
        }
        return jsonResponse({
          ok: true,
          group: {
            group_id: group.group_id, name: group.name, description: group.description,
            image_url: group.image_url, owner_id: group.owner_id, role: "OWNER",
            member_count: 1, requires_approval: group.requires_approval,
          },
        });
      }

      case "rename": {
        const role = await roleOf(admin, body.groupId, user.id);
        if (role !== "OWNER" && role !== "ADMIN") return Errors.forbidden();
        const name = (body.name ?? "").toString().trim();
        if (name.length < 2 || name.length > 60) return Errors.invalidInput();
        await admin.from("groups").update({ name, updated_at: new Date().toISOString() })
          .eq("group_id", body.groupId);
        return jsonResponse({ ok: true });
      }

      case "leave": {
        const { data: g } = await admin.from("groups").select("owner_id").eq("group_id", body.groupId).maybeSingle();
        if (!g) return Errors.notFound();
        if (g.owner_id === user.id) return Errors.forbidden("Owners must delete the group instead of leaving.");
        await admin.from("group_members").delete().eq("group_id", body.groupId).eq("user_id", user.id);
        return jsonResponse({ ok: true });
      }

      case "delete": {
        const { data: g } = await admin.from("groups").select("owner_id").eq("group_id", body.groupId).maybeSingle();
        if (!g) return Errors.notFound();
        if (g.owner_id !== user.id) return Errors.forbidden("Only the owner can delete the group.");
        await admin.from("invitations").delete().eq("target_id", body.groupId);
        await admin.from("groups").delete().eq("group_id", body.groupId); // cascades members
        return jsonResponse({ ok: true });
      }

      case "removeMember": {
        const role = await roleOf(admin, body.groupId, user.id);
        if (role !== "OWNER" && role !== "ADMIN") return Errors.forbidden();
        const targetRole = await roleOf(admin, body.groupId, body.targetUserId);
        if (targetRole === "OWNER") return Errors.forbidden("You can't remove the owner.");
        await admin.from("group_members").delete()
          .eq("group_id", body.groupId).eq("user_id", body.targetUserId);
        return jsonResponse({ ok: true });
      }

      case "approveMember": {
        const role = await roleOf(admin, body.groupId, user.id);
        if (role !== "OWNER" && role !== "ADMIN") return Errors.forbidden();
        const { error } = await admin.from("group_members")
          .update({ status: body.approve ? "ACTIVE" : "REJECTED" })
          .eq("group_id", body.groupId).eq("user_id", body.targetUserId)
          .eq("status", "PENDING");
        if (error) return Errors.server("Could not update membership request.");
        if (!body.approve) {
          await admin.from("group_members").delete()
            .eq("group_id", body.groupId).eq("user_id", body.targetUserId)
            .eq("status", "REJECTED");
        }
        return jsonResponse({ ok: true });
      }

      case "setRole": {
        const role = await roleOf(admin, body.groupId, user.id);
        if (role !== "OWNER") return Errors.forbidden("Only the owner can change roles.");
        if (body.targetUserId === user.id) return Errors.invalidInput("You can't change your own role.");
        const newRole = body.makeAdmin ? "ADMIN" : "MEMBER";
        await admin.from("group_members").update({ role: newRole })
          .eq("group_id", body.groupId).eq("user_id", body.targetUserId).neq("role", "OWNER");
        return jsonResponse({ ok: true });
      }

      case "revokeInvites": {
        const role = await roleOf(admin, body.groupId, user.id);
        if (role !== "OWNER" && role !== "ADMIN") return Errors.forbidden();
        await admin.from("invitations").update({ revoked: true }).eq("target_id", body.groupId);
        return jsonResponse({ ok: true });
      }

      default:
        return Errors.invalidInput("Unknown action.");
    }
  } catch (_e) {
    return Errors.server();
  }
});
