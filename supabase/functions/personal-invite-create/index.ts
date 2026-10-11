import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { hashInviteToken, randomToken } from "../_shared/crypto.ts";

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;
  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();
    const admin = adminClient();
    const token = randomToken(24);
    const tokenHash = await hashInviteToken(token);
    await admin.from("invitations").update({ revoked: true })
      .eq("created_by", user.id).eq("invitation_type", "USER");
    const { error } = await admin.from("invitations").insert({
      invitation_type: "USER",
      target_id: user.id,
      token_hash: tokenHash,
      created_by: user.id,
      usage_count: 0,
    });
    if (error) return Errors.server("Could not create personal QR.");
    return jsonResponse({ token });
  } catch (_e) {
    return Errors.server();
  }
});
