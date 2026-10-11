import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2.47.10";

/** Service-role client. Bypasses RLS — use only inside Edge Functions and
 *  always enforce authorization explicitly. */
export function adminClient(): SupabaseClient {
  return createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { persistSession: false, autoRefreshToken: false } },
  );
}
