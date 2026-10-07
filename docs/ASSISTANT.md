# Cove as a personal assistant (vision and plan)

The goal: a small, private "Jarvis" that handles the little details of life. You say or dump anything, it files it, and later it answers or acts: "I parked on level 3, pillar B", then "Where did I park?". It should feel like talking to someone who remembers, not like operating an app.

Status: **plan**, except where marked built. The first piece, hands-free end of speech, is built (section 1).

## What exists today

Voice goes `speech -> intent -> draft -> confirm -> execute -> undo`:

- `ai/speech/SpeechChain`: microphone engines with failover (ML Kit, Android on-device, Android system, typed).
- `ai/AiRouter` + `AiPolicy`: one place for privacy rules. Order: rules, Gemini Nano on device, Gemini cloud.
- `ai/model/VoiceIntent`: alarms, reminders, to-dos, expenses, habits, journal notes, workouts, `QueryNext`, `UndoLast`.
- `feature/voice/exec/IntentExecutor`: runs confirmed intents and records an undo payload.

What is missing is a way to **remember free-form things**, **answer questions about your data**, and **edit what you already made**.

## 1. Hands-free speaking (built)

You no longer have to tap Done. After you stop talking, Cove waits a short, adaptive pause and then processes what it heard. If you start speaking again inside that pause, it keeps listening and joins both parts.

