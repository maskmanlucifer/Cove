# Journal: blocks

An entry is a document of **blocks**: text, photos and voice notes, in any order, any number, directly next to each other (text, photo, photo, voice, voice, text, photo...). Code: `feature/journal/blocks/` (pure, unit tested), `JournalDocument` (editor state), `JournalDocumentList` / `JournalBlocksUi` (UI).

## Body format
`JournalEntryEntity.body` stays one plain string and the single source of truth. Each photo or voice note is a line holding only `⟦media:<mediaId>⟧` (id: letters, digits, `-`, `_`, at most 64). No new table, so sync, backup, the Supabase schema and DB version are unchanged and old entries stay valid. The marker does not say what kind of media it is; the `journal_media` row does.

```
Early walk to the market.
⟦media:3f2a...⟧
⟦media:91bc...⟧
Came home with far too many tomatoes.
```

## Codec rules (`JournalBodyCodec`, `JournalBlockOps`)
- `parse(body, media)` gives blocks; `serialize(blocks)` gives the body; `normalise` runs on both. Round trip is exact for any text.
- Text lines that start (after any U+2060) with `⟦` get one invisible U+2060 prefix when stored and lose it when read, so typed text can never forge or break the structure. A `⟦` line that is not a whole valid marker (truncated, bad id) is plain text.
- Repeated marker: dropped. Marker whose media row is missing (deleted, not synced yet, another entry's id): a block that shows a calm "Not available yet" box. Never a crash.
- Media rows with no marker are appended after the text, photos first, then voice notes. This is also how entries from before blocks (text, then attachments below) are read, so they look exactly as before. They are migrated to marker form lazily: only when the user edits the entry (an unchanged document is never saved).
- Adjacent text blocks merge (a newline between, unless one is empty); trailing blank lines are trimmed; there is always a text block last. Empty text between or before media is kept: it is where the user types.
- Insert at a caret splits the text block into text, media, text (first part keeps its id). Delete merges the neighbours and returns a `Removal` so Undo can split again. Move swaps with the neighbour. Drag reorders by id and applies once, on release.
- `plainText(body)` (markers gone) feeds titles (`displayTitle`), search, word counts. `textWithPlaceholders` puts `[photo]` / `[voice note]` lines in order for on-device summaries. `toMarkdown` writes the export.

## Editor
One `LazyColumn`: date and mood, title, then the blocks keyed by id (text fields keep focus and caret while others change). Photo, Voice note chips insert at the caret of the last focused text block (end of the document if none) and the caret continues in the text after it. A photo block appears at once as a placeholder; compression runs off the main thread; a failed decode removes the block and says "Couldn't use that photo". × on a block removes it (row soft-deleted, files kept until the Undo window ends). Long-press a media block and drag to reorder; every block also offers "Move up" / "Move down" as accessibility actions. Backspace at the start of a text block after media moves the caret to the end of the text before it; it never deletes media. Spacing: 16 dp around text, 12 dp between media blocks. Each voice block has its own play state; only one plays at a time. There is no limit on photos or voice notes per entry. Autosave is debounced (700 ms) and still never resurrects a deleted entry.

## Elsewhere
- Search/FTS and `SearchIndexer.enrich`: plain text only; captions of photos stay local insights. Summaries get `[photo]` placeholders; on-device only (`docs/AI.md`).
- Monthly backup `journal-YYYY-MM.md`: text as written, photos as `![photo](../Photos/<id>.<ext>)` (the Drive folder next to Backups), voice notes as `🎙 voice note (0:42)`, in the right positions. The JSON snapshot carries the body and media rows as they are; `Importer` restores both, markers and ids included.
- Sync: the body is a normal string. A body conflict shows the plain text (first line) of each side in the existing conflict screen.
- Voice command `journal_note` creates an entry with a plain body (valid, no markers).
- Debug: `--ez journalBlocks true` seeds August 2026 entries `blocks-text|mid|start|mixed|legacy|missing|long`; open with `--es route journal/blocks-mid`.
