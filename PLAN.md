# Cove — Build Plan

A calm, voice-first companion for Android (alarms, to-dos, habits, money, journal, morning brief).
Private app for one user. Target running cost: **₹0/month**, worst case ~₹0–250.

Source design: `Custom Still design implementation.zip` (Still.dc.html, Still Final.dc.html — 25 screens, light + dark, theme "Dusk").

> **Providers (final): two accounts only.** **Google** (Gemini API on the paid pay-as-you-go tier, Drive, Google Sign-In) and **Supabase** (free plan). Everything else is on-device or a keyless public API (Open-Meteo). Pricing and limits were checked via web search in Oct 2026 and drift often; re-check dashboards before relying on a number.

---

## 1. Name

**Cove.** A cove is a small sheltered inlet: calm water protected from the weather. That is the app's job (a quiet, protected place for your day). Short, easy to say to a voice assistant, and reads well as a launcher label.
Package id: `app.cove.companion`. Drive folder: "Cove". Backups: `cove-YYYY-MM.json.gz`.
Alternatives considered: *Lull*, *Hush*, *Nook*, *Hearth*, *Dusk* (kept as the theme name). Trademark and Play Store collisions not checked; matters little for a private sideloaded app.
**Earlier working names: "Still" (design files) and "Murmur".** The designs still say "Still" in copy ("Hi. I'm Still.", wordmark, "Hold to ask Still"); the app uses "Cove" everywhere.

---

## 2. Decisions at a glance

| Concern | Choice | Why / cost |
|---|---|---|
| Platform | **Native Android: Kotlin + Jetpack Compose** | Android-only; best access to exact alarms, Glance widgets, BiometricPrompt, AICore (Gemini Nano). Flutter/RN make widgets and alarms harder. Free |
| Local DB | **Room (SQLite) + SQLCipher** | Offline-first; app works with no network. Source of truth on device |
| Cloud DB + sync | **Supabase (Postgres)** free plan | 500 MB DB, 1 GB files, 50k MAU. Relational fits money/habits. Free projects pause after 7 days of inactivity — daily use prevents it; add a weekly cron ping anyway |
| Auth | Supabase Auth (Google sign-in, one account) + **BiometricPrompt** app lock | Free |
| Backend logic | **Supabase Edge Functions** (Deno) | Holds AI keys server-side; free allowance is plenty for one user |
| Intent parsing / short text | **Gemini Nano on-device** (ML Kit GenAI Prompt API, Pixel 10) → **Gemini API Flash-Lite** (paid tier) via the `ai-gateway` Edge Function (§6b) | On-device = free, offline. Cloud fallback is one provider, so nothing to orchestrate |
| Speech → text | **ML Kit GenAI SpeechRecognizer** (on-device; Advanced mode on Pixel 10, Basic on API 31+) → fallback **Gemini API audio transcription** for voice commands only | Free on-device; fallback is pennies. Journal voice notes are on-device only |
| Text → speech | **Android `TextToSpeech`** (Google offline voices) | Free, offline. Optional Gemini TTS later for a nicer brief voice |
| Weather | **Open-Meteo** | Free, no key |
| Calendar | Android **CalendarContract** provider (reads Google Calendar already synced on phone) | No OAuth, no API cost |
| Widgets | **Jetpack Glance** | Compose-style widgets |
| Background | **WorkManager** (sync, brief generation) + **AlarmManager.setAlarmClock** (alarms) | Free |
| Distribution | Sideload signed APK (or Firebase App Distribution, free) | Skips the $25 Play fee; Play internal testing is optional |

---

## 3. Architecture

```
┌──────────────────────── Android app ────────────────────────┐
│ Compose UI ──► ViewModels ──► Use cases ──► Repositories     │
│                                   │             │            │
│                       ┌───────────┘             ▼            │
│              Voice pipeline                Room (SQLCipher)  │
│   mic → STT → Intent parser → Executor        ▲   │ outbox   │
│                       │                       │   ▼          │
│          Nano ▸ Gemini API fallback     SyncWorker (WorkManager)
└───────────────────────┬──────────────────────┬──────────────┘
                        │ HTTPS (JWT)          │ HTTPS
                        ▼                      ▼
              Supabase Edge Functions     Supabase Postgres + Storage
              (ai-gateway)                (RLS: user_id = auth.uid())
                        │
                        ▼
              Gemini API, paid tier (key lives here, never in the APK)
```

Principles taken from the design doc and encoded as rules:
1. **One focal point per screen** — Today shows voice orb + one "next thing" card; lists collapsed.
2. **Voice has an off switch** — `spoken_replies` setting gates all TTS.
3. **Explainable AI** — every decision card stores `reasons[]` (powers the "Why this?" sheet) and every action stores an `undo_payload`.
4. **Adapts to the hour** — a `DayPhase` (morning/day/evening) value drives Today layout and nudges.
5. **Nudges bundled** — notifications go through one `NudgeScheduler` (9 am / 1 pm / 6 pm in "Bundled" mode).

---

## 4. Project file structure

