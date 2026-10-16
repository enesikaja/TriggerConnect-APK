import { handleOptions } from "../_shared/cors.ts";
import { Errors, jsonResponse } from "../_shared/http.ts";
import { adminClient } from "../_shared/supabase.ts";
import { requireUser } from "../_shared/auth.ts";
import { sendPush } from "../_shared/fcm.ts";

const PRIORITIES = new Set(["NORMAL", "IMPORTANT", "URGENT"]);

Deno.serve(async (req) => {
  const pre = handleOptions(req);
  if (pre) return pre;

  try {
    const user = await requireUser(req);
    if (!user) return Errors.unauthorized();

    const body = await req.json().catch(() => ({}));
    const { clientEventId, destinationType, destinationId, content, priority, notifySelf } = body;
    const text = (content ?? "").toString().trim();

    if (!clientEventId) return Errors.invalidInput("Missing event id.");
    if (!["USER", "GROUP"].includes(destinationType)) return Errors.invalidInput("Bad destination.");
    if (text.length < 1 || text.length > 280) return Errors.invalidInput("Message must be 1–280 characters.");
    const prio = PRIORITIES.has(priority) ? priority : "NORMAL";

    const admin = adminClient();

    const oneMinuteAgo = new Date(Date.now() - 60_000).toISOString();
    const { count: recentTriggers } = await admin.from("audit_logs")
      .select("*", { count: "exact", head: true })
      .eq("actor_id", user.id).eq("action", "TRIGGER_DISPATCH")
      .gte("created_at", oneMinuteAgo);
    if ((recentTriggers ?? 0) >= 20) {
      return Errors.rateLimited("Too many triggers. Wait a moment and try again.");
    }

    const { data: sender } = await admin.from("users").select("display_name").eq("id", user.id).single();
    const senderName = sender?.display_name ?? "Someone";

    // ---- Idempotency: same (sender, clientEventId) returns the original event.
    const { data: existing } = await admin.from("trigger_events")
      .select("*").eq("sender_id", user.id).eq("client_event_id", clientEventId).maybeSingle();
    if (existing) {
      if (existing.status === "DISPATCHING" &&
        Date.now() - new Date(existing.created_at).getTime() > 120_000) {
        await admin.from("trigger_events").update({ status: "FAILED" })
          .eq("event_id", existing.event_id).eq("status", "DISPATCHING");
        existing.status = "FAILED";
      }
      const { count } = await admin.from("event_recipients")
        .select("*", { count: "exact", head: true }).eq("event_id", existing.event_id);
      return jsonResponse({ event: eventRow(existing, senderName, count ?? 0), recipientCount: count ?? 0 });
    }

    // ---- Authorization + recipient resolution.
    let recipients: string[] = [];
    let destinationName = "";

    if (destinationType === "USER") {
      const { data: conn } = await admin.from("connections").select("status")
        .or(
          `and(requester_id.eq.${user.id},addressee_id.eq.${destinationId}),` +
            `and(requester_id.eq.${destinationId},addressee_id.eq.${user.id})`,
        ).maybeSingle();
      if (!conn || conn.status !== "ACCEPTED") return Errors.forbidden("You're not connected to this person.");
      const { data: target } = await admin.from("users").select("display_name").eq("id", destinationId).single();
      destinationName = target?.display_name ?? "Contact";
      recipients = [destinationId];
    } else {
      const { data: membership } = await admin.from("group_members").select("status")
        .eq("group_id", destinationId).eq("user_id", user.id).maybeSingle();
      if (!membership || membership.status !== "ACTIVE") return Errors.forbidden("You're not in this group.");
      const { data: group } = await admin.from("groups").select("name").eq("group_id", destinationId).single();
      destinationName = group?.name ?? "Group";
      const { data: members } = await admin.from("group_members").select("user_id")
        .eq("group_id", destinationId).eq("status", "ACTIVE");
      recipients = (members ?? [])
        .map((m) => m.user_id as string)
        .filter((id) => notifySelf ? true : id !== user.id);
    }

    // ---- Create the immutable event.
    const { data: event, error: evErr } = await admin.from("trigger_events").insert({
      sender_id: user.id,
      client_event_id: clientEventId,
      destination_type: destinationType,
      destination_id: destinationId,
      destination_name: destinationName,
      content: text,
      priority: prio,
      status: "DISPATCHING",
    }).select("*").single();
    if (evErr || !event) {
      // Possible race with idempotency insert — re-read and return it.
      const { data: raced } = await admin.from("trigger_events")
        .select("*").eq("sender_id", user.id).eq("client_event_id", clientEventId).maybeSingle();
      if (raced) return jsonResponse({ event: eventRow(raced, senderName, 0), recipientCount: 0 });
      return Errors.server("Could not create event.");
    }

    // Persist recipients before sending: a fast receiver can refresh immediately
    // after FCM delivery, and history/RLS requires these rows to exist already.
    if (recipients.length > 0) {
      const { error } = await admin.from("event_recipients").insert(
        recipients.map((rid) => ({
          event_id: event.event_id, recipient_id: rid, dispatch_status: "PENDING",
        })),
      );
      if (error) {
        await admin.from("trigger_events").update({ status: "FAILED" }).eq("event_id", event.event_id);
        return Errors.server("Could not record trigger recipients.");
      }
    }

    // ---- Resolve devices and push.
    const title = destinationType === "GROUP"
      ? `Trigger from ${senderName} — ${destinationName}`
      : `Trigger from ${senderName}`;

    const tokensByUser = new Map<string, string[]>();
    if (recipients.length > 0) {
      const { data: devices } = await admin.from("devices")
        .select("user_id,fcm_token").in("user_id", recipients);
      for (const d of devices ?? []) {
        const list = tokensByUser.get(d.user_id) ?? [];
        list.push(d.fcm_token);
        tokensByUser.set(d.user_id, list);
      }
    }

    const allTokens = [...tokensByUser.values()].flat();
    let results: Awaited<ReturnType<typeof sendPush>> = [];
    if (allTokens.length > 0) {
      try {
        results = await sendPush(allTokens, {
          title,
          body: text,
          data: { eventId: event.event_id, priority: prio },
        });
      } catch (_pushError) {
        // OAuth/config/network failures are recorded as dispatch failures.
        // The event must never remain indefinitely in DISPATCHING.
        results = allTokens.map((token) => ({ token, ok: false, unregistered: false }));
      }
    }
    const tokenOk = new Map(results.map((r) => [r.token, r.ok]));

    // Clean up unregistered tokens.
    const dead = results.filter((r) => r.unregistered).map((r) => r.token);
    if (dead.length > 0) await admin.from("devices").delete().in("fcm_token", dead);

    // ---- Per-recipient dispatch status + rows.
    let anyAccepted = false;
    let anyFailed = false;
    const rows = recipients.map((rid) => {
      const tokens = tokensByUser.get(rid) ?? [];
      let status: string;
      if (tokens.length === 0) {
        status = "SKIPPED";
      } else if (tokens.some((t) => tokenOk.get(t))) {
        status = "ACCEPTED";
        anyAccepted = true;
      } else {
        status = "FAILED";
        anyFailed = true;
      }
      return { event_id: event.event_id, recipient_id: rid, dispatch_status: status };
    });
    if (rows.length > 0) {
      const updates = await Promise.all(rows.map((row) =>
        admin.from("event_recipients").update({ dispatch_status: row.dispatch_status })
          .eq("event_id", row.event_id).eq("recipient_id", row.recipient_id)
      ));
      const recipientError = updates.find((result) => result.error)?.error;
      if (recipientError) {
        await admin.from("trigger_events").update({ status: "FAILED" })
          .eq("event_id", event.event_id);
        return Errors.server("Could not record trigger recipients.");
      }
    }

    // Status reflects the DISPATCH outcome, not device delivery. SKIPPED means
    // the recipient simply has no registered device yet — the event is still
    // recorded and shows in their in-app history, so an all-SKIPPED fan-out is
    // DISPATCHED, not FAILED. Only genuine push-provider failures mark FAILED.
    const finalStatus = recipients.length === 0
      ? "DISPATCHED"
      : anyAccepted
      ? (anyFailed ? "PARTIAL_FAILURE" : "DISPATCHED")
      : anyFailed
      ? "FAILED"
      : "DISPATCHED"; // all recipients SKIPPED (no devices) — not a failure
    await admin.from("trigger_events").update({ status: finalStatus }).eq("event_id", event.event_id);

    await admin.from("audit_logs").insert({
      actor_id: user.id, action: "TRIGGER_DISPATCH", target_id: event.event_id,
      metadata: { recipients: recipients.length, status: finalStatus },
    });

    event.status = finalStatus;
    return jsonResponse({ event: eventRow(event, senderName, recipients.length), recipientCount: recipients.length });
  } catch (_e) {
    return Errors.server();
  }
});

// deno-lint-ignore no-explicit-any
function eventRow(event: any, senderName: string, recipientCount: number) {
  return {
    event_id: event.event_id,
    sender_id: event.sender_id,
    sender_name: senderName,
    destination_type: event.destination_type,
    destination_id: event.destination_id,
    destination_name: event.destination_name,
    content: event.content,
    priority: event.priority,
    status: event.status,
    created_at: event.created_at,
    direction: "SENT",
    recipient_count: recipientCount,
    acknowledged_count: 0,
    my_acknowledgment: "NONE",
  };
}
