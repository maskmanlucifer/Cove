# How categorisation works

Owner's rule: filing an expense is cheap, instant and offline. Rules and learning decide; AI is only an occasional, manual cross-check, and it never changes anything by itself. Code: `data/categorize/`.

## Per expense (no AI)
`ExpenseCategorizer.suggest(note, categories, memory, payee?)` returns `Suggestion(categoryId?, confidence 0..1, reason)`. Layers, strongest first:
0. **Payee memory** (`PayeeMemoryEntity`, see "Payee memory" below), only when the caller knows the expense's payee (imported payments). It wins over everything else. Confidence 0.87 (one confirmation) to 0.95; reason "Learned from your earlier payment".
1. **Learned** (`CategoryMemoryEntity`, token to category with a count). Written whenever the user explicitly picks or changes a category (Add expense chips, voice draft chip, Review accept). A different pick lowers the old mapping first, so a changed mind wins quickly. Confidence 0.75 to 0.95.
2. **Your words**: the category's "Words that file here" (`ExpenseCategoryEntity.keywords`, comma separated; a phrase needs all its words), then the category's own name, with simple plural matching. 0.85 / 0.90 for phrases / 0.70 for the name.
3. **Built-in rules** (`BuiltInRules`): common words and Indian merchants, mapped onto the user's categories by name through a synonym list (Food is also Eating out, Groceries, Dining; Transport is also Travel, Commute, Fuel; and so on). Only used when such a category exists. 0.55 to 0.65.
4. **Nothing**: `categoryId` is null and the caller uses "Other" when it exists.

Ties go to the earlier category in the user's order, then name, then id. Tokens are lowercase letters only (accents folded, digits, currency and stop words dropped), so "Lunch · Café Ivy" gives lunch, cafe, ivy. "Other" is never matched by name or keyword.

Used by: voice `log_expense` (through `CategoryResolver`, so `ai/provider/rules` never depends on features), Add expense (the chip follows the typed note until the user taps one) and Review.

Generic fallback notes ("Payment", "Money received") say nothing: `suggest` returns nothing for them, `teach` ignores them, and the word "payment" never teaches or matches inside a longer note either. Memory rows polluted by older versions stay on disk but are never used.

## Payee memory (labels for repeat payments)
Words only work when the note has a merchant in it. A QR payment to `paytmqr2810050501abcd@paytm` has no readable name (the note becomes "Payment"), and a label the user typed ("Gym") would be forgotten. So imported payments carry a stable **payee key** (`data/sms/PayeeKey`, `docs/SMS_IMPORT.md`) and Cove remembers, per payee, the category AND the label.

- **Entity**: `PayeeMemoryEntity(payeeKey, categoryId, label?, displayName, count, updatedAt, deletedAt)`. `label` is the note the user wants shown; null means "keep the cleaned merchant name". `displayName` is that cleaned name.
- **Teaching** (`PayeeLearning`, explicit actions only): (a) in the import review, a category pick or a typed label on a row that is imported; (b) editing the category and/or note of an expense that has a payee; (c) accepting rows in Money > Review (category only; the stored label is kept). Rows the user did not touch, bulk "Import N" of untouched rows, voice and manual adds never teach a payee. A note left as the generated name stores no label. Agreeing confirmations raise the count (max 20); a different category lowers it and replaces the mapping at one, like `CategoryLearning`. The word memory is still taught from the final note, for typed and voice notes.
- **Recall**: on import, `rowFor` asks the categorizer with the payee's memory, so the remembered category and label are applied and the row shows "Learned from your earlier payment"; it stays editable. In Review, rows with a payee key use it too. Priority: payee memory, then word memory, then your keywords and category names, then built-ins.
- **Undo**: import undo and Review undo put each payee row back exactly as it was (`PayeeSnapshot`), not by subtraction.
- **Retro-tagging** (`PayeeLogic.retroChanges`, `RetroTag`): after tagging a payee in the import review or an expense edit, if other spent expenses with the same key differ (another category, or another note when a label was set), a calm top prompt asks "Tag 3 earlier payments to this payee too?" with Apply / Not now (it leaves by itself after 15 s). Apply updates them without teaching anything and offers Undo through `UndoCenter`.
- **Learn from my past payments** (`PayeeLogic.pastProposals`, Money > Review): payees with no memory yet whose spent expenses sit at least twice in one real category (and more than in any other) are proposed; one tap creates the memory (category only), with Undo. It hides itself when there is nothing left to propose. Only expenses imported after payee keys existed carry one.
- **Control**: the expense edit screen shows the payee (cleaned name, handle small) and "Remembered for this payee: Gym · Health" with Forget. Me > "Forget imported-message history" also has "Forget what Cove learned about payees", which soft-deletes every `payee_memory` row (expenses and word memory stay).

## Sync
`payee_memory` (key `payee_key`) and `expenses.payee_key` sync like other expense data (`supabase/migrations/0009_payee_memory.sql`, also in `supabase/setup.sql` and its asset copy), are part of backups, and live in Room version 10 (`MIGRATION_9_10`). Payee handles and labels therefore reach the user's own Supabase; SMS text never does.

`category_memory` is a synced table (`SyncTables`, `supabase/migrations/0004_categorize.sql`, also in `supabase/setup.sql` and its asset copy). `expense_categories.keywords` syncs with the category. Room version 6 (`MIGRATION_5_6`).

## Review (Money tab, `money/review`)
A quiet "N in Other · Review" row appears on Money when at least 3 spent expenses of the last 60 days are in Other or have no category. Each row shows the suggestion with its reason (learned, your keyword, its name, built-in, AI), with Accept, a chip picker and Skip; Accept all files every open row that has a suggestion and offers Undo. Accepting writes through `MoneyRepository.applyCategories` and teaches the memory; undo restores the categories and forgets what was taught.

- **Check with AI** asks about the open rows that still have no suggestion.
- **Cross-check this month** asks about filed expenses of the last 30 days and lists only those where AI disagrees; nothing changes until the user taps.

Both are manual only, call `AiService.suggestCategories` in batches of at most 40 (one call each), and show "On-device" or "Cloud" plus plain-language errors. See `docs/AI.md` for routing and privacy: only scrubbed note text (no amounts) and category names are sent, as everyday (never journal) data.

## Imported messages
`Import from messages` (`docs/SMS_IMPORT.md`) suggests each imported payment's category with `ExpenseCategorizer` on the cleaned merchant name, and teaches the memory only when the user changes a category by hand; undoing the import forgets what it taught.
