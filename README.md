# SpendTracker Pro 2.1

A privacy-first personal finance Android app that automatically reads your bank SMS messages, categorises transactions using on-device machine learning, and gives you a complete picture of your spending — without sending any data to the cloud.

---

## Screenshots

> _Add screenshots here after your next build_

---

## Features

### Automatic Transaction Import
- Reads bank and UPI SMS messages in real time and on demand
- Parses transactions from all major Indian banks (HDFC, SBI, ICICI, PNB, Axis, Kotak and more)
- Detects debit, credit, UPI, NEFT, IMPS, card swipes, and ATM withdrawals
- Duplicate detection using SMS hash fingerprinting

### 3-Layer Hybrid ML Engine
| Layer | What it does | Works from day 1? |
|---|---|---|
| Frequency map | Remembers your past merchant-category pairs | Yes |
| TFLite model | 86-feature on-device classifier (18 categories) | Yes (after training) |
| Static map + NLP | Rule-based fallback for unknown merchants | Always |

All three layers run entirely on-device. No internet required for categorisation.

### 18 Spending Categories
🍽️ Cafe & Food Delivery · 🛒 Groceries · 🚗 Transport · ⛽ Fuel · ✈️ Travel · 🛍️ Shopping · 🏠 Rent · 🔌 Bills · 🎬 Entertainment · 🏥 Health · 💊 Medicine · 📚 Education · 💪 Fitness · 💰 Investment · 🎁 Gifts · 💄 Beauty & Salon · 💚 Income · 💼 Others

### Budget Tracking
- Set monthly budgets per category
- Real-time progress tracking with alerts at 90% usage
- Auto-budget generator based on past 3-month averages
- Daily spend limit streaks

### Analytics
- Week / month / custom range breakdowns
- Top merchants, category trends, month-over-month comparison
- Health score (0–100) based on spending habits
- Spending anomaly detection
- Projected month-end forecast

### Bills & Recurring
- Auto-detect recurring bills from SMS patterns
- Due-date reminders with notification alerts
- Mark bills as paid

### Credit Cards
- Track multiple cards with billing cycle awareness
- Statement amount tracking
- Utilisation percentage per card

### Bank Accounts
- Manual balance tracking across multiple bank accounts
- Total balance aggregated on home screen

### Net Worth
- Asset and liability tracking
- Syncs bank account balances automatically

### OCR Receipt Scanning
- Point your camera at any receipt
- Extracts amount and merchant name automatically
- One tap to fill the add-expense form

### Split Expense
- Split any transaction with contacts
- Tracks who owes what

### Security
- Biometric lock (fingerprint / face)
- AES-256 encrypted backup files
- All data stored locally on device

### Import / Export
- Import transactions from CSV
- Export transactions, credit cards, and bank accounts to CSV
- Full backup and restore as `.stpbak` file

