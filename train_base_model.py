#!/usr/bin/env python3
"""
train_base_model.py
───────────────────
Trains a lightweight transaction classifier and exports it as base_model.tflite.

USAGE
  pip install tensorflow numpy
  python train_base_model.py \
      --dataset  app/src/main/assets/merchant_dataset.json \
      --output   app/src/main/assets/base_model.tflite

The output file must be placed in app/src/main/assets/ so the Android app
can load it via TFLiteTransactionClassifier.

FEATURE LAYOUT (must match TransactionFeatureExtractor.java)
  [0-7]  Merchant bigram hash buckets   (8 floats)
  [8]    Log-normalised amount           (1 float)
  [9-10] Day-of-week sin/cos            (2 floats)
  [11-12] Day-of-month sin/cos          (2 floats)
  [13-14] Hour-of-day sin/cos           (2 floats)
  [15-19] Payment method one-hot        (5 floats)
  TOTAL: 20 floats

CATEGORY LABELS (must match TFLiteTransactionClassifier.java CATEGORY_LABELS)
  Index 0-19 as listed in CATEGORY_LABELS below.
"""

import argparse
import json
import math
import random
import numpy as np

# ── Category definitions (must stay in sync with TFLiteTransactionClassifier.java) ──
CATEGORY_LABELS = [
    "Food", "Groceries", "Transport", "Fuel", "Travel",
    "Shopping", "Rent", "Bills", "Entertainment", "Health",
    "Medicine", "Education", "Fitness", "Investment", "Gifts",
    "Salary", "Cashback", "Investment Return", "Refund", "Others"
]

# Map dataset keys → CATEGORY_LABELS indices
DATASET_KEY_MAP = {
    "Food":           0, "Groceries":  1, "Transport":   2, "Fuel":     3, "Travel": 4,
    "Shopping":       5, "Rent":       6, "Bills":       7, "Entertainment": 8,
    "Health":         9, "Medicine":  10, "Education":  11, "Fitness":  12,
    "Investment":    13, "Gifts":     14,
}

FEATURE_SIZE  = 20
NUM_CLASSES   = len(CATEGORY_LABELS)
MERCHANT_BUCKETS = 8

# ── Typical amount ranges per category (₹) for synthetic data augmentation ──
# Format: (min, max, typical_hour_range)
CATEGORY_AMOUNT_HINTS = {
    0:  (30,   800,   [7, 8, 12, 13, 19, 20, 21]),   # Food
    1:  (100, 3000,   [9, 10, 17, 18, 19]),           # Groceries
    2:  (20,   800,   list(range(7, 22))),             # Transport
    3:  (200, 3000,   [7, 8, 17, 18]),                # Fuel
    4:  (500, 30000,  list(range(6, 22))),             # Travel
    5:  (200, 10000,  [10, 11, 12, 15, 16, 17, 18]),  # Shopping
    6:  (5000,50000,  [1, 2, 3, 4, 5]),               # Rent — first days of month
    7:  (100, 5000,   [9, 10, 11, 14, 15]),            # Bills
    8:  (100, 1500,   [14, 15, 16, 19, 20, 21, 22]),  # Entertainment
    9:  (200, 10000,  [9, 10, 11]),                    # Health
    10: (50,  2000,   [9, 10, 11, 14, 15, 16]),       # Medicine
    11: (500, 30000,  [9, 10, 11, 14, 15]),            # Education
    12: (500, 3000,   [6, 7, 17, 18, 19]),             # Fitness
    13: (500, 50000,  [9, 10, 11, 14, 15]),            # Investment
    14: (100, 5000,   [10, 11, 12, 14, 15]),           # Gifts
    15: (20000,200000,[1, 2, 3]),                      # Salary — early month
    16: (10,  500,    list(range(8, 22))),             # Cashback
    17: (100, 10000,  [9, 10, 11, 14]),                # Investment Return
    18: (50,  5000,   [9, 10, 11, 14, 15]),            # Refund
    19: (50,  5000,   list(range(8, 20))),             # Others
}


# ── Feature extraction (mirrors TransactionFeatureExtractor.java) ──────────

def extract_merchant_features(merchant: str) -> list:
    counts = [0] * MERCHANT_BUCKETS
    m = merchant.lower().strip()
    for i in range(len(m) - 1):
        bigram_hash = (ord(m[i]) * 31 + ord(m[i + 1])) & 0x7FFFFFFF
        counts[bigram_hash % MERCHANT_BUCKETS] += 1
    max_c = max(counts) if max(counts) > 0 else 1
    return [c / max_c for c in counts]


def log_normalise_amount(amount: float) -> float:
    log_max = math.log1p(100_000.0)
    return min(1.0, math.log1p(abs(amount)) / log_max)


def cyclic(value: float, period: float):
    return math.sin(2 * math.pi * value / period), math.cos(2 * math.pi * value / period)


