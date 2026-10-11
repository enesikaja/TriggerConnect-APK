import { handleOptions, corsHeaders } from "../_shared/cors.ts";
import { adminClient } from "../_shared/supabase.ts";

// Receives uncaught-exception reports from the Android app (CrashReporter) and
// stores them for remote diagnosis. Public (verify_jwt=false) because a crash
// can happen before a session exists. Best-effort; never fails hard.
Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;
  try {
    const b = await req.json().catch(() => ({}));
    const admin = adminClient();
    await admin.from("crash_logs").insert({
      app_version: (b.appVersion ?? "").toString().slice(0, 40),
      device: (b.device ?? "").toString().slice(0, 120),
      android_sdk: typeof b.androidSdk === "number" ? b.androidSdk : null,
      thread: (b.thread ?? "").toString().slice(0, 120),
      message: (b.message ?? "").toString().slice(0, 1000),
      stacktrace: (b.stacktrace ?? "").toString().slice(0, 8000),
    });
    return new Response(JSON.stringify({ ok: true }), {
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (_e) {
    return new Response(JSON.stringify({ ok: false }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
