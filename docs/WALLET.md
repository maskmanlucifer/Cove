# Wallet: cards and documents (design, not built yet)

A private place inside Cove for the cards you own and the documents you carry (Aadhaar, PAN, boarding passes, tickets). Everything is **on this phone, encrypted, behind your biometric**. Nothing here syncs to Supabase or backs up to Drive unless you export it yourself.

Status: proposal. Nothing in this file exists in the app yet.

## Goals and non-goals

| Goal | Not a goal |
|---|---|
| Card-shaped views of your own cards: nickname, bank, last four digits, expiry, billing and due dates | Issuing real virtual cards or paying with them (needs a licensed issuer; out of scope) |
| An encrypted vault for scans, photos and PDFs of documents, with expiry reminders | Replacing DigiLocker or Google Wallet; Cove keeps a personal copy |
| Boarding passes and tickets surfaced on Today for the trip, hidden after | Storing a CVV, ever |
| Linking a card to Money through its last four digits | Cloud sync or sharing with other people |

## Principles

1. **Store the least.** Default to non-secret fields. The full card number is an opt-in extra. A CVV has no field at all.
2. **Secrets need you.** Anything sensitive is sealed by a key that Android only releases after a fresh biometric or device-credential check.
3. **Stays local.** The vault is excluded from sync, from the Drive backup and from the setup code.
4. **Honest about limits.** If the phone is rooted, or you lose the key, this is not recoverable. The export (below) is the safety net.

## What is stored where

| Data | Where | Protection |
|---|---|---|
| Nickname, bank, network, **last four**, expiry month and year, colour, billing day, due day | Room table `wallet_cards` (main database, SQLCipher) | Same as the rest of the app. These fields cannot be used to pay |
| Full card number and holder name (optional) | `wallet_cards.sealed` blob | AES-256-GCM, key `cove_vault_key`, **biometric-bound** |
| Document metadata: type, title, expiry date, masked number | Room table `wallet_docs` | Same as the rest of the app |
| Document file (image or PDF) | `no_backup/vault/<id>.bin` | Envelope encryption: a random data key per file, sealed by `cove_vault_key` |
| Full ID numbers (Aadhaar, PAN, passport) typed in | `wallet_docs.sealed` blob | Biometric-bound, like the card number |

The existing `SecretBox` (`security/SecretBox.kt`) is the pattern: a non-exportable Keystore AES-256-GCM key, with `iv || ciphertext` stored. The vault adds a second key with `setUserAuthenticationRequired(true)`.

### The vault key

```kotlin
KeyGenParameterSpec.Builder("cove_vault_key", ENCRYPT or DECRYPT)
    .setBlockModes(BLOCK_MODE_GCM)
    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(60, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL) // one prompt opens the vault for 60 s
    .setInvalidatedByBiometricEnrollment(true)
```

- **One prompt per visit.** Opening Wallet runs `BiometricPrompt`. After success, the key is usable for 60 seconds, so a screen full of cards or a document decrypts without prompting for each item. Leaving the Wallet screens ends the session in the app as well.
- **Envelope encryption for files.** Each file gets its own random AES key. Only that small key is sealed by the vault key. Changing or rotating the vault key means re-sealing small keys, not re-encrypting every file.
- **Enrolling a new fingerprint invalidates the key** (the safer default). Then every sealed value is unreadable. This is the main data-loss risk, so the export below is offered up front, and the app says so before the first save.
- **Why a separate key:** the main database key is deliberately not authenticated (alarms, widgets and background workers must read the database; see `docs/SECURITY.md`). That trade-off is acceptable for to-dos but not for card numbers and IDs. This is the "private partition" that `docs/SECURITY.md` already describes as the stricter option.

## Cards

- **List:** card visuals sorted by a pinned order. Each shows nickname, bank, `•••• 1234`, expiry, and the next due date. No full number on the list, ever.
- **Detail:** the same card, larger. **Reveal** (biometric) shows the full number and holder if stored. **Copy number** puts it on the clipboard, marked sensitive and cleared after 30 seconds.
- **Add:** manual entry first. Validate with the Luhn check and a plausible expiry. The network (Visa, Mastercard, RuPay, Amex) is derived from the number prefix, so no network picker is needed. If the number is not stored, only the last four are kept; the full number is discarded in memory.
- **Scan (later phase):** camera plus on-device text recognition fills the form. Every field stays editable because OCR makes mistakes. The scan image is never saved.
- **Reminders:** card expiry (30 days before) and billing or due dates, through the existing nudge and WorkManager infrastructure.
- **Money link:** `SmsTransactionParser` already extracts `last4`. Matching it to a wallet card lets an imported payment say "Paid with HDFC •••• 1234" instead of a generic "Card". This works even if you never store the full number.

## Documents

- **Types:** Aadhaar, PAN, passport, driving licence, insurance, boarding pass or ticket, other.
- **Capture:** the on-device ML Kit document scanner (crop, deskew, multi-page PDF) or import from gallery or files. Scanning the barcode on a pass or ticket reads its text so it can be shown again.
- **Masking:** for ID numbers the list shows only the last four digits (`XXXX XXXX 1234`). The full value needs the biometric, the same as cards. Cove never shows a full Aadhaar number without a deliberate tap.
- **Viewer:** `FLAG_SECURE` (no screenshots, hidden in recents), pinch to zoom, and for passes a **max brightness** mode for scanning at a gate.
- **Boarding passes and tickets:** a date is required. From the day before until a few hours after, a small card appears on Today. After that, the pass is archived and the Today card disappears.
- **Expiry:** passport, licence and insurance get a reminder before the date.

