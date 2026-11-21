# SMS training data status

## Files and trust levels

- `seed_messages.csv`: 360 authored synthetic training examples, 120 per ML label. They are deliberately marked `synthetic_curated`; they are not claimed to be real inbox messages.
- `holdout_challenge.csv`: 15 separately authored challenge examples, 5 per label. These are not included in training. They are a smoke-test set, not a statistically representative benchmark.
- `india_spam_ham_candidates.csv`: 763 candidates from an MIT-licensed Indian spam/ham source. Every row remains `requires_manual_review`; none has been promoted into the training set.
- `INDIAN_SMS_SOURCE_AUDIT.md`: provenance and limitations of that public source.

## Why the first usable set is synthetic

The public Indian collections inspected so far do not provide trustworthy three-way Personal / Services / Promotions labels. The Indian telecom corpus is binary spam/ham. ScamShield's Indian corpus mixes promotional spam, scams and legitimate examples; courier examples in it include phishing messages, not ordinary delivery notices. A separate SmartSMS project advertises a 13,500-row six-class dataset, but its own README says roughly 8,500 rows are synthetic and only about 5,000 derive from UCI ham/spam; it is not an independent real Indian services corpus.

It would be misleading to relabel spam as Promotions, ham as Personal, or courier scams as legitimate Services. The current 360-row set therefore bootstraps the data pipeline only. It is not sufficient evidence to ship an ML classifier.

## Grouping and evaluation

Rows generated from the same base wording share a `template_group`. Training/validation splitting must keep each group together to prevent near-identical variants leaking across the split. The challenge set uses different wording and remains untouched during training.

## Next quality gate

Before enabling the model by default, add reviewed, consented or appropriately licensed real-world examples across all three labels, especially genuine Indian delivery, booking, telecom, utility, appointment and customer-service notifications. Measure per-class precision/recall and inspect confusion pairs. Use Uncategorized when confidence is insufficient.
