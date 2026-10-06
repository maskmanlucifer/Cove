# Cove: working agreement for building features

Cove is a calm, voice-first Android app (Kotlin, Jetpack Compose, Room, manual DI). `PLAN.md` is the product/architecture plan; this file is how to build in the repo. Design source of truth: `design/ref/*.png` (390x844 previews), `design/ref3x/*.png` (1170x2532, the size of our emulator), `design/frames/*.html` (exact CSS per screen; read these for sizes, colours, spacing, copy).

## Name and copy
The app is **Cove**. The design files say "Still": use "Cove" wherever copy mentions the app name. Copy is otherwise taken verbatim from the frames.

## Code style
- Kotlin official style, 4 spaces, no wildcard imports, no unused imports. Keep files focused (aim < 300 lines); split composables into private functions.
- Documentation: short KDoc (`/** ... */`) on every public class, function and non-obvious property: one line saying what/why, `@param`/`@return` only when not obvious. No comments that restate code. No TODO placeholders for requested behaviour: implement it.
- Match the surrounding idiom: `Screen` composable + `ViewModel` built with `appViewModel { XViewModel(it) }`, state as a single immutable data class exposed as `StateFlow`, repositories injected via `AppContainer`.
- No new dependencies unless the feature truly needs them (add to `gradle/libs.versions.toml`; pin versions compatible with AGP 8.13 / compileSdk 36).
- Strings shown to users live in code next to the screen (the app is English only); the app name comes from `R.string.app_name`.

## Design system (already built, reuse it)
`app/src/main/kotlin/app/cove/companion/design/`:
- `Cove.colors` (light/dark tokens), `CoveType` (Geist scale), `CoveShapes`, `CoveIcons` (+ `CoveIcon`), `CoveTheme`.
- `components/`: `CoveText` (tabular figures; overload with muted trailing part), `BalancedText` (headline wrapping like CSS `text-wrap: balance`), `PillButton`, `CheckCircle`, `CoveSwitch`, `Segmented`, `Chip`, `CoveCard`, `ValueRow`, `Hairline`, `CoveSheet` (floating bottom sheet), `CoveDock`/`VoiceOrb`, `coveTopInset()`, `DockClearance`, `pressable()`.
- Always draw text with `CoveText`/`BalancedText`: they trim Compose's extra leading so line boxes equal the design's CSS line boxes. If you need a new shared component or icon, add it in a NEW file under `design/components/` (or a new object next to `CoveIcons`) rather than rewriting existing ones. Icons are SVG paths copied from the frames (see `Icons.kt` helpers).
- Dark mode: every screen must work in both themes using `Cove.colors` tokens only (no hard-coded colours except the voice orb gradient). The designs show dark for only some frames; derive the rest with the same token mapping.
- Spacing: 390dp wide design. Content starts at `coveTopInset()` (48dp or status bar); screens with the dock leave `DockClearance` at the bottom.

## Navigation and data
- Routes and `Nav` are in `navigation/`; every route already points at a stub screen in `feature/<name>/`. Replace the stub's body, keep its signature. Add new routes in `Routes.kt` + `CoveNavHost.kt` only if you must (one-line additions).
- Entities, DAOs and repositories are in `data/`. Add queries/repo methods you need; keep schema changes additive and bump `CoveDatabase` version (run a build so `app/schemas/` is regenerated and commit it). Every repo write goes through `ChangeLog.mark` so sync can pick it up.
- Time always comes from `AppContainer.clock` (never `System.currentTimeMillis()`/`LocalDate.now()` in logic) so screens can be frozen for comparison.
- Money is stored in paise (`Long`); format with `core/Format.kt` (`rupees`, `clockText`, ...).

## Verify against the design (required)
Each screen must be compared with its reference before you finish.
1. An emulator sized 390x844dp is provided to you (serial in your task). Export it: `export ANDROID_SERIAL=emulator-XXXX`.
2. `tools/run.sh --seed --now 10:35 [--dark] [--evening]` builds, installs, launches with sample data (see `data/DebugSeed.kt`; extend it with the data your frames show, in a `suspend fun seed<Feature>()` called from `load`). `CLEAR=1` wipes app data first (needed to re-seed).
3. Navigate to the screen (adb `input tap/swipe`, `am start` extras, or a debug extra you add in `MainActivity.handleDebugIntent`: e.g. `--es route money/categories`; keep debug helpers inside `BuildConfig.DEBUG`).
4. `tools/shot.sh /tmp/x.png && python3 tools/compare.py <NN_Name> /tmp/x.png`: prints a mean difference and writes a side-by-side (design | device | diff). Open the PNG and look at it. Today (`01_Today`) scores about 2 and shows only anti-aliasing noise; aim for the same. Differences in the status-bar band, the real clock, or data you cannot know (for example route times) are fine; layout, spacing, type, colour and copy must match.
5. `uiautomator dump` gives exact bounds of text nodes if you need to measure.

## Quality bar
- A screen is done when: it matches its frame in light (and dark where a frame exists), its controls actually work (persist to Room, navigate, undo where the design shows undo), empty states exist, and it survives rotation/process death without crashing.
- `./gradlew :app:compileDebugKotlin` must be clean. Add focused unit tests for pure logic you write (formatting, grouping, scheduling math) under `app/src/test/kotlin`; run `./gradlew :app:testDebugUnitTest`.
- Do not leave debug logging. Do not commit build output.

## Git
You work in your own git worktree on your own branch. Commit with clear messages (`feat(plan): ...`) as you go and when finished. Do not touch other branches. Shared files (`DebugSeed.kt`, `Routes.kt`, `CoveNavHost.kt`, DAOs, repositories, `Icons.kt`) may be edited by others too: keep edits small and additive so merges are easy. The orchestrator merges branches.
