# Design prompts

Ready-to-paste prompts for a design tool (Claude Design, Figma AI, v0 or a human designer) for the new assistant screens in `docs/ASSISTANT.md` and `docs/WALLET.md`. Paste **Brief 0** first, then one screen brief at a time.

## Brief 0: shared context (paste first)

> You are designing screens for **Cove**, a calm, voice-first Android companion app for one person: alarms, to-dos, habits, money, a journal and a morning brief. The feeling is a **calm garden**: warm paper, forest ink, leaf green, sunny highlights, soft rounded shapes, kind short copy with no exclamation marks. It must never feel like a dashboard or a hacker tool.
>
> **Canvas:** 390 x 844 dp, Android 12+, edge-to-edge. Design every screen in **light and dark**.
>
> **Colour (light / dark):** canvas `#F6F1E6` / `#121A14` with a faint paper grain; card `#FFFCF5` / `#1B261E`; ink `#1F2B22` / `#EEE9D8`; muted text `#55614F` / `#A5B09E`; accent (leaf green) `#2F6B45` / `#9ED08F`; soft accent fill `#E1EEDA` / `#243A2A`; hairline `#E9E3D2` / `#26332A`; alert `#A34824` / `#E08A64`; saved `#336E4E` / `#6DB38E`. Highlight tints for cards: sun `#FBEBC2`, coral `#FBE0D2`, lilac `#E9E3F8`, blossom `#FADCE7`, leaf `#E1EEDA`, sky `#DDEFF5` (dark variants are deeper, muted versions). Use a tint for **one main surface per screen**, not everything. Text on tints is ink or muted, never the strong hue.
>
> **Type:** Geist for all UI text and numbers (tabular figures). Big titles only in Fraunces (soft, warm). Body 16 sp, meta 13 sp.
>
> **Shapes:** 24 dp cards, pill buttons (56 dp primary in ink-green with cream text; 44 dp secondary), grouped rows with hairline dividers, floating bottom sheets, a glowing voice orb in the bottom dock (peach, sun, leaf, lilac gradient over cream).
>
> **Illustration:** soft painted meadows and one friendly sage-and-clay companion character, used sparingly (at most one per screen), decorative only. Empty states use a small picture, a short title and one line.
>
> **Rules:** minimal and quiet; one primary action per screen; large touch targets (48 dp); WCAG contrast (ink 7:1, muted 4.5:1); respect reduce motion; no new bottom tab; every screen needs **empty, loading, error and filled states**. Copy is short, warm and specific.

## Screen briefs

### 1. Listening that finishes by itself
> The voice screen while the user speaks. The orb breathes with their voice and the live transcript appears in large type. When they pause, a thin **ring around the orb quietly fills** over the pause length to show Cove is about to process, and it **resets the moment they speak again**. Words already heard stay visible. A small "Done" pill is still there for people who want to end it now. When the ring completes, the transcript animates into the result card (no jump). Show states: waiting for speech, speaking, pause with ring filling (short, normal, patient lengths), resumed speaking, and processing. Include a calm "I didn't catch that" state with Try again and Type instead.

### 2. Result card for a remembered note ("I parked on level 3, pillar B")
> A draft card shown after a dump that is not a command. Title "Remembered", then the user's own words, with small editable chips for **what it's about** (car), **kind** (place) and **how long to keep it** ("Until tomorrow", "A week", "Keep"). Primary "Save", secondary "Edit". After saving, an Undo toast. Tint the card sky. Also show the empty state of the Memories list ("Nothing remembered yet. Say it once and I'll keep it.").

### 3. Answer card ("Where did I park?")
> A calm answer that is spoken and shown: large Fraunces sentence ("Level 3, pillar B"), a small line under it ("You said this 2 hours ago"), and quiet secondary actions: "Not anymore" (forget it), "Update", "Done". If several memories match, show them as a short grouped list with the best one on top. Include the "I don't have anything about that" state with an offer to remember it. Tint the card sun.

### 4. Ask your data ("How much did I spend on food this week?")
> A single answer card: the figure in large Geist numerals, the comparison in one muted line ("₹2,340 of ₹6,000 budget, 3 days left"), and a tiny sparkline or bar in accent colour. Under it, "See details" opening the existing Money screen. Show a spoken-answer indicator and a text-only variant. Coral tint.

