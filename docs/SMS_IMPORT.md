# Import from messages

Money > "Import from messages" finds payments in the bank, card and UPI texts on this phone and adds the ones the user approves as expenses. Code: `data/sms/` (parser, dedupe, inbox, repository) and `feature/money/imports/` (screens). Route `money/import`.

## Privacy (the rules)
- Message text is read and parsed **on the phone only**. It is never sent to any AI or service, never logged, never synced, never put in a backup (`allowBackup=false`; `sms_import_log` is not in `SyncTables`).
- Nothing keeps the text: the parser returns parsed fields (amount, direction, merchant, time, last four digits, reference, bank), the screens hold those, and the log stores a **hash** of (sender, normalised body) plus parsed fields. Only the expenses the user chose are written, and only they sync (with the payee key and, once the user tags a payee, the payee memory: UPI handle or merchant name, category and label).
- The AI category cross-check still sends only note text without amounts (`docs/AI.md`). An imported expense's note is the cleaned merchant name, so the same rule applies.
- `READ_SMS` is a sensitive permission. Cove is sideloaded, so Play policy does not apply. On Android 13+ a sideloaded app may be blocked by "Restricted setting"; the denial guide tells the user to open App info > three dots > "Allow restricted settings", then "Open settings".
- Without the permission, **Paste a message** parses what the user pastes (one or many messages) with the same parser and dedupe.

## Flow
1. Rationale + `READ_SMS` request (calm copy; denial and permanent denial show the guide with "Open settings"; paste is always offered).
2. Range: Since last import (default; falls back to 30 days when nothing was imported), Last 30 days, This month, All available, with a count estimate.
3. Scan on `Dispatchers.Default`, cancellable, in pages of 500 read through a cursor (a 50,000-message inbox is never in memory at once), with live counts.
4. Review: **New** (checked) and **Possible duplicates** (unchecked, with the match shown). Each row has the suggested category (`ExpenseCategorizer`, learned memory first), a Spent/Received switch and a check. Changing a category by hand teaches the memory when imported (and undo forgets it). "Select all new" / "Select none". "Import N" runs once (guarded in the ViewModel).
5. Done: "Added 14 · skipped 3 duplicates" with Undo (through `UndoCenter`, area `money`): the batch's expenses are soft-deleted, its log rows removed (so the messages show up again), and what was taught is forgotten.

Failures give calm sentences and a next step: permission revoked mid-scan returns to the rationale, a null cursor or provider error offers retry or paste, an empty result offers "Change range" and "Paste a message".

## How parsing works (`SmsTransactionParser`)
`parse(sender, body, receivedAt)` returns `Accepted(ParsedSms)` or `Rejected(reason)` (reason is for debug and tests).
1. Sender: personal phone numbers are rejected. Alphanumeric ids (`AX-HDFCBK`, `VM-ICICIB`, `JD-SBIUPI`) are trusted; any other sender, or pasted text without a sender, needs strong structure (account/card/UPI/VPA/reference words plus a last4, reference or VPA).
2. Rejections, in order: OTP (code next to "OTP", or "OTP" without a completed debit/credit word), promo (hard offer phrases; soft ones when no completed verb), future ("will be debited", pre-debit, scheduled), requests (collect/"has requested"), failed/declined/reversal, statements, dues and reminders, card-bill acknowledgements, mandate set-up notices, balance-only.
3. Amount: `Rs`, `Rs.`, `INR`, `₹` with Indian grouping and decimals, skipping figures that follow "bal", "avl", "limit", "outstanding", "due", "min", "total". SBI-style texts with no currency symbol ("debited by 450.0") use a verb-anchored fallback.
4. Direction: first of debit words (debited, spent, sent, paid, purchase, withdrawn, charged, Dr.) or credit words (credited, received, refund, deposited, Cr.); refunds are credits.
5. Merchant: `UPI/P2M/ref/NAME`, `NEFT-ref-NAME`, VPA local part ("zomato@okaxis" becomes Zomato; handle noise and trailing digits dropped), then "to/at/towards/trf to" for debits and "from/by" for credits, ICICI "; NAME credited", Axis card "time NAME Avl". Names are cleaned (VPA, handles, long digit runs, "PVT LTD") and title-cased (known acronyms such as IRCTC kept).
6. Date and time from the text (dd-mm-yy, dd/mm/yyyy, 05Oct26, 05-Oct-2026, 2026-10-05, "5 October, 2026"); otherwise, or when the date is more than a day ahead or over 400 days old, the SMS timestamp.
7. Last four digits (A/c, Acct, AC, Card), reference (UPI Ref, Refno, UTR, RRN, Txn id, `UPI:123..`), bank (sender id, then body), paid-with (UPI, Card, Cash for ATM, Bank transfer), confidence 0..1.

Supported formats (each has realistic synthetic samples in `SmsTransactionParserTest`): HDFC, ICICI, SBI (UPI, user, card), Axis (UPI, card), Kotak, IDFC First, Yes Bank, Canara, Bank of Baroda, PNB, Federal, IndusInd, Paytm Payments Bank, American Express, plain credit-card "spent/charge", UPI debit and credit, IMPS/NEFT, ATM withdrawals, autopay debits, refunds.

Limitations: English only; banks change wording, so an unseen format may be skipped (it is then ignored, not wrongly imported); a message's own date is read as day-month-year; identical text from the same sender is treated as one message; people-to-people UPI texts are imported with the payee's name, not the reason; the samples are synthetic, not taken from real accounts.

