/** Pure request/response validation for the ai-gateway; no Deno or network APIs so it is easy to test. */

export type Task = "intent" | "brief" | "stt";

export interface GatewayRequest {
  task: Task;
  payload: Record<string, unknown>;
}

const TASKS: readonly string[] = ["intent", "brief", "stt"];
const MAX_TRANSCRIPT = 600;
const MAX_AUDIO_BASE64 = 2_000_000;
const INTENT_TYPES = new Set([
  "set_alarm", "change_alarm", "add_todo", "add_reminder", "log_expense",
  "log_habit", "journal_note", "query_next", "undo_last",
]);

/** True when [value] is a plain object (not null, not an array). */
function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** True when any key at any depth is a journal flag that is switched on. */
export function mentionsJournal(value: unknown): boolean {
  if (Array.isArray(value)) return value.some(mentionsJournal);
  if (!isObject(value)) return false;
  return Object.entries(value).some(([key, v]) => {
    if (key.toLowerCase() === "journal") return v !== false && v !== null && v !== undefined;
    if (key.toLowerCase() === "source" && typeof v === "string" && v.toLowerCase() === "journal") return true;
    return mentionsJournal(v);
  });
}

/** Parses the request body; returns an error code instead of throwing. */
export function parseRequest(body: unknown): { ok: true; req: GatewayRequest } | { ok: false; status: number; error: string } {
  if (!isObject(body)) return { ok: false, status: 400, error: "bad_request" };
  if (typeof body.task !== "string" || !TASKS.includes(body.task)) return { ok: false, status: 400, error: "bad_request" };
  if (!isObject(body.payload)) return { ok: false, status: 400, error: "bad_request" };
  if (mentionsJournal(body)) return { ok: false, status: 403, error: "journal_not_allowed" };
  const task = body.task as Task;
  const payload = body.payload;
  if (task === "intent") {
    if (typeof payload.transcript !== "string" || payload.transcript.trim() === "" || payload.transcript.length > MAX_TRANSCRIPT) {
      return { ok: false, status: 400, error: "bad_request" };
    }
  }
  if (task === "stt") {
    if (typeof payload.audio !== "string" || payload.audio === "" || payload.audio.length > MAX_AUDIO_BASE64) {
      return { ok: false, status: 400, error: "bad_request" };
    }
  }
  return { ok: true, req: { task, payload } };
}

/** Strips a ```json fence if the model added one. */
export function unfence(text: string): string {
  return text.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
}

/**
 * Validates a model reply for [task] and returns the normalised response object, or null when invalid.
 * intent -> {intents:[...], confidence}; brief -> {segments:[{title,text}]}; stt -> {text}.
 */
export function validateModelOutput(task: Task, raw: string): Record<string, unknown> | null {
  if (task === "stt") {
    const text = raw.trim();
    return text ? { text } : null;
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(unfence(raw));
  } catch {
    return null;
  }
  if (!isObject(parsed)) return null;
  if (task === "intent") {
    const intents = parsed.intents;
    if (!Array.isArray(intents) || intents.length > 5) return null;
    for (const i of intents) {
      if (!isObject(i) || typeof i.type !== "string" || !INTENT_TYPES.has(i.type)) return null;
    }
    const c = typeof parsed.confidence === "number" ? Math.min(1, Math.max(0, parsed.confidence)) : intents.length ? 0.8 : 0;
    return { intents, confidence: c };
  }
  const segments = parsed.segments;
  if (!Array.isArray(segments) || segments.length === 0 || segments.length > 8) return null;
  for (const s of segments) {
    if (!isObject(s) || typeof s.title !== "string" || typeof s.text !== "string") return null;
  }
  return { segments };
}

/** Decodes the payload of a JWT without verifying it (verification is done by Supabase auth). */
export function jwtSubject(token: string): string | null {
  const part = token.split(".")[1];
  if (!part) return null;
  try {
    const json = atob(part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "="));
    const sub = JSON.parse(json).sub;
    return typeof sub === "string" ? sub : null;
  } catch {
    return null;
  }
}
