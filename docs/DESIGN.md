# Cove design: "calm garden"

Warm paper, forest ink, leaf green, and a small set of sunny highlight hues. Simple, calm, human. This replaces the cool grey/blue scheme of `design/ref*` (see the note in `FIDELITY.md`). Tokens live in `design/Color.kt`; never write a colour literal in feature code (`ContrastTest` fails the build if you do).

## Palette (light / dark)
| Token | Light | Dark | Use |
|---|---|---|---|
| canvas | `#F6F1E6` | `#121A14` | screen background (with a faint paper grain) |
| card | `#FFFCF5` | `#1B261E` | neutral grouped surfaces |
| ink / onInk | `#1F2B22` / `#F6F1E6` | `#EEE9D8` / `#121A14` | text; primary button fill + its text |
| muted | `#55614F` | `#A5B09E` | secondary text |
| tail / placeholder | `#7D8874` / `#858F7F` | `#7A8674` / `#8A9684` | continuation text, hints |
| well / wellStrong | `#ECE6D6` / `#E3DCC8` | `#25322A` / `#2D3B32` | quiet fills, tracks, segmented track |
| hairline / quiet | `#E9E3D2` / `#D9D4BF` | `#26332A` / `#3A4A3F` | dividers / decorative pale marks |
| ring, switchOff | `#7C8776` | `#66745F` | empty checkbox outline, off switch (3:1 on card and canvas) |
| accent / onAccent | `#2F6B45` / `#FFFCF5` | `#9ED08F` / `#121A14` | leaf green: active tab, links, selected chips and days, switches on, checkboxes, progress |
| accentSoft | `#E1EEDA` | `#243A2A` | soft green button fill, active nav disc |
| alert, saved | `#A34824`, `#336E4E` | `#E08A64`, `#6DB38E` | problems, confirmations |
| shadow | `#3A3420` at 12% | black at 40% | warm brown-olive elevation |

Highlight hues (`CoveColors.hues`, `Hue(strong, tint)`; tint is a quiet surface that ink text stays readable on, strong is for dots, lines and bars):

| Hue | Light strong / tint | Dark strong / tint |
|---|---|---|
| sun | `#F2C14E` / `#FBEBC2` | `#D9AE4A` / `#3A3420` |
| coral | `#EE8B60` / `#FBE0D2` | `#E0866A` / `#3D2B24` |
| lilac | `#B9A8E6` / `#E9E3F8` | `#A99BDB` / `#2E2A40` |
| blossom | `#E98FB0` / `#FADCE7` | `#D98AA8` / `#3B2630` |
| leaf | `#7DB36B` / `#E1EEDA` | `#86BA76` / `#24392A` |
| sky | `#9CCFE0` / `#DDEFF5` | `#7DB5C8` / `#20343B` |

Orb gradient (`OrbColors`): peach, sun, leaf, lilac over a cream base; used by the dock orb, voice wash, ring glow, onboarding blob and widgets.

## Usage rules
- Tint main surfaces, not everything: Today Next card = sun (wind-down = lilac), workout = leaf, Money overview = coral, Journal calendar = lilac, Training weight = sun, Schedule events = sun, habit rows and Plan categories = `hueFor(id)`. Use `CoveCard(color = hue.tint)`; rows inside a tinted card read `LocalSurface` (swipe rows).
- `hueFor(key)` is deterministic (FNV hash over leaf, sun, coral, lilac, blossom); sky is only used on purpose. `moodHue(mood)`: calm leaf, good sun, tired lilac, low coral.
- Selected/active/on = accent with `onAccent`. Primary buttons stay ink-green with cream text.
- Charts: lines and bars in accent, soft sun area under lines, coral/paper behind money bars.
- Text on a tint is ink (7:1) or muted (4.5:1); never a hue's `strong` colour as text.

## Contrast (WCAG 2.x, checked by `ContrastTest` for light and dark)
Ink on canvas/card/well >= 7:1 (13.1 light, 14.6 dark); muted on every surface and tint >= 4.5:1 (5.8 on canvas); accent text on card, canvas, well, accentSoft and every tint >= 4.5:1 (6.2 on card); onAccent on accent 6.2; alert and saved >= 4.5:1 on canvas, card and well; ring and switchOff >= 3:1 on card and canvas (3.7); dark hue strong marks >= 3:1 on card. Values were nudged from the first draft (muted, alert, saved, placeholder, ring) to pass.

## Type
- UI text, labels and every figure: Geist (tabular figures), unchanged scale.
- Big titles only (`CoveType.Title`: greeting headline, screen titles, voice headlines, onboarding questions, journal entry title; also empty-state headings): Fraunces variable (OFL, `res/font/fraunces.ttf`), `SOFT=100`, `WONK=0`, `opsz=48`, weight 500, 32/38sp with -0.5 letter spacing. `LineBox`/`BalancedText` work unchanged: Fraunces' natural line (about 1.23 em) is larger than 38/32, so the trim logic behaves as for Geist.
- Money amounts, times and workout figures stay Geist.

## Motion
- Bloom on completing a to-do or habit: scale 1.0 to 1.08 to 1.0 plus a faint ring, 240 ms (`Modifier.bloom`).
- Rows ease in on first appearance: fade plus 8 dp rise, 180 ms, never replayed on scroll (`Modifier.easeIn`).
- Both are skipped under reduce motion. Button press feedback and haptics are unchanged.

## Paper
A 128 px grey noise tile is generated once and overlaid at 7% (9% dark) behind every `CoveScreen` (`PaperGrain.kt`, switch: `PaperGrainEnabled`). It is static, so it costs nothing per frame.