Single Gradle module (`:app`), manual DI through `AppContainer` (see "Decisions that changed" under §10). Package `app.cove.companion`.

```
cove/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── kotlin/app/cove/companion/
│   │   ├── CoveApp.kt              # Application: builds AppContainer, starts services off the main thread
│   │   ├── AppContainer.kt         # manual dependency graph (lazy heavy services)
│   │   ├── MainActivity.kt         # single activity, lock gate, debug intent hooks (debug builds only)
│   │   ├── UpdatingSplash.kt       # shown until the encrypted DB is ready
│   │   ├── DebugStrictMode.kt      # debug-only StrictMode policy
│   │   ├── core/                   # Clock (freezable), Format (money/time), Notifications, Permissions, ViewModels, net/
│   │   ├── design/                 # Color, Type (Geist), Shapes, Icons, Theme, components/ (CoveText, Sheet, Dock, Orb, ...)
│   │   ├── navigation/             # Routes, CoveNavHost, MainScreen (tabs), DebugLaunch
│   │   ├── security/               # SQLCipher open helper, key (Keystore SecretBox), plaintext migrator
│   │   ├── data/
│   │   │   ├── local/              # CoveDatabase (Room, v4, FTS4 journal search), entity/, dao/
│   │   │   ├── repo/               # repositories; every write goes through ChangeLog.mark (outbox)
│   │   │   ├── sync/               # SyncEngine/Manager/Worker, Ktor-based Supabase remote, conflict resolver
│   │   │   ├── auth/               # Supabase session via Google ID token (Credential Manager), encrypted store
│   │   │   ├── drive/              # Google Drive uploader and monthly backup (drive.file scope)
│   │   │   ├── media/              # image compression, thumbnails, voice notes, media upload worker
│   │   │   ├── backup/             # export/import, retention
│   │   │   ├── ai/                 # AiGateway (Ktor to the ai-gateway Edge Function), OnDeviceLlm
│   │   │   └── insights/           # journal search/indexer, Nano insights, foreground tracker
│   │   └── feature/
│   │       ├── onboarding/ today/ plan/ habits/ money/ journal/ me/ sync/ security/
│   │       ├── alarms/             # AlarmManager.setAlarmClock, ring service/activity, boot receiver
│   │       ├── voice/              # speech, intent parser, executor, TTS, quick-listen tile/shortcut
│   │       ├── brief/              # generator, player, Open-Meteo, calendar, worker, geocoder
│   │       ├── suggest/            # rule-based decision engine ("Why this?")
│   │       ├── nudges/             # bundled notifications and reminders
│   │       └── widgets/            # Glance widgets + updater
│   └── res/                        # fonts, drawables, xml/ widget infos, shortcuts
├── app/src/test/kotlin/            # JVM unit tests (pure logic, Ktor MockEngine, fake stores)
├── supabase/                       # migrations 0001-0003, functions/ai-gateway (Deno), README
├── docs/                           # CONTRIBUTING, DRIVE_SETUP, SECURITY, RELEASE
├── design/                         # reference PNGs and HTML frames
└── tools/                          # run.sh, shot.sh, compare.py
```

### Screen to code map

| Design frame | Feature |
|---|---|
| 01 Welcome, 02 Wake-up | `feature/onboarding` |
| 03/04 Today, F1 Evening | `feature/today` |
| 05 Listening, 06 Voice result, T5 Voice sorted | `feature/voice` |
| 07 Decision, F3 Why this | `feature/suggest` |
| 08 Brief player | `feature/brief` |
| 09 Plan, T1-T4, T6 | `feature/plan` |
| 10 Habits | `feature/habits` |
| 11/12 Money | `feature/money` |
| 13/14/15 Journal | `feature/journal` |
| 16 Me, F2 Calm settings | `feature/me`, `feature/security` |

---

## 5. Data model

Room mirrors Postgres. Conventions on **every** synced table:
`id uuid pk (client-generated)`, `updated_at timestamptz`, `deleted_at timestamptz null` (soft delete so deletes sync). Postgres adds `user_id uuid default auth.uid()` + RLS. Money is `bigint` paise (never floats).