def extract_features(merchant: str, amount: float, timestamp_ms: float,
                      payment_method: str = "upi") -> list:
    f = [0.0] * FEATURE_SIZE

    # [0-7] merchant hash buckets
    mf = extract_merchant_features(merchant)
    f[0:8] = mf

    # [8] amount
    f[8] = log_normalise_amount(amount)

    # [9-14] time (from mock timestamp_ms expressed as seconds since unix epoch)
    import datetime
    dt = datetime.datetime.fromtimestamp(timestamp_ms / 1000.0)
    dow = dt.weekday()       # 0 = Monday
    dom = dt.day - 1         # 0-30
    hod = dt.hour            # 0-23

    f[9],  f[10] = cyclic(dow, 7)
    f[11], f[12] = cyclic(dom, 31)
    f[13], f[14] = cyclic(hod, 24)

    # [15-19] payment one-hot
    pm = payment_method.lower()
    if "upi"    in pm: f[15] = 1.0
    elif "credit" in pm: f[16] = 1.0
    elif "debit"  in pm: f[17] = 1.0
    elif "cash"   in pm: f[18] = 1.0
    else:                f[19] = 1.0   # bank / other

    return f


# ── Dataset builder ──────────────────────────────────────────────────────

def build_dataset(dataset_path: str, augment_factor: int = 30):
    """
    Load merchant_dataset.json and generate synthetic (features, label) pairs.
    Each merchant name is augmented by varying amount, time, and payment method.
    """
    with open(dataset_path) as fh:
        raw = json.load(fh)
    merchants_by_category = raw["merchants"]

    X, y = [], []
    import datetime, time

    payments = ["upi", "credit card", "debit card", "cash", "bank transfer"]
    base_epoch_ms = time.time() * 1000

    for cat_name, merchants in merchants_by_category.items():
        cat_idx = DATASET_KEY_MAP.get(cat_name)
        if cat_idx is None:
            continue

        hints = CATEGORY_AMOUNT_HINTS[cat_idx]
        amt_min, amt_max, hours = hints

        for merchant in merchants:
            for _ in range(augment_factor):
                amount  = random.uniform(amt_min, amt_max)
                hour    = random.choice(hours)
                dom_val = random.randint(1, 28)
                # Construct a mock timestamp with the chosen day/hour
                dt = datetime.datetime(2024, random.randint(1, 12), dom_val,
                                        hour, random.randint(0, 59))
                ts_ms   = dt.timestamp() * 1000
                payment = random.choice(payments)

                feats = extract_features(merchant, amount, ts_ms, payment)
                X.append(feats)
                y.append(cat_idx)

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int32)


# ── Train and export ─────────────────────────────────────────────────────

def train_and_export(dataset_path: str, output_path: str):
    print(f"Loading dataset from: {dataset_path}")
    X, y = build_dataset(dataset_path)
    print(f"Dataset size: {X.shape[0]} samples, {X.shape[1]} features, {NUM_CLASSES} classes")

    # Shuffle
    idx = np.random.permutation(len(X))
    X, y = X[idx], y[idx]
    split = int(0.85 * len(X))
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    import tensorflow as tf

    # Model: 20 → 128 → 64 → 20
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(FEATURE_SIZE,)),
        tf.keras.layers.Dense(128, activation="relu"),
        tf.keras.layers.Dropout(0.25),
        tf.keras.layers.Dense(64, activation="relu"),
        tf.keras.layers.Dropout(0.15),
        tf.keras.layers.Dense(NUM_CLASSES),          # Raw logits (no softmax — TFLite adds it)
    ])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss=tf.keras.losses.SparseCategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )

    print("Training model…")
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=40,
        batch_size=64,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
            tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=3),
        ],
        verbose=1,
    )

    val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
    print(f"\nValidation accuracy: {val_acc * 100:.1f}%  (loss={val_loss:.4f})")

    # Convert to TFLite (float32 — compatible with all Android API levels)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]   # dynamic range quantization
    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"\nExported: {output_path}  ({size_kb:.1f} KB)")
    print("\nNEXT STEPS:")
    print("  1. Copy the .tflite file to app/src/main/assets/base_model.tflite")
    print("  2. Rebuild the Android project — TFLiteTransactionClassifier will")
    print("     load it automatically and fall back gracefully if not found.")
    print("  3. To retrain later with your own user feedback, export records:")
    print("     SELECT * FROM user_feedback → CSV → augment dataset → re-run this script")

    return val_acc


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train SpendTracker ML model")
    parser.add_argument("--dataset", default="app/src/main/assets/merchant_dataset.json")
    parser.add_argument("--output",  default="app/src/main/assets/base_model.tflite")
    parser.add_argument("--seed",    type=int, default=42)
    args = parser.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)

    acc = train_and_export(args.dataset, args.output)
    if acc < 0.70:
        print("\nWARNING: Validation accuracy below 70%. Consider:")
        print("  - Adding more merchant entries to merchant_dataset.json")
        print("  - Increasing --augment_factor (default 30)")
