# Phase 1 — Intelligent Inbox (implementation notes)

## Scope
Phase 1 introduces offline categorisation into Personal, Transactions, and Promotions. It does not automatically delete, quarantine, or suppress any message. Spam quarantine remains a separate later phase, and the existing Recycle Bin remains unchanged.

## First implementation slice
- Add a deterministic, local-only Kotlin classifier with no network or Android dependencies.
- Ambiguous and unknown messages default to Personal.
- Expose matched rule signals and a conservative confidence value so later UI can explain and allow corrections.
- Keep classification separate from OTP extraction (Phase 2).

## Safety and data handling
- No message body or sender is transmitted.
- No existing SMS is modified by this classifier.
- Do not make the experimental APK the default SMS app on a primary phone.
- Before integrating classifications into Room, add an explicit non-destructive database migration. Do not rely on destructive migration for user data.

## Validation still required
- Build the Foss debug variant.
- Add a representative synthetic Indian SMS test corpus covering bank alerts, UPI, bills, shopping promotions, delivery notices, telecom alerts, and ordinary personal messages.
- Review false positives before wiring categories into the inbox.
