# Illustrations and empty states (system v2)

Everything is drawn in code (`design/illustrations/`, no image assets, no dependencies). Review the 14 mascot poses and all 22 scenes, light and dark, plus the 64/160 dp Today strips, at `--es route debug/illustrations` (debug builds).

## Look
Painterly-flat botanical scenes (layered rolling hills, seeded brush strokes and speckles, paper grain, flowers in orange, sun yellow, lilac, blossom pink and white three-petal with golden centres) with one recurring character: **Cove's companion**, a sage-clay pebble with two oval eyes, a leaf-sprig antenna and stubby arms. Expression is eyes only (size, tilt, gaze, closed or happy lids); no mouth.

* Palette: `IllusPalette` (own file, light and dark). Illustrations never read theme tokens, so re-theming cannot break them.
* Poses (`MascotPose`): Idle, Waving, Sleeping, Reading, Coin, Tending, Listening, Thinking, Celebrating, Blanket, Lantern, Lifting, Walking, Stargazing.
* Scenes: time of day (wide): Morning, Afternoon, Evening, Night; Welcome (wide, tall). Round spot scenes: Todos, Schedule, Money, Journal, Habits, Alarms, Training, Voice, Messages, Synced, Offline, Help, Lantern, Secure, Cleared, Celebration, Lock.
* Small spots: `Mascot(pose)` and `MascotHead()` composables.

## Performance model
A scene is painted once per (scene, pixel size, theme, mascot variant) on `Dispatchers.Default` into an `ImageBitmap` held in a 24 MB LRU (`ArtCache`); drawing is one `drawImage`. Only the mascot's eyes and antenna are drawn live in a small second canvas (blink about every 3.5 s, leaf sway on a 7 s loop). Motion stops under reduce-motion, when the screen is not resumed, or with `animate = false`. Everything is hidden from semantics and non-interactive.

## Where they are used
| Screen / state | Scene | How |
|---|---|---|
| Today, nothing planned | time of day | `EmptyState` with "Say it" (opens Voice) |
| Today, little content (<= 5 weight) | time of day | 160 dp banner under the list |
| Today, moderate (6-8) | time of day | 64 dp strip |
| Today, full / offline | none (offline: Offline spot art) | hidden |
| Plan schedule, empty day | Schedule | `EmptyState` + "Add to schedule" |
| Plan to-dos, no categories | Todos | `EmptyState` + "Say it" |
| Plan to-dos, empty category / all empty | Todos | art above the existing "Tell me what you need" prompt |
| Money, no spending | Money | compact `EmptyState` |
| Money categories, none | Money | compact `EmptyState` |
| Category detail, no transactions | Money | compact `EmptyState` |
| Money import: intro, scanning | Messages | 120 dp art (also covers permission denied, which reuses the intro) |
| Money import: done | Synced (added) / Cleared (nothing found) | 120 dp art |
| Money review, all filed | Cleared | `EmptyState` |
| Journal month, no entries | Journal | `EmptyState` |
| Habits, none | Habits | `EmptyState` |
| Alarms, none | Alarms | `EmptyState` |
| Training: nothing planned today, plan day, empty progress | Training | art / compact `EmptyState` |
| Brief | Voice | fills the dead space above the scrubber |
| Connect services, nothing connected | Synced | 120 dp art |
| Sync conflict | Synced | on the canvas above the sheet |
| Recovery | Lantern (key/db problems) or Help (others) | 132 dp art, text kept |
| Lock screen | Lock | replaces the orb glyph |
| Welcome (onboarding step 0) | Welcome | top 60% of the page, signature mascot-in-meadow |
| Mic and alarm permission guides | Voice, Alarms | fills the free space (skipped when under 96 dp) |
| Clear data, cloud done | Cleared | 110 dp art |

## Not changed
Journal "day with no entry" (tapping an empty day opens a new entry, so there is no page to fill), Brief generating/offline states (the player has no separate empty page). `Secure` and `Celebration` are in the library but not placed yet.

## Verification
Checked at 1080x2400 / 420 dpi in light and dark. Today (empty, banner up, mascot idling) on the emulator: 727 frames, 0 janky, p50 17 ms, p99 20 ms (v1: 726 frames, 0 janky, p99 19-23 ms). Unit tests: `IllustrationHelpersTest` (seeded strokes, fit maths, cache key and LRU, blink and sway ranges) and `TimeOfDayTest`.