```sql
-- settings (one row per user)
settings(user_id pk, display_name, wake_time time, theme text,         -- light|dark|system
         font text, text_scale real, spoken_replies bool, reduce_motion text,
         nudge_mode text,                                              -- as_they_come|bundled|brief_only
         one_thing_mode bool, suggest_decisions bool, updated_at)

-- plan
alarms(id, time time, days smallint, label, sound, snooze_minutes int, snooze_max int,
       enabled bool, next_fire_at timestamptz, updated_at, deleted_at)
reminders(id, title, due_at, repeat_rule text null, category_id null, status, source, updated_at, deleted_at)
chores(id, title, cadence_days int, last_done_at, next_due_at, updated_at, deleted_at)

-- to-dos
todo_categories(id, name, icon, sort int, updated_at, deleted_at)
todos(id, category_id fk, title, notes, due_at null, status text,      -- open|done
      sort int, source text,                                           -- manual|voice
      completed_at, updated_at, deleted_at)

-- habits
habits(id, name, icon, target_per_week int, sort, archived bool, updated_at, deleted_at)
habit_logs(id, habit_id fk, day date, count int, updated_at, deleted_at, unique(habit_id, day))

-- money
expense_categories(id, name, icon, monthly_budget_paise bigint, sort, updated_at, deleted_at)
expenses(id, amount_paise bigint, category_id fk, merchant, note, spent_at,
         source text,                                                  -- manual|voice|sms
         updated_at, deleted_at)
budgets(id, month text 'YYYY-MM', total_paise bigint, updated_at, deleted_at)

-- journal
journal_entries(id, day date, title, body, mood text, updated_at, deleted_at)
journal_media(id, entry_id fk, kind text,                              -- photo|voice
              storage_path, local_uri, duration_ms, bytes, caption,
              upload_state text,                                       -- pending|done|failed
              drive_file_id text null, thumb_local_uri,
              updated_at, deleted_at)

-- me
saved_items(id, kind text, title, subtitle, ext_source, ext_id, updated_at, deleted_at)  -- book|movie
integrations(id, provider text, status text, config jsonb, updated_at)                    -- notion|letterboxd|goodreads

-- AI / explainability (these make "Why this?" and Undo possible)
decisions(id, kind text, trigger text, reasons jsonb,                  -- ["Phone in use until 1:40 am", …]
          options jsonb, chosen text null, status text,                -- shown|confirmed|dismissed|muted
          created_at, updated_at)
suggestion_prefs(kind pk, muted bool, updated_at)                      -- "Don't suggest this again"
voice_commands(id, transcript, intent jsonb, result text,
               undo_payload jsonb, undone bool, created_at)
briefs(id, day date unique, script jsonb, audio_local_path, duration_s, generated_at)

-- local-only (not synced)
outbox(seq autoinc, table_name, row_id, op, queued_at)
sync_state(table_name pk, last_pull_at timestamptz, last_push_seq)
usage_events(ts, kind)  -- screen-on/off aggregated on device; feeds DecisionEngine, never uploaded
```

Postgres: index `(user_id, updated_at)` on every synced table (sync cursor); RLS `using (user_id = auth.uid())`.

### Sync design (simple, enough for one user + maybe two devices)
- **Write path:** repo writes Room + appends to `outbox` in one transaction.
- **Push:** `SyncWorker` drains outbox → `upsert` batches to Supabase.
- **Pull:** `select * where updated_at > cursor order by updated_at` per table; apply to Room.
- **Conflicts:** last-writer-wins on `updated_at` (server stamps via trigger). Fine for personal data.
- **Triggers:** on every write (expedited one-time work), plus periodic 15-min WorkManager, plus on app foreground.
- **Media:** compress photos to WebP ~300 KB, voice notes to Opus ~16 kbps (≈120 KB/min). 1 GB free storage ≈ years of journaling. Media lives on Drive (§6c), so Supabase's 1 GB only holds thumbnails.
- **Not worth it:** Realtime subscriptions, CRDTs, PowerSync/Firebase. Re-evaluate only if you add multi-user.

---

## 6. APIs and what each is used for

