import { corsHeaders } from "./cors.ts";

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

/** Error shape consumed by the Android SupabaseApi: { code, error }. */
export function errorResponse(code: string, message: string, status: number): Response {
  return jsonResponse({ code, error: message }, status);
}

export const Errors = {
  unauthorized: () => errorResponse("UNAUTHORIZED", "Sign in required.", 401),
  invalidInput: (msg = "Invalid input.") => errorResponse("INVALID_INPUT", msg, 400),
  forbidden: (msg = "Not allowed.") => errorResponse("FORBIDDEN", msg, 403),
  notFound: (msg = "Not found.") => errorResponse("NOT_FOUND", msg, 404),
  conflict: (msg = "Conflict.") => errorResponse("CONFLICT", msg, 409),
  rateLimited: (msg = "Too many attempts. Try again later.") =>
    errorResponse("RATE_LIMITED", msg, 429),
  server: (msg = "Server error.") => errorResponse("SERVER", msg, 500),
};
