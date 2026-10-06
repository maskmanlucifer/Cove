# Illustrations and empty states

Everything is drawn in code (`design/illustrations/`, no image assets). Review all 19 scenes, light and dark, at `--es route debug/illustrations` (debug builds).

## Scenes
Time of day (wide, own sky): Morning, Afternoon, Evening, Night. Spot scenes: Todos, Schedule, Money, Journal, Habits, Alarms, Training, Voice, Messages, Synced, Offline, Help (lifebuoy), Lantern, Secure, Cleared.

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
| Lock screen | Secure | replaces the orb glyph |
| Mic and alarm permission guides | Voice, Alarms | fills the free space (skipped when under 96 dp) |
| Clear data, cloud done | Cleared | 110 dp art |

## Not changed
Welcome (already has its own gradient blob), Journal "day with no entry" (tapping an empty day opens a new entry, so there is no page to fill), Brief generating/offline states (the player has no separate empty page).

## Verification
Checked at 1080x2400 / 420 dpi in light and dark, and at 2.0 font scale. Today p99 frame time on the emulator: 20-23 ms with the banner versus 19-23 ms before; an animated empty Today ran 726 frames with 0 janky. 1000-event monkey run clean, `adb logcat -b crash` empty.
