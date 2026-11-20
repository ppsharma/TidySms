# Phase 1 — Intelligent Inbox

## Agreed scope

Phase 1 adds offline inbox categories while retaining the existing Fossify Messages storage, SMS retrieval, conversation adapter, search, archive, compose, and Recycle Bin flows.

The inbox categories are:
- Transactions
- Promotions
- Personal
- Uncategorised
- All

Transactions is the default opening category. Messages with uncertain or insufficient signals remain Uncategorised; they are not silently promoted to Personal. All shows every non-archived conversation.

## Classification and sender controls

- Classification is deterministic, local-only, and does not require network access.
- TRAI sender-header metadata is evaluated before message wording.
- Saved contacts are treated as Personal in the inbox.
- A sender-level manual category override takes precedence over automatic classification and persists locally.
- Resetting a sender to Automatic removes that override.
- Category selection persists locally across app restarts.
- The sender category control is separate from blocking a sender.
- The classifier exposes matched signals and a confidence value for diagnostics and future explanation.

## Existing data and safety

- Existing SMS/MMS history continues to be read through the existing Android Telephony provider and Fossify database pipeline; no parallel message store or adapter is introduced.
- The classifier does not rewrite or delete message content.
- No message body, OTP, contact name, or phone number is transmitted by the classifier.
- Spam quarantine and OTP extraction are outside Phase 1.
- The existing Recycle Bin behaviour remains unchanged.
- Do not install or test an experimental build as the default SMS app on a primary phone used for banking or OTPs.

## Phase 1 completion gates

### 1A — Product fixes and integration
- Confirm first-launch history retrieval and category filtering.
- Verify manual sender override, reset to Automatic, and persistence.
- Verify category selection survives restart and All restores the full inbox.
- Verify search, compose/return, archive, recycle bin, incoming messages, and dual-SIM behaviour.
- Audit for duplicate retrieval, database, adapter, and listener responsibilities.
- Confirm branding, system appearance, and typography.

### 1B — Persistent diagnostics
- Add privacy-safe persistent diagnostic logs for development/test builds only.
- Never log message bodies, OTPs, contact names, phone numbers, or attachments.
- Provide folder selection, sharing, rotation, and clear controls.

### 1C — Consolidated validation
- Run unit tests, Android lint, Detekt, and the debug build.
- Complete regression checks and final review before requesting approval to merge.

## Current validation notes

The classifier unit tests and Detekt have passed on the current development revision. Android lint and the debug APK workflow are still being checked. CI success does not replace regression testing on an emulator or secondary device.