## Screens

| Screen | Notes |
|---|---|
| Me > Wallet | Entry point first, so the dock and Today stay untouched while this is proven out |
| Wallet home | Two segments: **Cards** and **Documents**. Locked state shows only the title and "Unlock" |
| Card detail and edit | As above |
| Document detail and edit | Viewer, metadata, delete |
| Add sheet | "Add a card" or "Add a document", then enter or scan |

All screens use the existing design system (`CoveText`, `CoveCard`, `PillButton`, sheets) and the code-drawn illustrations for empty states ("Nothing here yet. Keep your cards and documents in one safe place.").

## Sync, backup and clearing

- **Sync:** the new tables are **not** registered in `SyncTables`, so they never reach Supabase.
- **Drive backup:** `ExportBuilder` skips wallet tables and the vault folder.
- **Setup code:** unaffected; it holds service credentials only.
- **Clear all data** (`docs/DATA_CONTROLS.md`): deletes the tables, the `no_backup/vault` folder and the `cove_vault_key` Keystore entry. The confirmation sheet says wallet items are included.
- **Reinstall, new phone or a new signing key:** the vault does not survive. Use the export.
- **Encrypted export (phase 4):** one file, AES-256-GCM, with a key derived from a passphrase you choose (PBKDF2-HMAC-SHA256, at least 600,000 iterations, random salt). It can be saved to Drive or anywhere you like and imported on a new phone. Cove cannot recover a forgotten passphrase.

## Data model (Room, migration 11 to 12, additive)

```kotlin
@Entity(tableName = "wallet_cards")
data class WalletCardEntity(
    @PrimaryKey val id: String,
    val nickname: String,
    val bank: String,
    val network: String,        // visa | mastercard | rupay | amex | other, derived
    val last4: String,
    val expiryMonth: Int,
    val expiryYear: Int,
    val kind: String,           // credit | debit | prepaid
    val color: String,
    val billingDay: Int? = null,
    val dueDay: Int? = null,
    val sealed: ByteArray? = null,   // full number + holder, biometric-bound; null when not stored
    val position: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "wallet_docs")
data class WalletDocEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val maskedNumber: String? = null,
    val expiresOn: Long? = null,     // epoch day for passes, tickets and licences
    val eventAt: Long? = null,       // boarding time, for the Today card
    val files: String,               // ordered ids of encrypted files
    val sealed: ByteArray? = null,   // full ID number, biometric-bound
    val createdAt: Long,
)
```

No `updated_at` or `deleted_at` is needed, because these tables never sync. Deletes are real deletes, and the encrypted files are removed with them.

## Dependencies

| Need | Option | Note |
|---|---|---|
| Document scanner | ML Kit Document Scanner (Play services) | On-device. Needs Google Play services, which the app already depends on |
| Barcode and QR reading | ML Kit Barcode Scanning | Bundled or Play services variant |
| Card OCR | ML Kit Text Recognition (Latin) | On-device. Result is parsed with a Luhn check |
| Biometric | `androidx.biometric` | Already in the project |

No network access is added, and nothing leaves the phone.

## Phases (each ends in something usable)

1. **Documents vault.** `wallet_docs`, `cove_vault_key`, envelope-encrypted files, scan or import, viewer with `FLAG_SECURE`, expiry reminders, boarding-pass card on Today. *Verify:* unit tests for the crypto round trip, masking and Luhn; manual check that files are unreadable via `adb pull` and that screenshots are blocked.
2. **Cards, reference only.** `wallet_cards` with last four, expiry and dates (no sealed number yet), card visuals, billing reminders, link to SMS import by `last4`. *Verify:* tests for network detection, expiry rules and SMS matching.
3. **Optional full number and scanning.** The sealed blob, Reveal and Copy with clipboard clearing, camera scan for cards. *Verify:* tests for the sealed round trip and key invalidation handling.
4. **Encrypted export and import.** Passphrase-based, with a visible warning when the key is invalidated. *Verify:* export on one install, import on a clean one.

## Risks and open questions

| Risk | Mitigation |
|---|---|
| Fingerprint changes make the vault unreadable | Warn before the first save, offer the export, make the behaviour explicit in Me > Wallet |
| Phone lost or rooted | The sealed values need the biometric on the original hardware. A root attacker able to run code as the app can still try to use the key within the 60 second window. Keep the window short |
| Clipboard or screenshots leak numbers | `FLAG_SECURE` on all Wallet screens, sensitive clipboard flag, auto-clear |
| Users expect cloud backup | Say plainly that the vault is local; the export is the only way to move it |
| Legal and policy: Aadhaar and card data | This is a personal vault on your own phone, which differs from a business storing others' data. If Cove is ever offered to other people, revisit with a proper privacy policy and legal advice before shipping |

Open questions:
1. Should the full card number be storable at all, or should phase 3 be dropped?
2. Is a 60 second unlock window right, or should every reveal prompt?
3. Is Me > Wallet the right home, or should Wallet become a main tab?
