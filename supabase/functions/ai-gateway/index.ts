// Thin Gemini gateway (PLAN 6b). Deploy: `supabase functions deploy ai-gateway`.
// Secrets: GEMINI_API_KEY, GEMINI_MODEL (Flash-Lite), GEMINI_RETRY_MODEL (Flash, optional), ALLOWED_USER_ID.
// SUPABASE_URL and SUPABASE_ANON_KEY are provided by the platform.
import { jwtSubject, parseRequest, type GatewayRequest, validateModelOutput } from "./validate.ts";

const TIMEOUT_MS = 4000;
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const unavailable = () => json({ error: "unavailable" }, 200);

/** Confirms the bearer token is a live Supabase session and returns its user id. */
async function verifiedUserId(token: string): Promise<string | null> {
  const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/auth/v1/user`, {
    headers: { apikey: Deno.env.get("SUPABASE_ANON_KEY") ?? "", authorization: `Bearer ${token}` },
  });
  if (!res.ok) return null;
  const id = (await res.json()).id;
  return typeof id === "string" ? id : jwtSubject(token);
}

function systemPrompt(req: GatewayRequest): string {
  const p = req.payload;
  if (req.task === "intent") {
    const cats = Array.isArray(p.categories) ? p.categories.join(", ") : "";
    return `You turn one spoken command into JSON. Reply with JSON only, exactly {"intents":[...],"confidence":0..1}.
Intent types: set_alarm{time:"HH:mm",label,days[]}, change_alarm{time,label}, add_todo{items:[{title,category}]},
add_reminder{title,at:"yyyy-MM-ddTHH:mm"|null}, log_expense{amount,category,paid_with,note,received},
log_habit{name}, journal_note{text}, query_next{}, undo_last{}.
Todo categories: ${cats}. Current local time: ${p.now ?? ""} (${p.timezone ?? ""}). Use 24-hour times.
If unclear reply {"intents":[],"confidence":0}.`;
  }
  if (req.task === "brief") {
    return `Write a calm 3 to 5 segment morning brief from the supplied facts. Reply with JSON only: {"segments":[{"title":"","text":""}]}.`;
  }
  return "Transcribe the audio. Reply with the plain transcript text only.";
}

async function callGemini(model: string, req: GatewayRequest): Promise<string | null> {
  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return null;
  const parts: unknown[] = [{ text: req.task === "stt" ? "Transcribe." : JSON.stringify(req.payload) }];
  if (req.task === "stt") {
    parts.push({ inline_data: { mime_type: String(req.payload.mime ?? "audio/mp4"), data: req.payload.audio } });
  }
  const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, {
    method: "POST",
    headers: { "content-type": "application/json", "x-goog-api-key": key },
    body: JSON.stringify({
      systemInstruction: { parts: [{ text: systemPrompt(req) }] },
      contents: [{ role: "user", parts }],
      generationConfig: { temperature: 0.1, ...(req.task === "stt" ? {} : { responseMimeType: "application/json" }) },
    }),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  if (!res.ok) return null;
  const data = await res.json();
  return data?.candidates?.[0]?.content?.parts?.[0]?.text ?? null;
}

/** Tries [model]; any failure or invalid output yields null so the caller can retry once on Flash. */
async function attempt(model: string | undefined, req: GatewayRequest): Promise<Record<string, unknown> | null> {
  if (!model) return null;
  try {
    const text = await callGemini(model, req);
    return text ? validateModelOutput(req.task, text) : null;
  } catch {
    return null;
  }
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  const token = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
  const userId = token ? await verifiedUserId(token) : null;
  const allowed = Deno.env.get("ALLOWED_USER_ID");
  if (!userId || !allowed || userId !== allowed) return json({ error: "forbidden" }, 403);

  const parsed = parseRequest(await request.json().catch(() => null));
  if (!parsed.ok) return json({ error: parsed.error }, parsed.status);

  const result = (await attempt(Deno.env.get("GEMINI_MODEL"), parsed.req)) ??
    (await attempt(Deno.env.get("GEMINI_RETRY_MODEL") ?? "gemini-2.5-flash", parsed.req));
  return result ? json(result) : unavailable();
});