| What you said last | Pause allowed (after the recognizer's own end-of-speech) |
|---|---|
| Sounds finished: ends in a number, `am`/`pm`, `tomorrow`, `rupees`, `please`, or a full stop | 1.5 s |
| Anything else | 2.2 s |
| Sounds unfinished: ends in `and`, `to`, `at`, `for`, `remind me`, a comma and similar | 4.5 s |

Code: `feature/voice/EndpointPolicy.kt` (pure and unit-tested) and the `dictate` / `listenSegment` loop in `VoiceViewModel`. **Done** still works for people who want to end it instantly. A new run always gets at least 1.2 s to hear you resume.

Next steps for the same feel:
- **Speculative understanding:** parse the text during the pause and keep the draft ready, discard it if you resume. This matters when the cloud model is the one answering.
- **Barge-in and follow-ups:** "make it 7", "also add eggs" refer to the last command for about two minutes.
- **A setting:** "Finish when I stop talking" on or off, and pause length (short, normal, patient).

## 2. The brain: tools, not one-off intents

Today every new capability needs a new hand-written intent. The framework to build is a small **tool registry**: each part of the app describes what it can do, and the planner picks one.

```kotlin
interface AssistantTool {
    val name: String                // "recall", "add_todo", "spend_summary"
    val description: String         // one line the planner reads
    val args: ArgSchema             // typed, validated like the existing JSON schemas
    val risk: Risk                  // ReadOnly | Reversible | Sensitive
    suspend fun run(args: Args): ToolResult   // text to say, plus an optional undo
}
```

- **Planning:** rules first (instant, offline, as now), then Gemini Nano, then cloud, using function-calling over the registered tools. The router and policy stay as they are. A new tool never touches privacy code.
- **Risk decides confirmation:**
  - `ReadOnly` (answers, searches): run immediately and speak the answer.
  - `Reversible` (add a to-do, log an expense): show the draft, save with an Undo toast. Later this can auto-save when confidence is high.
  - `Sensitive` (delete, money out of the ordinary, anything in the vault): always asks, and the vault asks for the biometric.
- **Existing intents become tools** one by one, so nothing is rewritten at once.

## 3. Memory: dump anything, ask later

This is the "car parking" case, and it is the heart of the vision.

**Remember** (`remember` tool, from "park", "note that", "remember", or any statement that is not a command):

```kotlin
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val text: String,            // what you said, kept verbatim
    val kind: String,            // place | thing | person | fact | plan | other (inferred, editable)
    val subject: String,         // "car", "passport", "Rahul's number": the thing it is about
    val keywords: String,        // normalised words for search
    val createdAt: Long,
    val expiresAt: Long? = null, // parking expires in a day; "where is my passport" never does
    val pinned: Boolean = false,
)
```

- **Newer beats older:** a new memory with the same `subject` and `kind` replaces the old one as the current answer (the old one stays in history). "Car on level 3" then "car on level 5" answers level 5.
- **Expiry by kind:** parking and "today only" notes expire by themselves. Things like locations of objects don't.

**Recall** (`recall` tool): "Where did I park?", "Where's my passport?", "What did I say about the geyser?"
1. Full-text search over `subject` and `keywords` with a recency boost. SQLite FTS works with Room and needs no model.
2. An answer is built from the top hit with a template ("You parked on level 3, pillar B, 2 hours ago").
3. Later, embeddings (PLAN 6a, EmbeddingGemma) for fuzzy questions like "that thing about the landlord".

**Privacy rule (important):** memories are as personal as the journal. They are treated as `Sensitivity.Journal`: on-device models and templates only. The cloud model never sees them, and the answer is composed locally. This extends `AiPolicy` by one capability, not by new rules.

**Where it lives:** Room table in the main database. Sync is off by default, with an opt-in later.

## 4. Questions about your own data

Read-only tools over repositories that already exist:

- **Money:** `spend_summary(period, category?)`: "How much did I spend on food this week?", "What's left in my budget?"
- **Day:** `what_next` (exists), `todos_left`, `habit_status`.
- **Training:** `next_workout` (exists), `last_session`.
- **Renewals and documents:** `whats_coming_up`, `when_does_X_expire`.

The query is parsed into a tool call with arguments. The numbers come from code, never from a model, so the answer cannot be invented. A model only phrases it, and a template covers the offline case.

## 5. Edit and complete by voice

New tools: `complete_todo`, `reschedule(target, when)`, `rename`, `delete_last`, `snooze`. Targets are resolved against what is on screen or was just created, then by fuzzy title match, and ambiguity becomes a spoken question: "Which one: Call dentist or Call mom?"

## 6. Wallet, minimal: a Keep vault (no cards)

Cards are dropped. What stays is the documents vault from `docs/WALLET.md`, narrowed:

- Documents and scans (Aadhaar, PAN, passport, insurance, warranties and bills), boarding passes and tickets.
- Encrypted with the biometric-bound key from `WALLET.md`; never synced, never in the Drive backup.
- **Renewals** come from the same data: anything with an expiry date feeds one "Coming up" list on Today. The SMS parser already recognises mandates, dues and statements, which can pre-fill subscription renewals.
- Voice hooks: "When does my passport expire?", "Show my boarding pass", "Remind me to renew insurance in March".

## 7. Proactive, never noisy

The `suggest` package already notices patterns. The assistant should use it sparingly: at most one "Cove noticed" item on Today, bundled with the existing nudges, off by default for anything new. Examples: a renewal in 7 days, a parking note you probably no longer need, a weekly review on Sunday.

## 8. Guardrails: how it stays small

1. No new tabs. New capability surfaces on Today, in the voice result, or in Me.
2. New things are new *tool types* or *kinds* of existing data, not new silos.
3. Every tool has a rule-based path that works offline, so the model is an upgrade, not a dependency.
4. Speed budget: cold start and first listen must not get slower. Nothing new runs at launch.
5. Anything the assistant can do can be undone or is read-only.
6. Private by construction: sensitive classes stay on device through `AiPolicy`, tested by `FeatureBoundaryTest` and a new test that no memory text reaches a cloud provider.

## 9. Phases

| Phase | Ships | How to verify |
|---|---|---|
| A (done) | Hands-free end of speech | `EndpointPolicyTest`; on device: speak, pause, speak again joins both, pause ends and shows the result |
| B | Tool registry; port `query_next`, `undo_last`, `add_todo` onto it | Existing `RuleParserTest` and `IntentExecutorTest` pass unchanged; new registry tests |
| C (first slice built) | Memory: `remember` and `recall` with keyword ranking, expiry and newer-wins. Built: `Remember` and `Recall` intents, `memories` table (migration 11 to 12), rules for parking, "I put X in Y", "remember that ...", "where is/did I ...", and a fallback that offers to keep any statement nothing else understood. Not built: full-text search, embeddings, a Memories screen to browse or forget, the on-device model for fuzzy questions | Unit tests for supersede, expiry and ranking; privacy test; "park" then "where did I park" on device |
| D | Money and day questions (`spend_summary` and others) | Tests against fixed ledger data; spoken and shown answers match |
| E | Edit by voice (`complete_todo`, `reschedule`, `snooze`) with ambiguity questions | Parser tests; undo works for each |
| F | Keep vault and Renewals | `WALLET.md` phases 1 and 2, narrowed |
| G | Speculative parse, follow-ups, weekly review, optional embeddings | Latency measurements before and after |

## 10. Open questions

1. Should unfinished sentences ever be completed by asking ("Remind you to...?") instead of waiting?
2. Memory sync: never, or opt-in and end-to-end encrypted?
3. Auto-save when confident (with Undo), or always show the draft?
4. Wake word ("Hey Cove") is out of scope for now: a permanent microphone is a big privacy and battery cost. Quick settings tile, shortcut and widget remain the entry points.