### UI
- Light and dark theme with live switching
- Home screen widget (today's spend + monthly total + bank balance)
- Daily summary push notifications

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Database | Room v2.6 (SQLite), schema v7 |
| ML inference | TensorFlow Lite 2.14 |
| Charts | MPAndroidChart 3.1 |
| Camera / OCR | CameraX 1.3 + Google ML Kit Text Recognition |
| Background work | WorkManager 2.9 |
| Biometrics | AndroidX Biometric 1.1 |
| Animations | Lottie 6.4 |
| CI/CD | GitHub Actions (manual trigger) |

---

## Project Structure

```
app/src/main/
├── java/com/spendtracker/pro/
│   ├── ML Agent
│   │   ├── HybridTransactionAgent.java      — orchestrates all 3 layers
│   │   ├── TFLiteTransactionClassifier.java  — Layer 2: on-device model
│   │   ├── SmartTransactionAgent.java        — Layer 1: frequency maps
│   │   ├── TransactionFeatureExtractor.java  — 86-float feature builder
│   │   └── NlpCategorizer.java               — Layer 3: word-match fallback
│   ├── SMS Parsing
│   │   ├── BankAwareSmsParser.java
│   │   ├── SmsImporter.java
│   │   ├── SmsReceiver.java
│   │   └── BankDetector.java
│   ├── Database
│   │   ├── AppDatabase.java                  — Room DB, schema v7
│   │   ├── Transaction.java / TransactionDao.java
│   │   ├── Budget.java / BudgetDao.java
│   │   ├── CreditCard.java / CreditCardDao.java
│   │   └── BankAccount.java / BankAccountDao.java
│   └── UI
│       ├── MainActivity.java / HomeFragment.java
│       ├── AddExpenseActivity.java
│       ├── AnalyticsActivity.java / AnalyticsFragment.java
│       ├── BudgetActivity.java
│       ├── TransactionsActivity.java
│       └── ...
├── assets/
│   ├── base_model.tflite                     — trained 18-class classifier
│   ├── merchant_dataset.json                 — 400+ merchant training data
│   └── merchant_logo_map.json
└── res/
    ├── values/colors.xml                     — light theme (lavender + white)
    └── values-night/colors.xml               — dark theme (deep navy)
```

---

## ML Model

The on-device classifier uses an 86-element feature vector:

| Features | Indices | Description |
|---|---|---|
| Merchant trigrams | 0–63 | MD5-hashed char-trigram bag-of-words, L1 normalised |
| Amount bucket | 64–71 | Log-scale one-hot (₹0 / ₹50 / ₹150 / ₹300 / ₹500 / ₹1K / ₹3K+) |
| Day of week | 72–78 | One-hot (Mon–Sun) |
| Day of month | 79 | Normalised 1/31–31/31 |
| Hour of day | 80 | Normalised 0/23–23/23 |
| Payment method | 81–85 | One-hot (UPI / Credit / Debit / Cash / Bank) |

To retrain the model after updating `merchant_dataset.json`:

```bash
pip install tensorflow numpy
python train_base_model.py
# Output: app/src/main/assets/base_model.tflite
```

---

## Build & Run

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 34

### Local build
```bash
git clone https://github.com/<your-username>/SpendTrackerPro2.0.git
cd SpendTrackerPro2.0
./gradlew assembleDebug
```

### Release build (CI)
Trigger manually via GitHub Actions → **Build SpendTracker Pro APK** → **Run workflow**.

Required repository secrets:
| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_PASSWORD` | Key password |

---

## Permissions

| Permission | Reason |
|---|---|
| `READ_SMS` / `RECEIVE_SMS` | Auto-import bank transactions |
| `CAMERA` | OCR receipt scanning |
| `USE_BIOMETRIC` | App lock |
| `POST_NOTIFICATIONS` | Budget alerts and daily summary |
| `SCHEDULE_EXACT_ALARM` | Bill due-date reminders |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `READ/WRITE_EXTERNAL_STORAGE` | CSV export and backup |

---

## Database Schema

Room database — current version **7**. Migrations are handled gracefully from v1 through v7, including skip migrations (v5→v7 direct path).

---

## Changelog

### v2.1 (current)
- Added **Beauty & Salon** category
- Renamed Food → **Cafe & Food Delivery**
- Consolidated 4 income categories → single **Income** category
- Hybrid 3-layer ML engine with TFLite + on-device personalisation
- Light theme redesign — lavender background, white cards, purple typography
- Uniform 2×3 quick-action card grid on home screen
- Fixed edit-expense crash (SwitchCompat type mismatch)
- All 80+ lint warnings resolved

### v2.0
- Complete UI overhaul — neo-banking dark and light themes
- SMS auto-import with BankAwareSmsParser
- OCR receipt scanner
- Split expense feature
- Biometric lock
- Backup and restore
- Home screen widget

---

## Privacy

All processing happens on your device. SpendTracker Pro does not:
- Send your SMS messages anywhere
- Connect to any server for categorisation
- Track usage or analytics
- Show ads

Your data lives in the app's private storage and never leaves unless you explicitly export or share a backup.

---

## License

MIT License — see [LICENSE](LICENSE) for details.
