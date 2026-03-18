# 💰 SpendTracker Pro

> A smart, offline-first Android expense tracker that reads your bank SMS automatically, categorizes transactions using AI keyword matching, tracks budgets in real time, and syncs your data securely to the cloud.

---

## 📱 Screenshots

| Home Dashboard | Budget Manager | Analytics | Transactions |
|---|---|---|---|
| Today & Month totals, Health Score, Quick cards | Live budget progress per category | Bar, Pie & Line charts + AI Insights | Filter by category, merchant & payment |

---

## ✨ Features

### 🔍 Automatic SMS Detection
- Reads bank & UPI SMS messages from the last 90 days on first scan
- Real-time detection via `BroadcastReceiver` — works completely **offline**
- Supports HDFC, SBI, ICICI, Axis, Kotak, Paytm, PhonePe, GPay, and 15+ banks/apps
- Duplicate detection — never imports the same transaction twice

### 🧠 AI Categorization
- 16 spending categories: Food, Groceries, Transport, Fuel, Travel, Shopping, Rent, Bills, Entertainment, Health, Medicine, Education, Fitness, Investment, Gifts, Others
- 200+ merchant keywords mapped automatically
- Smart merchant map for: Swiggy, Zomato, Zepto, BigBasket, Blinkit, DMart, JioMart, Smart Bazar, Flipkart, Amazon, Meesho, Myntra, Gullak, Zerodha, Groww, Apollo, HP/HPCL, BP/BPCL, IOCL, Uber, Ola, IRCTC, and many more
- Auto-fills category when you type a merchant name in Add Expense

### 📊 Analytics
- **7-day Bar Chart** — daily spending overview
- **Category Pie Chart** — visual breakdown by category
- **30-day Line Chart** — spending trend
- **Merchant Breakdown** — top 10 merchants with amounts
- **AI Insights** — top 3 spending categories (🥇🥈🥉), top 3 merchants, month-over-month comparison, spending predictions, day-of-week patterns

### 📋 Budget Manager
- Set per-category monthly budgets
- Live progress bars with colour coding: 🟢 Safe → 🟡 Caution → 🟠 Warning → 🔴 Exceeded
- Automatically calculates spent vs remaining from actual transactions (always in sync)
- Push notifications when budget reaches 90% or is exceeded
- Total budget summary (Total Set / Total Spent / Total Left)

### 🔄 Self-Transfer Support
- Mark any transaction as "Self-Transfer" (e.g. moving money between own accounts)
- Self-transfers are excluded from all spending totals, budgets, and analytics

### ✏️ Full Transaction Editing
- Tap any transaction to edit it (amount, merchant, category, date, payment method, notes)
- Long-press also triggers edit
- Add manual expenses with the ➕ FAB button

### 📥 CSV Import
- Import transactions from any CSV file via Settings → 📥 Import from CSV
- Auto-detects header rows (date, merchant, amount, category, payment, notes)
- Supports 6 date formats: `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`, `MM/dd/yyyy`, `dd MMM yyyy`, `dd-MMM-yyyy`
- Deduplicates against existing records automatically

### 📤 CSV Export
- Export all transactions as a CSV file and share via any app

### 🔁 Recurring Bills
- Track monthly/weekly/yearly bills
- Automatic push reminders 1 day before due date

### 💼 Net Worth Tracker
- Add assets and liabilities
- Real-time net worth calculation

### 🔒 Security
- Biometric lock (fingerprint) — toggle in Settings
- Cloud data encrypted with AES-256-CBC using per-user keys
- JWT auth tokens stored in Android `EncryptedSharedPreferences` (AES-256-GCM)
- Passwords hashed with bcrypt (cost 12) — never stored in plaintext

### ☁️ Cloud Account & Sync
- Register / login with email + password
- One-tap sync to backend — all data encrypted before storage
- Sign out and sign back in anytime — data is preserved in cloud
- Rate-limited auth (10 attempts / 15 min) to prevent brute force

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java (Android SDK 26+) |
| UI | Material Design 3, ConstraintLayout, RecyclerView, CardView |
| Local DB | Room (SQLite) with LiveData observers |
| Charts | MPAndroidChart v3.1.0 |
| Networking | OkHttp 4.12 |
| Secure Storage | AndroidX Security Crypto (EncryptedSharedPreferences) |
| Background | WorkManager, AlarmManager, BroadcastReceiver |
| Backend | Node.js + Express + better-sqlite3 |
| Auth | JWT (jsonwebtoken) + bcryptjs |
| Encryption | AES-256-CBC (per-user key derivation) |

---

## 📁 Project Structure

