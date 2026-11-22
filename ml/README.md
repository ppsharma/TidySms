# TidySms on-device SMS classifier training

This directory contains the reproducible training path for the **TidySms-specific**
Average Word Embedding (AWE) text classifier. The exported model is intended to run
locally through MediaPipe Tasks Text on Android.

## Classification architecture

TidySms does **not** ask this model to relearn all five inbox categories.

- **Transactions:** remain handled by the existing, tested deterministic transaction rules in `SmsClassifier`.
- **Personal, Services, Promotions:** are the three categories the AWE model is intended to distinguish.
- **Uncategorized:** is the fallback when the transaction rules do not match and the ML result is absent or below the confidence threshold.

The transaction rules are retained as a baseline. Do not silently change their precedence or patterns as part of model training. A future Android integration must call `classifyTransaction` first and invoke ML only when it returns no transaction result.

## Important status

The repository does not yet contain a trained production model. Do not replace the current classifier with an unvalidated model. The 360 starter rows are authored synthetic development examples, not representative real Indian SMS data. See `data/DATASET_STATUS.md` for provenance and limitations.

## Model labels

The AWE model has exactly three labels:

- `PERSONAL`
- `SERVICES`
- `PROMOTIONS`

Do not put Transactions or Uncategorized into this model's training labels. Do not treat all non-spam messages as Personal. When the model cannot confidently choose one of its three labels, Android must use Uncategorized.

## Data format

CSV columns:

- `text`: message text, with personal identifiers removed
- `label`: one of the three model labels above
- `source`: provenance such as `synthetic`, `public_dataset`, or `human_curated`

Keep candidate/review-queue data separate. Files with labels such as `*_CANDIDATE` and `requires_manual_review` are not approved training data.

## Build a dataset

1. Add appropriately licensed public Indian SMS examples and carefully reviewed, anonymised human-curated examples.
2. Aim for at least 100 reviewed examples per model label before the first experiment; more diverse examples are preferable.
3. Do not infer a category from a binary spam/ham label alone.
4. Keep template families and near-duplicates together when splitting data.
5. Maintain a separate untouched test set. Do not tune against it.

## Train

Use Python 3.10 in an isolated environment:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python train_awe.py --input data/seed_messages.csv --output build/awe
```

The script reports validation metrics and exported model size. AWE is only accepted if the held-out SMS-specific evaluation supports it.

## Android integration gate

Only integrate `build/awe/model.tflite` after per-class precision/recall, confusion matrix, held-out evaluation, label order, model size and on-device latency have been reviewed. Keep the transaction regex baseline unchanged. No SMS content is sent to a training service from Android.
