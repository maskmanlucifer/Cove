# How categorisation works

Owner's rule: filing an expense is cheap, instant and offline. Rules and learning decide; AI is only an occasional, manual cross-check, and it never changes anything by itself. Code: `data/categorize/`.

## Per expense (no AI)
`ExpenseCategorizer.suggest(note, categories, memory)` returns `Suggestion(categoryId?, confidence 0..1, reason)`. Layers, strongest first:
1. **Learned** (`CategoryMemoryEntity`, token to category with a count). Written whenever the user explicitly picks or changes a category (Add expense chips, voice draft chip, Review accept). A different pick lowers the old mapping first, so a changed mind wins quickly. Confidence 0.75 to 0.95.
2. **Your words**: the category's "Words that file here" (`ExpenseCategoryEntity.keywords`, comma separated; a phrase needs all its words), then the category's own name, with simple plural matching. 0.85 / 0.90 for phrases / 0.70 for the name.
3. **Built-in rules** (`BuiltInRules`): common words and Indian merchants, mapped onto the user's categories by name through a synonym list (Food is also Eating out, Groceries, Dining; Transport is also Travel, Commute, Fuel; and so on). Only used when such a category exists. 0.55 to 0.65.
4. **Nothing**: `categoryId` is null and the caller uses "Other" when it exists.

Ties go to the earlier category in the user's order, then name, then id. Tokens are lowercase letters only (accents folded, digits, currency and stop words dropped), so "Lunch · Café Ivy" gives lunch, cafe, ivy. "Other" is never matched by name or keyword.

Used by: voice `log_expense` (through `CategoryResolver`, so `ai/provider/rules` never depends on features), Add expense (the chip follows the typed note until the user taps one) and Review.

## Sync
`category_memory` is a synced table (`SyncTables`, `supabase/migrations/0004_categorize.sql`, also in `supabase/setup.sql` and its asset copy). `expense_categories.keywords` syncs with the category. Room version 6 (`MIGRATION_5_6`).

## Review (Money tab, `money/review`)
A quiet "N in Other · Review" row appears on Money when at least 3 spent expenses of the last 60 days are in Other or have no category. Each row shows the suggestion with its reason (learned, your keyword, its name, built-in, AI), with Accept, a chip picker and Skip; Accept all files every open row that has a suggestion and offers Undo. Accepting writes through `MoneyRepository.applyCategories` and teaches the memory; undo restores the categories and forgets what was taught.

- **Check with AI** asks about the open rows that still have no suggestion.
- **Cross-check this month** asks about filed expenses of the last 30 days and lists only those where AI disagrees; nothing changes until the user taps.

Both are manual only, call `AiService.suggestCategories` in batches of at most 40 (one call each), and show "On-device" or "Cloud" plus plain-language errors. See `docs/AI.md` for routing and privacy: only scrubbed note text (no amounts) and category names are sent, as everyday (never journal) data.