### 5. Edit by voice
> A confirmation sheet for "Move my 6 pm reminder to 7": the item with the **change highlighted** (old time struck through, new time in accent), "Save" and "Cancel". Also an ambiguity state where Cove asks which one ("Call dentist or Call mom?") with two large tappable choices. Show the Undo toast after saving.

### 6. Keep vault: locked and unlocked
> A private area under Me, called **Keep**: documents, passes and warranties. **Locked state:** a quiet title, a small lock illustration, and one "Unlock" button (biometric). **Unlocked:** a list of document cards grouped by type with a small type icon, title, masked number (`XXXX XXXX 1234`) and an expiry chip that turns coral when under 30 days. A floating "Add" pill with two choices: Scan, Import. Empty state: "Nothing here yet. Keep your documents in one safe place." Include a banner line "Stays on this phone. Never synced or backed up."

### 7. Document viewer and scan
> A full-screen viewer with pinch to zoom, a small top bar (back, title, more), and a bottom row with "Reveal number" (biometric), "Share" and "Delete". A **max brightness** mode for boarding pass QR codes with a one-tap toggle. Scan flow: camera with edge detection, a review step, then a form with type, title and an optional expiry date. Mark the screen as private (no screenshots) with a subtle "Private screen" indicator.

### 8. Coming up (renewals and expiry)
> A section on Today, only shown when something is due soon: "Coming up" with up to three rows (passport, insurance renewal, Netflix renews tomorrow ₹649), each with a relative date, a hue dot and a one-tap action ("Remind me", "Done"). A "See all" link opens the full list grouped by this week, this month and later. Empty state is simply absent from Today.

### 9. Boarding pass on Today
> A compact card that appears the day before a trip: airline and flight, departure time, gate if known, and a large "Show pass" button that opens the viewer at max brightness. After the flight it fades away and moves to an archive.

### 10. Weekly review (Sunday)
> One card with three short lines: money (spent against budget), habits (days kept), to-dos (done of planned), and one kind sentence. A leaf tint, a small meadow illustration, and a single "Open the week" link. No charts beyond one small bar for each line.

### 11. Settings for the assistant
> Under Me > Voice, a quiet group: **Finish when I stop talking** (switch), **Pause length** (Short, Normal, Patient as a segmented control with a live demo ring), **Spoken answers** (switch), and **Memories** (opens the list, with "Forget everything" at the bottom behind a confirmation sheet). Explain privacy in one line: "Memories stay on this phone."

### 12. Rethinking the navigation bar
> Cove's bottom dock today has **five tabs** (Today, Plan, Money, Journal, Me) and a **glowing voice orb** that sits in the dock and opens the voice screen. Problems to solve: five equal tabs feel like a generic app, the orb competes with them, reaching the far tabs one-handed is awkward, and new areas (Keep vault, Renewals, Memories) must **not** become more tabs.
>
> Explore **four distinct directions**, each as light and dark frames for Today, Money and the voice-listening state, and for each write a three-line "good for / costs / best when":
> 1. **Voice-first dock:** the orb becomes the centre and the main control. Only two or three tabs remain on either side (for example Today, Plan, Money), and Journal and Me move into a "More" sheet opened from a quiet profile or overflow button.
> 2. **Swipe between pages:** no tab labels; Today is home, and swiping or a small page indicator moves between Plan, Money and Journal. The orb floats bottom-right as a single action. Include how Me and settings are reached.
> 3. **Ask bar:** a slim, always-visible "Ask or say anything" pill (tap to type, hold or tap the mic to speak) replaces the dock. Sections are reached from Today cards and from a "Go to" list inside the pill. Include a recent-places row.
> 4. **Adaptive dock:** the bar shows only the **three most used** places plus the orb, learns from use, hides while scrolling down, and returns on scroll up. Others live under "More".
>
> Constraints: one-handed reach (primary controls in the bottom third), 48 dp targets, labels remain visible for the active destination, state is clear for screen readers, reduce-motion safe, and **no new tabs**. Show how a badge or a "Coming up" nudge appears, and how a locked area (Keep) looks. Finish with a recommendation and the migration cost for each option.

## Deliverables to ask for
- Light and dark frames for each state above at 390 x 844 dp.
- A spec table per screen: spacing, type styles, colours by token name, corner radii.
- Motion notes for the ring fill, the transcript to result transition, and the answer card entrance (all skipped under reduce motion).
- Copy deck with every string, written in Cove's tone.
