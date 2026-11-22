# Dataset source audit — Indian SMS

## Imported source
- Repository: https://github.com/junioralive/india-spam-sms-classification
- File: dataset/spam_ham_india.csv
- Source file SHA-256 is not asserted here; upstream Git blob: 2f8552223ceafc22ce634a659a5f947ad44e4d88
- Upstream licence: MIT (see upstream LICENSE).
- Imported rows: 2267 (as fetched during preparation).

## What the source actually labels
The upstream file has only two labels: `spam` and `ham`. It does not provide TidySms categories. In this import, `spam` is not automatically treated as Promotions and `ham` is not automatically treated as Personal.

## Candidate extraction
The generated candidate file includes only rows matching conservative lexical cues in addition to the upstream label. Candidate labels are deliberately named `*_CANDIDATE` and every row is marked `requires_manual_review`. These are NOT approved training labels and must not be passed to the AWE trainer without review.

- Promotions candidates: explicit offer/discount/sale/free/claim/recharge/promo wording, excluding obvious scam/credential/OTP cues.
- Personal candidates: conversational cues such as greetings, first-person language, requests or coordination; excludes obvious banking, transaction, ticket, appointment and delivery terms.
- All other rows are excluded from this candidate file rather than forced into a TidySms category.

## Privacy processing
Email addresses, URLs, phone numbers, long numeric identifiers and account-like suffixes are masked before candidate export. This is automated masking, not a guarantee that all personal data has been removed; review is required before redistribution or training.

## Limitations
The upstream dataset is a binary spam/ham corpus. It is not representative of all Indian SMS traffic and cannot supply reliable Transactions or Services labels. The candidate rules are only a triage aid. Review labels against message meaning, not just the source label or keywords. Preserve the upstream MIT attribution when redistributing derived material.
