# TidySms on-device SMS classifier training

This directory contains the reproducible training path for the **TidySms-specific**
Average Word Embedding (AWE) text classifier. The exported model is intended to run
locally through MediaPipe Tasks Text on Android.

## Important status

The repository does not yet contain a trained production model. Do not replace the
current deterministic fallback classifier with an unvalidated model.

The starter examples in `data/seed_messages.csv` are synthetic development examples.
They prove the data format and help exercise the pipeline; they are not a representative
Indian SMS corpus and must not be used to claim production accuracy.

## Labels

The model uses these five labels:

- `PERSONAL`
- `TRANSACTIONS`
- `SERVICES`
- `PROMOTIONS`
- `UNCATEGORISED`

Keep transactional alerts separate from service updates. Do not label all non-spam
messages as personal. When a message is genuinely ambiguous, use UNCATEGORISED.

## Data format

CSV columns:

- `text`: message text, with personal identifiers removed
- `label`: one of the five labels above
- `source`: provenance such as `synthetic`, `public_dataset`, or `human_curated`

Only include messages whose label is supported by their content. For public datasets,
retain the original dataset name and license in the accompanying dataset notes.

## Build a dataset

1. Add appropriately licensed public Indian SMS examples and carefully reviewed,
   anonymised human-curated examples to `data/seed_messages.csv`.
2. Ensure each category has enough independent examples. The trainer currently requires
   at least 100 examples per category before it will train.
3. Avoid near-duplicate templates crossing the train/validation split. Split by source
   group or template family where possible.
4. Keep a separate untouched test set for final evaluation; do not tune against it.

## Train

Use Python 3.10 in an isolated environment:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python train_awe.py --input data/seed_messages.csv --output build/awe
```

The script reports the validation metrics and exported model size. The AWE architecture
is selected because Google's documented reference implementation is small and fast;
the actual TidySms model size and quality must be measured after training.

## Android integration gate

Only copy `build/awe/model.tflite` into the Android assets after:

- all five labels are present and correctly ordered in model metadata;
- a held-out, SMS-specific test set has been evaluated;
- per-class precision/recall and a confusion matrix have been reviewed;
- the exported TFLite model passes MediaPipe inference tests on Android;
- the model size and inference latency are measured on a real device.

Message contents must remain on-device. No SMS content is sent to a training service
from the Android app.