```
SpendTrackerPro/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/spendtracker/pro/
│           │
│           ├── ── Activities ──
│           ├── SplashActivity.java          # Launch screen + biometric gate + auth routing
│           ├── LoginActivity.java           # Email/password sign-in
│           ├── RegisterActivity.java        # New account creation
│           ├── MainActivity.java            # Home dashboard
│           ├── TransactionsActivity.java    # Full list with filters
│           ├── AnalyticsActivity.java       # Charts + AI insights
│           ├── BudgetActivity.java          # Budget manager
│           ├── NetWorthActivity.java        # Assets & liabilities
│           ├── RecurringActivity.java       # Recurring bills
│           ├── SettingsActivity.java        # Biometric, export, import, sync, logout
│           ├── AddExpenseActivity.java      # Add / Edit expense
│           │
│           ├── ── Room Entities ──
│           ├── Transaction.java
│           ├── Budget.java
│           ├── RecurringTransaction.java
│           ├── NetWorthItem.java
│           │
│           ├── ── DAOs ──
│           ├── TransactionDao.java
│           ├── BudgetDao.java
│           ├── RecurringDao.java
│           ├── NetWorthDao.java
│           ├── AppDatabase.java
│           │
│           ├── ── Core Engines ──
│           ├── CategoryEngine.java          # 16 categories, 200+ keywords, merchant map
│           ├── SmsParser.java               # Regex parser for bank SMS
│           ├── SmsImporter.java             # Bulk 90-day offline SMS import
│           ├── SmsReceiver.java             # Real-time SMS BroadcastReceiver
│           ├── InsightEngine.java           # AI insights + health score
│           ├── CsvImporter.java             # CSV file import with header detection
│           ├── CsvExporter.java             # CSV export
│           │
│           ├── ── Backend / Auth ──
│           ├── ApiClient.java               # OkHttp wrapper for backend calls
│           ├── SessionManager.java          # Encrypted JWT token storage
│           ├── SyncManager.java             # Cloud data sync
│           │
│           ├── ── Notifications ──
│           ├── NotificationHelper.java      # 4 channels + TaskStackBuilder intents
│           ├── ReminderReceiver.java        # Bill due alerts
│           ├── BootReceiver.java            # Reschedule alarms on reboot
│           │
│           └── ── Adapters ──
│               ├── TransactionAdapter.java  # Tap to edit, self-transfer badge
│               ├── BudgetAdapter.java       # Progress bar, colour status
│               ├── NetWorthAdapter.java
│               └── RecurringAdapter.java
│
└── .github/workflows/build-apk.yml         # GitHub Actions CI — auto-builds APK
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 26+ (Android 8.0 Oreo)
- JDK 17

### Build & Run

```bash
# Clone the repo
git clone https://github.com/atiwari111/SpendTrackerPro.git
cd SpendTrackerPro

# Open in Android Studio and sync Gradle, OR build from terminal:
./gradlew assembleDebug

# APK output:
# app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions (Auto Build)
Every push to `main` or `master` triggers a workflow that builds the APK automatically.
Download it from **Actions → SpendTrackerPro-APK → Artifacts**.

---

## ☁️ Backend Setup (Optional — for cloud sync)

> Without the backend, the app works fully offline. Backend is only needed for account login and cloud backup.

### Quickest option — Railway (free, no server needed)

1. Push the `SpendTrackerBackend/` folder to a GitHub repo
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Set environment variables:
   ```
   JWT_SECRET=<run: node -e "console.log(require('crypto').randomBytes(64).toString('hex'))">
   ENCRYPTION_KEY=<run: node -e "console.log(require('crypto').randomBytes(32).toString('hex'))">
   ```
4. Railway gives you a free `*.railway.app` HTTPS URL automatically
5. Paste that URL into `ApiClient.java` → `BASE_URL`

### Self-hosted (VPS)

```bash
cd SpendTrackerBackend
npm install
cp .env.example .env    # fill in JWT_SECRET and ENCRYPTION_KEY
node server.js          # runs on port 3000
```

Add nginx + Let's Encrypt for free HTTPS. See `SpendTrackerBackend/SETUP_GUIDE.md` for the full step-by-step.

### Update `BASE_URL` in the app

```java
// ApiClient.java
public static final String BASE_URL = "https://your-domain.com";
// For emulator local testing:  "http://10.0.2.2:3000"
// For device on same WiFi:     "http://192.168.x.x:3000"
```

---

## 🔐 Permissions

| Permission | Why it's needed |
|---|---|
| `READ_SMS` | Read existing bank SMS to import transactions |
| `RECEIVE_SMS` | Detect new transactions in real time |
| `POST_NOTIFICATIONS` | Budget alerts, bill reminders, spend notifications |
| `INTERNET` | Cloud sync and account login |
| `ACCESS_NETWORK_STATE` | Check connectivity before sync |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Biometric lock screen |
| `SCHEDULE_EXACT_ALARM` | Recurring bill reminders |
| `RECEIVE_BOOT_COMPLETED` | Reschedule reminders after phone restart |
| `WRITE_EXTERNAL_STORAGE` (≤ API 28) | CSV export |

---

## 🗺️ Roadmap

- [ ] UPI deep-link payment integration
- [ ] Dark / light theme toggle
- [ ] Widget for home screen balance
- [ ] Multi-currency support
- [ ] Bank statement PDF import
- [ ] Google Drive / Dropbox backup
- [ ] Shared household expenses

---

## 🤝 Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m 'Add your feature'`
4. Push: `git push origin feature/your-feature`
5. Open a Pull Request

---

## 📄 License

```
MIT License — free to use, modify, and distribute.
```

---

## 👤 Author

**Animesh Tiwari**
GitHub: [@atiwari111](https://github.com/atiwari111)
Linkdin : https://www.linkedin.com/in/animeshtiwari/

---

> Built with ❤️ for smart personal finance management
