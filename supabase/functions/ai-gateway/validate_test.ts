import { assertEquals } from "jsr:@std/assert@1";
import { jwtSubject, mentionsJournal, parseRequest, validateModelOutput } from "./validate.ts";

Deno.test("accepts a well formed intent request", () => {
  const r = parseRequest({ task: "intent", payload: { transcript: "wake me at 6", now: "2026-10-06T09:00", categories: [] } });
  assertEquals(r.ok, true);
});

Deno.test("rejects unknown tasks and missing payloads", () => {
  assertEquals(parseRequest({ task: "chat", payload: {} }).ok, false);
  assertEquals(parseRequest({ task: "intent" }).ok, false);
  assertEquals(parseRequest("x").ok, false);
  assertEquals(parseRequest({ task: "intent", payload: { transcript: "  " } }).ok, false);
});

Deno.test("rejects anything flagged journal", () => {
  const r = parseRequest({ task: "intent", payload: { transcript: "dear diary", journal: true } });
  assertEquals(r.ok, false);
  if (!r.ok) assertEquals(r.status, 403);
  assertEquals(mentionsJournal({ payload: { nested: [{ source: "journal" }] } }), true);
  assertEquals(mentionsJournal({ payload: { journal: false } }), false);
});

Deno.test("validates intent output and clamps confidence", () => {
  const ok = validateModelOutput("intent", '```json\n{"intents":[{"type":"set_alarm","time":"06:00"}],"confidence":3}\n```');
  assertEquals(ok, { intents: [{ type: "set_alarm", time: "06:00" }], confidence: 1 });
  assertEquals(validateModelOutput("intent", '{"intents":[{"type":"launch_missiles"}]}'), null);
  assertEquals(validateModelOutput("intent", "not json"), null);
  assertEquals(validateModelOutput("intent", '{"intents":[]}'), { intents: [], confidence: 0 });
});

Deno.test("validates brief and stt output", () => {
  assertEquals(validateModelOutput("brief", '{"segments":[{"title":"Day","text":"Hi"}]}'), { segments: [{ title: "Day", text: "Hi" }] });
  assertEquals(validateModelOutput("brief", '{"segments":[]}'), null);
  assertEquals(validateModelOutput("stt", " buy milk "), { text: "buy milk" });
  assertEquals(validateModelOutput("stt", "  "), null);
});

Deno.test("reads the subject of a jwt", () => {
  const payload = btoa(JSON.stringify({ sub: "abc-123" })).replace(/=+$/, "");
  assertEquals(jwtSubject(`h.${payload}.s`), "abc-123");
  assertEquals(jwtSubject("garbage"), null);
});