### Adding a bank format
1. Add the sender key to `bankByKey` if the bank name is not yet known.
2. Add a realistic synthetic sample to `accepted` in `SmsTransactionParserTest` with the expected amount, direction, merchant, last4, reference and bank.
3. Run `./gradlew :app:testDebugUnitTest --tests '*SmsTransactionParserTest*'`. If the merchant is missing, add a pattern to `debitTargets` or `creditTargets` (they are tried in order); if the direction or amount is wrong, adjust `debitWord`, `creditWord` or `amountRegex`. If it is wrongly rejected, check which rule fires with the `Rejection` in the failure message.
4. Add a rejected sample for the bank's OTP or promo text to `rejected`.
5. Bump `SmsTransactionParser.VERSION` when a change should re-judge messages that were earlier ignored.

## Payee identity (`PayeeKey`, pure, tested in `PayeeKeyTest`)
Every accepted message gets `ParsedSms.payeeKey`, copied to `ExpenseEntity.payeeKey` (indexed) on import. It identifies the other side of the payment, never the user's account (the last four digits are never used).
- **UPI handle**: `vpa:` plus the whole handle, lowercased and trimmed (`vpa:zomato@okaxis`, `vpa:bharatpe.9000123456@fbpe`, `vpa:gpay-1123@okaxis`, `vpa:merchant.name@ybl`). QR merchant handles are stable per shop. The user's own handle ("your VPA", "linked to VPA") is skipped. Handles may contain a phone number; they sync like other expense data.
- **One documented exception**: `paytmqr<8 or more digits><suffix>` drops the per-QR suffix (`paytmqr2810050501abcd@paytm` and `...wxyz@paytm` are one shop, `vpa:paytmqr2810050501@paytm`), because the digits are the merchant and the suffix only tells printed QR codes apart. No other pattern is guessed at: merging two shops would be worse than missing one.
- **Name** (card, NEFT, IMPS, UPI texts with a name only): `name:<TYPE>:<NAME>` with TYPE `UPI`, `CARD` or `BANK` and the name uppercased, without store numbers and ids (tokens with 3+ digits), a trailing city, "PVT LTD", "INDIA", "STORE" and similar (`name:CARD:STARBUCKS COFFEE`).
- **No key**: generic fallbacks ("Payment", "Money received", "UPI"), ATM cash and empty names.
When the handle is opaque (`paytmqr...`, `bharatpe.9000...`, `gpay-1123`) the merchant is null, the note is "Payment" and the review row shows the handle small under it; the user's first label ("Gym") is what gets remembered.

## Payee memory in the review
Import review rows are pre-tagged from payee memory (category and label, hint "Learned from your earlier payment") and stay editable: category pill, a "Rename" field for the label. Only a category pick or a typed label teaches (see `docs/CATEGORIZATION.md`); undoing the import restores payee memory. After the import, a top prompt offers to tag earlier payments to the same payees. "Forget what Cove learned about payees" is in Me > Forget imported-message history; it clears payee memory only. Expenses imported before version 10 have no payee key (the message text is not kept), so they cannot be re-tagged this way.

## Dedupe (rigorous, idempotent, explainable; `SmsDedupe`, tested by tables in `SmsDedupeTest`)
1. **Message level.** `sms_import_log` (local Room table, DB version 9, migration 8 to 9) is keyed by `msg:` + SHA-256 of (upper-cased sender, lower-cased whitespace-normalised body) and also by the SMS provider id. A message already imported, duplicated or skipped is never parsed again, so re-scanning imports nothing twice. Messages judged "not a transaction" are logged as `ignored` with the parser version and are re-read only when the parser version rises. The log holds: key, provider id, outcome (imported, duplicate, ignored, skipped), parsed fields, reference, expense id, batch id, times. No text.
2. **Across messages (one payment, several texts).** A bank text and a UPI-app text collapse into one candidate when direction and amount match and either the reference ids are equal (any time apart), or no reference conflicts and they are within 10 minutes with the same last four digits or a similar merchant and no contradiction. The richer record wins and fills its gaps from the other; all message ids are kept so every message is logged.
3. **Against the log.** A candidate with the same reference as a decided message, or the same payment by rule 2, is dropped and logged as duplicate (a skipped message and its twin from another sender stay skipped).
4. **Against existing expenses** (manual, voice, earlier imports). Same reference on an expense's `externalRef` is an exact match at any distance. Otherwise same direction and amount within 36 hours is a possible duplicate; same local day plus a similar merchant or note is marked exact. Each expense is matched at most once (two real ₹340 payments next to one manual ₹340 leave one candidate unflagged). Possible duplicates are listed unchecked with the match ("Possible duplicate: already added "Lunch ₹340""); left unchecked they are logged as duplicate, checked they import.
5. **Undo, restore, forget.** Imported expenses store `externalRef` (the reference, or the message hash when there is none; synced; Supabase `0008_expense_external_ref.sql`, also in `setup.sql` and its asset copy). Undo soft-deletes them and removes the batch's log rows, so messages come back and re-import cleanly. Before each scan, log rows are re-created from imported expenses that the log does not know, so a backup restore (the log is not backed up) or "Forget imported-message history" (Me > More; clears the log, not the expenses) cannot cause double imports.

## Not covered
Real bank messages on a real phone were not available while building; formats follow public examples and are synthetic. The ordered rules above are where to adjust when a real message is missed.
