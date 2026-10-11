import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { hashCode, randomSixDigitCode, randomToken } from "../_shared/crypto.ts";

const EMAIL_DOMAIN = Deno.env.get("AUTH_EMAIL_DOMAIN") ?? "users.triggerconnect.app";

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const { displayName } = await req.json().catch(() => ({}));
    const name = (displayName ?? "").toString().trim();
    if (name.length < 2 || name.length > 40) {
      return Errors.invalidInput("Please enter a name between 2 and 40 characters.");
    }

    const admin = adminClient();
    const bootstrapToken = randomToken(24);
    const authEmail = `${crypto.randomUUID()}@${EMAIL_DOMAIN}`;

    const { data: created, error: createErr } = await admin.auth.admin.createUser({
      email: authEmail,
      password: bootstrapToken,
      email_confirm: true,
    });
    if (createErr || !created.user) return Errors.server("Could not create account.");

    const userId = created.user.id;

    const { error: profileErr } = await admin.from("users").insert({
      id: userId,
      display_name: name,
    });
    if (profileErr) {
      await admin.auth.admin.deleteUser(userId); // rollback
      return Errors.server("Could not create profile.");
    }

    // Generate a unique six-digit code (retry on hash collision).
    let loginCode = "";
    let stored = false;
    for (let attempt = 0; attempt < 12 && !stored; attempt++) {
      loginCode = randomSixDigitCode();
      const codeHash = await hashCode(loginCode);
      const { error } = await admin.from("user_credentials").insert({
        user_id: userId,
        code_hash: codeHash,
      });
      if (!error) stored = true;
    }
    if (!stored) {
      await admin.auth.admin.deleteUser(userId);
      return Errors.server("Could not assign a login code. Please try again.");
    }

    await admin.from("audit_logs").insert({
      actor_id: userId, action: "REGISTER", target_id: userId,
    });

    return jsonResponse({ userId, displayName: name, authEmail, bootstrapToken, loginCode });
  } catch (_e) {
    return Errors.server();
  }
});