| Need | API | Cost | Notes |
|---|---|---|---|
| Intent parsing ("set alarm for 6:30 and remind me to…") | **Gemini Nano** via ML Kit GenAI Prompt API (on-device, Pixel 9/10 and some others) | Free | Output a strict JSON schema. Check `checkFeatureStatus()` at runtime; Nano is the "in-house Pixel API" |
| Same, cloud fallback | **Gemini API Flash-Lite** (paid pay-as-you-go tier; Flash for harder tasks) | ~$0.10-0.25 in / $0.40-1.50 out per 1M tokens (varies by model version; confirm on Google's pricing page) | Called via Edge Function. Paid tier means prompts are not used for training. Roughly $2/month even at 100 calls/day |
| Brief script, decision phrasing | Nano first (app in foreground); templates otherwise; optional Gemini line | Free / cents | One call per morning |
| Speech → text | **ML Kit GenAI SpeechRecognizer** (on-device) | Free | Advanced mode on Pixel 10; Basic mode elsewhere (API 31+) |
| Speech → text fallback | **Gemini API audio understanding** (voice commands only, never journal) | Cents | Only if on-device recognition fails |
| Text → speech | Android `TextToSpeech` | Free | Gate by `spoken_replies` |
| Weather | **Open-Meteo** `/v1/forecast` | Free, no key | Cache 1 h |
| Calendar | `CalendarContract` | Free | Needs `READ_CALENDAR`; shows "10:30 call" in brief |
| Books (optional, later) | **Open Library** API | Free, no key | Goodreads' public API is closed. Skipped in the core build to keep providers minimal |
| Movies (optional, later) | Letterboxd CSV export import (no API key) | Free | Letterboxd's API is by-approval only; TMDB would add another account |
| Notion | Notion API (internal integration token) | Free | Optional, push journal/todos |
| Phone-usage signals ("phone in use until 1:40 am") | `UsageStatsManager` / screen on-off broadcasts | Free | User grants Usage Access; stays on device |
| Bank SMS → expense (optional) | `READ_SMS` + local regex | Free | Sideloaded app only (Play restricts SMS permission). On-device, never uploaded |
| Auth | Supabase Auth + Google Sign-In | Free | |

> **As built:** feature code calls one facade, `AiService` (`ai/`), and never an ML Kit or Gemini class. `AiRouter` walks an ordered provider list per capability and `AiPolicy` holds the rules below in one place (journal never to cloud, Nano only in the foreground, cloud only online with a key). Details, provider order and how to add a provider: `docs/AI.md`.

### AI routing rule (`AiRouter`)
```
if content is journal (text, photo, voice note) → on-device only: Nano / on-device speech / on-device embedder
                                                  if unavailable: skip the AI step, never cloud
else if Nano available and app is foreground  → Nano
else if network                               → Edge Function → provider chain (§6b)
else                                          → deterministic regex/rules + "I couldn't understand — type it?"
```
**Privacy rule:** journal content is processed by in-house (on-device) AI only. Other data (alarms, to-dos, reminders, expenses, habits, brief facts) may use the cloud chain; free tiers may use prompts to improve their products.

### Voice command intents (JSON schema the model must emit)
`set_alarm`, `change_alarm`, `add_todo[]` (with `category` guess → T5 auto-sort), `add_reminder`, `log_expense`, `log_habit`, `journal_note`, `query_next`, `undo_last`.
Execution is deterministic code; the LLM only parses. Each execution writes `voice_commands.undo_payload` so the "Undo" chip in screen 06 works.

### Decision engine (screen 07 / F3)
Rules fire first (e.g. `last_screen_on > 01:00 AND first_event_tomorrow >= 11:00 AND alarm < 08:00`), producing `reasons[]` + options. LLM only phrases the text. "You picked *shift* the last two times" comes from querying `decisions`. At most one card at a time; "Don't suggest this again" writes `suggestion_prefs`.

---

> **Decision (Oct 2026, final): journal content (text, photos, voice notes) is processed only by on-device AI, and the insights derived from it (summaries, tags, AI mood, transcripts, captions, embeddings, answers) stay on the device too.** None of it goes to the cloud gateway, Supabase, Drive backups or Notion. Other data may still use the cloud chain. No setting is needed; this is fixed behaviour. Consequence: journal AI features (captions, summaries, mood, semantic search, "Ask Cove") need Gemini Nano / on-device models, i.e. the Pixel 10. On a phone without Nano (e.g. Pixel 6a) they are simply off and the journal still works with plain full-text search. The design's S4 copy "Recordings are processed on this phone" is accurate for journal voice notes; for voice commands (alarms, to-dos) it needs rewording since the cloud fallback may be used.

## 6a. Gemini Nano constraints (verified Oct 2026; these change the design)

Sources: Android/ML Kit docs, Android developer blog, release notes, developer write-ups. I could not find Reddit threads through search, so there is no community experience reported here; treat the numbers as documented, not field-tested.

| Fact | Consequence for Cove |
|---|---|
| Prompt API accepts text and image + text; **Pixel 10 runs Nano v3** (Gemma 3n architecture). Pixel 9 and some others only get the weaker Nano v2 | Photo captions and intent parsing are feasible on your phone |
| Prompt API is still **beta** (1.0.0-beta2, Apr 2026), not stable | Wrap it behind `NanoClient`; expect API changes; pin the version |
| **Inference only runs while your app is the top foreground app.** Do not run it in widgets, background jobs or foreground services | **The 6:15 am `BriefWorker` cannot use Nano.** See changes below |
| Per-app quota: `BUSY` and `PER_APP_BATTERY_USE_QUOTA_EXCEEDED` errors; retry with backoff, never loop | `AiRouter` treats these as "fall to the next tier", no retries beyond one backoff |
| Documented limits around 4,000 input / 255 output tokens; short JSON outputs ≈ 1–2 s, long outputs several seconds | Keep prompts small; the brief is built from facts + short generated lines, not one long generation |
| Nano has **no embedding API** | Embeddings need a separate model (below) |

**Design changes made because of this**
1. **Do all Nano work at capture time, in the foreground.** When you save a journal entry or voice note, the app captions the photo, summarises, tags and embeds it then. `MediaUploadWorker` and `SearchIndexer` never call Nano in the background; they only move files and upload already-computed text.
2. **Morning brief:** `BriefWorker` assembles facts (weather, calendar, tasks, spend) and builds the script from templates, with an optional LLM-written intro from the cloud gateway. Journal content is never included. A richer Nano-written brief can be pre-generated in the evening while the app is open (F1 wind-down screen), then spoken in the morning.
3. **Voice widget** only launches the Activity into listening mode; inference happens after it is in the foreground.
4. **Embeddings:** use **EmbeddingGemma (308M, LiteRT, 768-dim)** on-device. It works offline but needs a manually bundled/downloaded model file (a few hundred MB), so schedule it as a **spike in phase 5**. MediaPipe's older Text Embedder is a lighter fallback with different vectors, so re-index if you switch. If neither is good enough, Postgres full-text search alone is the fallback and nothing else changes.
5. **Pre-phone testing:** use Gemma 3n locally to prototype prompts (same architecture as Nano v3). The ML Kit Prompt API itself needs a supported device or AICore, so verify on the Pixel 10.

---

## 6b. Providers and AI gateway (final: Google + Supabase)

Compared earlier: Firebase, Turso, Neon, Cloudflare, Groq, Cerebras, OpenRouter, Deepgram, AssemblyAI. All are dropped to keep to two accounts. Free LLM tiers also change often (Google already moved its Pro models out of the free Gemini tier in 2026) and may train on prompts.

| Concern | Provider | Plan |
|---|---|---|
| LLM + cloud speech fallback | **Google, Gemini API** | **Paid pay-as-you-go (billing on), Flash-Lite default, Flash for harder tasks.** Not used for training on the paid tier |
| Media + backups | **Google Drive** | Free 15 GB (Google One if ever needed) |
| Sign-in | **Google Sign-In** | Free |
| Database, sync, thumbnails, Edge Functions | **Supabase** | **Free plan.** Pro ($25/mo) only if you hit limits. Weekly ping prevents idle pause |
| Weather | Open-Meteo | No account, no key |

**Why this is the long-term pick:** fewer terms and dashboards to track, cost is about $2/month in the worst realistic case, and your data is also on the phone and in Drive, so either provider can be replaced without data loss.

### `ai-gateway` Edge Function (thin)

```
request {task: intent|brief|stt, payload}   # never journal content
  ▼
call Gemini Flash-Lite (4 s timeout) → validate JSON against schema
  on 429/5xx/timeout or invalid JSON → one retry on Gemini Flash
  still failing → {error:"unavailable"} → client falls back to rules / "type it?"
```
- One normalised request/response shape (`{intent, confidence}`) so the provider can be swapped later if needed. Keep the model name in a `config` row so you can change it without shipping an app update.
- The Gemini key is an Edge Function secret. The APK holds none.
- **Spend control (required):** Gemini billing has no hard cap by default. In Google Cloud Console set a budget alert (e.g. $5/month) and a per-day quota limit on the Gemini API. The gateway also rejects requests from any user id other than yours.
- Journal text, photos and voice notes never enter this gateway (§6a decision).

---

## 6c. Media storage: Google Drive as the only blob store, with on-device compression

**Decision:** Drive is the single place photos and voice notes live. No second blob store, no hot/cold tiering. Supabase keeps rows and thumbnails only (journal insights stay on the phone, §6d).

| Blob storage option | Free tier | Verdict |
|---|---|---|
| **Google Drive** (`drive.file`, visible folder "Cove") | 15 GB shared with your Google account; scope is non-sensitive, no app verification | **Chosen.** Largest free space, you own and can browse the files, no extra account |
| Supabase Storage | 1 GB | Only for ~15 KB thumbnails |
| Google Photos API | App-uploaded items only | Optional later (§6e) |

**Capacity:** ~350 KB per photo x 5 photos a day ≈ 640 MB a year, so 15 GB lasts well over a decade. Voice notes (Opus ~16 kbps ≈ 120 KB/min) are negligible.

### Compression pipeline (on the phone, before upload)
Goal: visually close to the original on a phone screen at 5-10x smaller.

1. **Compress once, from the original.** Never recompress an already-compressed file.
2. **Resize** so the long edge is at most **2048 px** (never upscale; skip resize if already smaller).
3. **Encode lossy WebP, quality ~80** (`Bitmap.CompressFormat.WEBP_LOSSY`, API 30+). WebP is typically 25-35% smaller than JPEG at the same visual quality and decodes everywhere. AVIF/HEIC would be smaller still, but Android has no simple built-in encoder, so skip them.
4. **Skip compression** if the file is already under ~500 KB.
5. **Screenshots / text-heavy images:** encode lossless WebP so text stays sharp (detect by low colour count or ask the picker source).
6. **Metadata:** apply EXIF rotation, keep capture date, **strip GPS** by default (setting to keep it).
7. **Thumbnail:** 320 px WebP quality 70 (~10-20 KB), stored locally and in Supabase Storage so the journal calendar and lists load without touching Drive.
8. **Decode safely:** use `ImageDecoder` with a target size so a 50 MP photo never loads at full size.
9. **Keep the original** in the phone's own gallery; Cove stores only the compressed copy.
10. **Tuning:** the 2048 px / quality 80 numbers are starting points. Before shipping, compare a dozen of your own photos (faces, night shots, text) at 75/80/85 and pick the lowest quality that you cannot tell apart. Expose a "Photo quality: Balanced / High" setting (80 / 90).

### Flow
- Save entry → compress → write to app storage → row inserted (`upload_state = pending`) → `MediaUploadWorker` (Wi-Fi preferred) uploads to Drive via the REST API using Google Sign-In (Credential Manager + `AuthorizationClient`) → stores `drive_file_id`, `upload_state = done`.
- Other devices or a fresh install pull the thumbnail first and fetch the full file from Drive on tap, then cache it.
- If Drive is unreachable the file stays local and retries; nothing blocks the journal.
- **Backups (Drive):** monthly `cove-YYYY-MM.json.gz` of all tables plus journal entries as Markdown. Keep 12. Readable without the app.
- Optional: encrypt media with the Keystore-wrapped key before upload (loses Drive previews, off by default).
- OAuth: Android OAuth client in Google Cloud Console, consent screen set to *In production* for your own account (avoids the 7-day token expiry of *Testing* mode).
- Files: `data/media/{ImageCompressor.kt,ThumbnailMaker.kt,MediaUploadWorker.kt,MediaStore.kt}`, `data/remote/DriveClient.kt`, `backup/MonthlyBackupWorker.kt`. The earlier `ArchiveWorker` is replaced by these.

---

## 6d. Supabase as cache, and querying archived data

**Supabase as the cache layer: yes, for rows and thumbnails (not journal insights).** Read path is phone (Room) → Supabase. Writes go to Room first, then Supabase. Full-size media lives only on Drive (§6c) and is fetched on tap.

**Querying Drive "intelligently": there is no good free Google API for it.**
- Drive `files.list` search only matches file names and, for text/PDF/Google Docs, content. It won't understand your `.json.gz` backups, voice notes or WebP photos.
- The Gemini API cannot search your Drive. Its Files API takes uploads (kept ~48 h), not Drive folders.
- Vertex AI Search with a Drive connector and Gemini-in-Drive exist, but need Cloud billing / Workspace and are not a free, programmable path for a personal app.

**Do the preprocessing yourself, once, at capture time, and keep the result ON THE PHONE ONLY.** Journal insights never leave the device: not to cloud AI, not to Supabase, not to Drive.

| At capture time (on-device, foreground, free) | Stored in Room only (`search_index`, not synced) |
|---|---|
| Voice note → transcript (ML Kit speech) | `transcript` |
| Photo → short caption + tags (Nano multimodal prompt; skipped if unsupported) | `caption`, `tags` |
| Entry text + transcript + caption → embedding (on-device EmbeddingGemma / MediaPipe) | `embedding` (BLOB, 768 floats, ~3 KB) |
| Entry → one-line summary + inferred mood (Nano) | `summary`, `ai_mood` |

```sql
-- Room (SQLite) only. No Supabase table, excluded from sync, backups and exports.
search_index(entry_id pk, summary, ai_mood, transcript, caption, tags, embedding blob, updated_at)
search_fts  -- SQLite FTS5 virtual table over summary/transcript/caption/tags + entry body
```

**Query flow ("when did I last feel low after a bad night?")** runs entirely on the phone:
1. Embed the question on-device.
2. FTS5 keyword search + cosine similarity over the stored embeddings (brute force in Kotlin is fine up to ~100k entries, a few ms per thousand) → top ~8 entries.
3. Nano writes the answer from those snippets (app in foreground). The raw photo or audio is fetched from Drive only when you tap it.
- Use one embedding model for the whole index. If you change models, re-index.
- Money, habits and to-dos need no AI search; plain SQL aggregates answer them.

**Consequences of keeping insights on the device**
- **New phone / reinstall:** the raw entries, photos and voice notes restore from Supabase and Drive, but insights do not. The app re-runs on-device indexing in the foreground (progress shown, can take a while for years of entries). Full-text search over entry bodies works immediately.
- **Which journal fields still sync:** the entry text, the mood chip *you* pick, photos and voice notes (your own Supabase and Drive). Derived data (`summary`, `ai_mood`, `transcript`, `caption`, `tags`, `embedding`) does not. A voice note's transcript is derived, so it is regenerated locally.
- **Nothing derived from journal content may be used elsewhere:** not in the morning brief, not in widgets' cloud calls, not in the Notion export, not in the `ai-gateway`. Habit and money numbers are unaffected.
- **Supabase size:** the search index no longer lives there, so the free 500 MB database is used even more lightly.

New files: `data/search/{Embedder.kt,SearchIndexer.kt,SearchRepo.kt}`, `feature/ask/AskScreen.kt` (optional, later phase). Build the indexer in phase 5 and the query UI after phase 8.

### Google API review (checked Oct 2026) and verdict

| Google API | What it offers | Verdict |
|---|---|---|
| **Drive API** (`drive.file`, `drive.appdata`) | File storage in your Drive; both scopes are non-sensitive (no app verification) | **Use** for archive and backups (§6c) |
| **Gemini Nano via ML Kit GenAI Prompt API** | On-device text + image-and-text prompts; works best on Pixel 10 (Nano v3, Gemma 3n-based); Prompt API was alpha at launch, so check its status when coding | **Use** as primary AI, including photo captions |
| **ML Kit GenAI SpeechRecognizer** | On-device transcription, Advanced mode on Pixel 10 | **Use** |
| **Gemini API Flash / Flash-Lite** | Free cloud LLM | **Use** as cloud fallback (§6b) |
| **`gemini-embedding-001`** | Free tier ≈ 100 req/min, 1,000 req/day, 30k tokens/min; 768-dim output available | **Not for journal.** Optional only for non-journal text (to-dos, reminders). Never mix its vectors with on-device vectors in one column |
| **Gemini File Search tool** | Managed RAG: storage and query-time embeddings free, 1 GB free store, indexing $0.15 per 1M tokens | **Rejected** for journal: 1 GB cap, content would leave the device, and local search already does the job |
| **Drive search (`files.list`)** | Name search; content search only for text/PDF/Docs | **Use only** to locate archive files by name |
| **Photos Library API** | Only app-uploaded items (scopes `appendonly`, `readonly.appcreateddata`, `edit.appcreateddata`); Picker API for choosing photos | **Optional** (§6e); Drive stays default |
| **Calendar API** | Full calendar read/write | **Skip**: `CalendarContract` on the phone already has your synced events with no OAuth |
| **Tasks API** | Sync with Google Tasks | **Skip**: duplicate of Cove's to-dos; adds OAuth scope for little gain |
| **Keep API** | Workspace enterprise only | **Not available** |
| **Vertex AI Search / Gemini-in-Drive** | Natural-language search over Drive | **Rejected**: needs billing/Workspace, not free |

**Chosen approach:** Room (phone: data + journal insights) → Supabase (rows, thumbnails) → Drive (raw media and monthly backups). On-device Nano and ML Kit do all journal preprocessing; the cloud chain handles only non-journal tasks.

---

## 6e. Google Photos and Notion: optional, not the source of truth

**Google Photos for journal photos: possible, but Drive stays the default.**
- Since 2025 the Photos Library API only lets an app read and manage items **it uploaded itself** (scopes `appendonly` + `readonly.appcreateddata`). That is enough for journal photos, so my earlier "rejected" was too strong.
- Why it still loses: photos you pick are usually already in your Photos library, so uploading creates a duplicate (the API can't link to existing items). Image URLs expire after about an hour and must be refetched. The app can't delete what it uploaded. Uploads count against the same 15 GB Google quota as Drive. Journal photos would also mix into your main gallery.
- Upside: they show up in the Photos app and its search.
- Optional switch: a `photo_store` setting (`drive` default, `photos`) and an `upload to Photos album "Cove"` path in `MediaUploadWorker`. Build it only if you actually want them in the gallery. Check the current scope verification rules in Google Cloud Console first; I haven't verified them.
- Voice notes and backups stay on Drive regardless.

**Notion ("noted" — I'm assuming you mean Notion; tell me if you meant something else): good as a mirror, poor as the database.**
- Free API, free personal plan, file uploads supported (small per-file limit on the free plan).
- Poor as primary storage: about 3 requests/second, no vector or hybrid search, awkward for money and habit queries, and sync would be one-way complexity with slow pagination.
- Use it as an optional **one-way export**: `NotionClient` writes each journal entry (and finished to-dos) as a Notion page, so you can read and share them in Notion. Supabase remains the source of truth; if Notion is down, nothing breaks. This is the Integrations row already in the Me screen.
- Google Keep's API is Workspace-enterprise only, so it is not an option.

---

## 7. Biometric login

1. First launch: Google sign-in via Supabase (once).
2. Generate an AES key in **Android Keystore** with `setUserAuthenticationRequired(true)` (biometric-strong, per-use or 30 s validity).
3. The key wraps the **SQLCipher passphrase** for Room and the Supabase refresh token.
4. On each cold start / after N minutes in background: `BiometricPrompt` with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` and a `CryptoObject`. Success unlocks the key → opens DB.
5. `FLAG_SECURE` on the window (hides content in recents). Optional toggle.
Library: `androidx.biometric`. Free.

Result: even if the phone is unlocked and someone opens Cove, data stays encrypted until the biometric passes.

---

## 8. Android widgets (Glance)

| Widget | Size | Content | Tap |
|---|---|---|---|
| **Next thing** | 4×2 | Single next item + time (mirrors Today card) | Opens Today |
| **Voice** | 2×2 | Orb | Launches `VoiceSheet` directly in listening state (via trampoline Activity) |
| **Habits** | 4×1 | Water / Walk / Read dots; tap toggles today | Toggles log, syncs |
| **Spent today** | 2×1 | ₹ today, budget ring | Opens Money |

Updates: `WidgetUpdater` called after any repo write + WorkManager every 30 min. Respect `DayPhase` (evening widget shows "Start winding down"). Also add a **Quick Settings tile** for voice — cheap and very useful.

Permissions list: `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `READ_CALENDAR`, `PACKAGE_USAGE_STATS` (user-granted), `USE_BIOMETRIC`, optional `READ_SMS`.

**Alarm reliability (the one thing that must not fail):** `AlarmManager.setAlarmClock()`, re-register in `BootReceiver`, full-screen `AlarmRingActivity`, foreground service while ringing, ask the user to exempt battery optimisation. Test on your actual Pixel with the phone idle overnight.

---

## 9. Hosting and cost

| Service | Plan | Monthly | Limit that matters |
|---|---|---|---|
| Supabase | Free | ₹0 | 500 MB DB, 1 GB files, pauses after 7 idle days |
| Gemini API | Paid pay-as-you-go, Flash-Lite | ~$0-2 | Set budget alert + quota cap (no hard cap by default) |
| Google Drive | Free 15 GB | ₹0 | Google One if you exceed it |
| Open-Meteo | Free, no account | ₹0 | |
| Gemini Nano, ML Kit STT, TTS | On-device | ₹0 | Device support |
| Distribution | Sideload / Firebase App Distribution | ₹0 | Play internal testing = one-time $25 |
| **Total** | | **≈ ₹0-170 (about $0-2)** | |

Fallbacks: Supabase Pro $25/mo if the free plan is outgrown (unlikely at one user); the AI gateway can point at another LLM provider if Gemini pricing or terms change, since the request shape is provider-neutral.

---

## 10. Build phases (each ends with something you can run)

Status as built (all phases are merged; items marked "partial" are described in the notes column).

| # | Phase | Status | Notes |
|---|---|---|---|
| 0 | Skeleton, theme, VoiceOrb, cards, dock, static Today | done | Compared with `design/ref` using `tools/compare.py` |
| 1 | Room schema, repos, To-dos + Plan + Habits | done | Offline; swipe/undo works |
| 2 | Alarms (AlarmManager, boot, ring screen) | done | `setAlarmClock`; re-registered after boot/time change |
| 3 | Biometric lock + SQLCipher | done | DB encrypted with a Keystore-wrapped key; plaintext upgrade runs off the main thread (`docs/SECURITY.md`) |
| 4 | Voice: STT, intent, executor, TTS, Undo | done | Typed fallback; rule-based parser with Nano/cloud hooks |
| 5 | Money + Journal (text, photo, voice note) | done | Journal search is FTS4 |
| 6 | Supabase: migrations, RLS, auth, sync | done | Ktor client, outbox + last-write-wins with a conflict screen |
| 7 | `ai-gateway` Edge Function + Gemini | done | `supabase/functions/ai-gateway` |
| 8 | Brief generator/player, Open-Meteo, calendar | done | Calendar needs READ_CALENDAR (asked from Me); city set in Me |
| 9 | Decision engine, Why-this, nudges, calm settings | done | |
| 10 | Glance widgets + QS tile | done | Four widgets (Next, Spent, Tasks, Voice) |
| 11 | Integrations (books/movies/Notion), SMS parser | not built | Optional; out of scope for the first release |
| R | Release hardening | done | R8 + resource shrinking, signing config, startup off the main thread, lint clean (`docs/RELEASE.md`) |

### Decisions that changed during the build
- **Manual DI (`AppContainer`) instead of Hilt**: one module, one user; less build time and no annotation processing beyond Room.
- **Ktor client instead of supabase-kt**: a handful of REST/GoTrue calls; smaller APK, easy to test with `MockEngine`.
- **FTS4 journal search** (Room `@Fts4`) instead of the embeddings idea; `NoOpEmbedder` keeps the hook.
- **Widgets via Glance**, not RemoteViews, with `widget_preview_*` layouts only as picker previews.
- **Google Drive as the media store** (see §6c); Supabase keeps only thumbnails.
- **Portrait only**: the layouts are designed at 390 dp wide; `MainActivity` is locked to portrait.

---

## 11. Risks and open questions

1. **Target device: Pixel 10 (confirmed).** It runs Gemini Nano v3 and the ML Kit "Advanced" speech mode, so on-device AI and speech are the primary path for foreground work and the cloud chain is the backup. Nano cannot run in background jobs (§6a), the Prompt API is still beta, and embeddings need a separate model. Until the phone arrives, develop on the Android emulator (no Nano there, so the cloud chain and rules path get exercised first) and verify Nano/speech/alarm-overnight behaviour on the real device in phases 2, 4 and 8.
2. **Android version** — Pixel 10 ships with a current Android release; minSdk stays 31 so the app also runs on other devices.
3. **Single AI provider** — Gemini pricing, model names or terms may change (Google retires models on a schedule); keep the call behind `AiRouter`/`ai-gateway` and the model name in config.
4. **Journal AI and its insights are on-device only (final).** Depends on Pixel 10 / Nano; off on other phones. Insights are rebuilt locally after a reinstall or new phone.
5. **Bank SMS parsing** — useful for ₹ tracking but permission-sensitive; kept optional.
6. **Design gaps to add before build:** lock screen, sync status/error state, permission rationale screens, alarm ring screen, empty states for Money/Journal/Habits, widget visuals. None exist in the zip.

Sources checked: Gemini API pricing write-ups (morphllm.com, geotoolbox.ai, agentdeals.dev), Supabase free-plan limits (jetadmin.io, makerkit.dev), Android ML Kit GenAI overview (developers.google.com/ml-kit/genai), Google Drive scopes (developers.google.com/workspace/drive/api/guides/api-specific-auth), Photos Library API changes (developers.googleblog.com).


## Training (added)
Deliberately small: today's exercises (also a card on Today), my weight, a Mon-Sun plan, a rules-first weight suggestion and a progress screen; planned by voice too. No programme, rest timer or session flow. See `docs/TRAINING.md`. Room 8, Supabase `0006_training_simple.sql`.
