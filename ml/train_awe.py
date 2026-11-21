#!/usr/bin/env python3
"""Train the TidySms five-label Average Word Embedding text classifier."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

LABELS = ["PERSONAL", "TRANSACTIONS", "SERVICES", "PROMOTIONS", "UNCATEGORISED"]
MIN_EXAMPLES_PER_LABEL = 100


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def load_dataset(path: Path) -> pd.DataFrame:
    data = pd.read_csv(path).fillna("")
    required = {"text", "label", "source"}
    missing = required.difference(data.columns)
    if missing:
        raise ValueError(f"Missing CSV columns: {sorted(missing)}")

    data["text"] = data["text"].astype(str).str.strip()
    data["label"] = data["label"].astype(str).str.strip().str.upper()
    data = data[(data["text"] != "") & (data["label"] != "")]
    unknown = sorted(set(data["label"]) - set(LABELS))
    if unknown:
        raise ValueError(f"Unknown labels: {unknown}")

    counts = data["label"].value_counts().to_dict()
    insufficient = {label: counts.get(label, 0) for label in LABELS
                    if counts.get(label, 0) < MIN_EXAMPLES_PER_LABEL}
    if insufficient:
        raise ValueError(
            "Not enough reviewed data to train. Need at least "
            f"{MIN_EXAMPLES_PER_LABEL} examples per label; current counts: {counts}. "
            "Synthetic starter rows are only format examples."
        )
    return data


def main() -> None:
    args = parse_args()
    data = load_dataset(args.input)
    train, validation = train_test_split(
        data, test_size=0.2, random_state=args.seed, stratify=data["label"]
    )
    args.output.mkdir(parents=True, exist_ok=True)
    train_path = args.output / "train.csv"
    validation_path = args.output / "validation.csv"
    train[["text", "label"]].to_csv(train_path, index=False)
    validation[["text", "label"]].to_csv(validation_path, index=False)

    from mediapipe_model_maker import text_classifier

    csv_params = text_classifier.CSVParams(
        text_column="text", label_column="label"
    )
    train_data = text_classifier.Dataset.from_csv(
        filename=str(train_path), csv_params=csv_params
    )
    validation_data = text_classifier.Dataset.from_csv(
        filename=str(validation_path), csv_params=csv_params
    )
    model_options = text_classifier.TextClassifierOptions(
        supported_model=text_classifier.SupportedModels.AVERAGE_WORD_EMBEDDING_CLASSIFIER,
        hparams=text_classifier.AverageWordEmbeddingHParams(
            epochs=args.epochs,
            batch_size=32,
            export_dir=str(args.output),
        ),
    )
    model = text_classifier.TextClassifier.create(
        train_data, validation_data, model_options
    )
    loss, accuracy = model.evaluate(validation_data)
    model.export_model()
    model.export_labels(export_dir=str(args.output))

    model_path = args.output / "model.tflite"
    report = {
        "architecture": "AVERAGE_WORD_EMBEDDING_CLASSIFIER",
        "labels": LABELS,
        "training_rows": int(len(train)),
        "validation_rows": int(len(validation)),
        "validation_loss": float(loss),
        "validation_accuracy": float(accuracy),
        "model_bytes": model_path.stat().st_size if model_path.exists() else None,
        "source_counts": data["source"].value_counts().to_dict(),
        "warning": "Validation split is not a substitute for an untouched test set.",
    }
    (args.output / "training_report.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8"
    )
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
