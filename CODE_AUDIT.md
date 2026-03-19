# SpendTracker Pro Code Audit (Stability, UI/UX, Security)

Date: 2026-03-19
Scope: Android client in this repository (`app/`)

## Executive summary

The app has a solid baseline (Room, WorkManager, runtime permissions, cleartext disabled, and non-exported activities by default), but there are several high-impact gaps that can improve reliability, trust, and maintainability quickly.

### Priority 0 (do first)
1. **Add automated tests + CI quality gates** (unit tests, migration tests, parser tests).
2. **Stop swallowing migration exceptions silently** and verify schema migrations in tests.
3. **Fix security messaging mismatch**: remove/adjust AES-256 claims unless cryptography is actually implemented in this codebase.
4. **Correct destructive action UX mismatch**: “Delete all local data” currently deletes transactions only.

### Priority 1
5. **Reduce hardcoded UI text and disabled lint checks** to improve localization/accessibility quality.
6. **Refactor duplicate dashboard logic in `MainActivity` and `HomeFragment`** to reduce bugs and divergence.
7. **Gate notification scheduling by user preference at startup** to avoid behavior surprises.

### Priority 2
8. **Upgrade key AndroidX dependencies and add dependency scanning**.
9. **Harden privacy controls around SMS permissions and transparency**.

---

## Findings and recommendations

## 1) Stability

### 1.1 No test suite present (high risk)
- I did not find any test source sets (`app/src/test` / `app/src/androidTest`) in this repo.
- Result: parser regressions, migration breaks, and UI state issues can slip into releases.

**Recommendation**
- Add at least:
  - `SmsParser` / `BankAwareSmsParser` fixture tests.
  - Room migration tests (`MigrationTestHelper`) for versions 1→2→3→4.
  - DAO behavior tests for dedup and date-range methods.
  - A minimal smoke instrumentation test for app launch + navigation.

### 1.2 Room migrations swallow all exceptions silently (high risk)
- `MIGRATION_1_2` and `MIGRATION_2_3` wrap several SQL statements in `try/catch` with empty handlers.
- This can mask broken migrations and produce partial schema state.

**Recommendation**
- Catch only known/expected cases (e.g., duplicate column/index) and log with structured messages.
- Add migration verification tests that assert final schema exactly matches current entities.

### 1.3 Duplicate dashboard implementations increase bug surface
- `MainActivity` and `HomeFragment` contain near-duplicate UI wiring and stat update behavior.

**Recommendation**
- Consolidate to one dashboard implementation, or extract shared presenter/use-case layer.
- Keep one authoritative source of dashboard business logic.

### 1.4 Background scheduling can ignore explicit user intent
- `SplashActivity` always calls `DailySummaryWorker.schedule(this)` on launch.
- Settings provides a toggle for daily summary, but startup scheduling should honor it.

**Recommendation**
- Read `daily_summary_enabled` in startup path before scheduling.
- Keep schedule/cancel behavior centralized to avoid policy drift.

---

## 2) UI/UX

### 2.1 Hardcoded text throughout layouts and code hurts scalability
- A large amount of UI copy is hardcoded in layouts and Java code.
- Lint disables `HardcodedText` and `SetTextI18n`, which hides real product-quality issues.

**Recommendation**
- Move user-facing text to `strings.xml` incrementally (high-traffic screens first).
- Re-enable lint checks gradually and fail PRs only after baseline is cleaned.

### 2.2 Destructive action copy does not match behavior
- UI says “Delete All Local Data” and “permanently deletes all local data”.
- Current implementation deletes only transactions (`transactionDao().deleteAll()`).

**Recommendation**
- Either:
  - rename button/message to “Delete all transactions”, or
  - implement full-table purge across budgets, bills, cards, accounts, recurring, net worth.
- Add second-step confirmation text requiring exact phrase input for destructive operations.

### 2.3 Accessibility quality likely inconsistent
- Heavy use of emoji-only labels and mixed hardcoded colors may reduce readability and TalkBack clarity.

**Recommendation**
- Add content descriptions where needed, validate contrast for all text/background pairs, and test TalkBack traversal on home/settings.

---

## 3) Security & privacy

### 3.1 Security claims appear stronger than implemented code
- UI and README claim AES-256 encryption and encrypted token storage.
- In this codebase, settings state is stored with plain `SharedPreferences`; I did not find AndroidX Security Crypto usage.

**Recommendation**
- Either implement encrypted storage (`EncryptedSharedPreferences` + `MasterKey`) and document precisely what is encrypted,
- or update copy/README to avoid misleading security promises.

### 3.2 SMS access is sensitive; request timing and disclosure should be optimized
- App requests and processes `READ_SMS` and listens for `RECEIVE_SMS`.

**Recommendation**
- Keep permission request contextual and delayed until user taps scan (already partly done).
- Add explicit privacy notice screen before permission prompt:
  - what is read,
  - what is stored,
  - retention policy,
  - how to revoke/delete.

### 3.3 Receiver export posture is mostly good but should be regression-tested
- Exported receivers are permission-protected, and activities are non-exported by default.

**Recommendation**
- Add an Android security checklist in CI/release process (manifest export review, permission review, PendingIntent flags, backup policy).

---

## Suggested implementation roadmap (2 weeks)

### Week 1
- Create test harness + migration tests + parser fixture tests.
- Fix migration exception handling and add logs.
- Align destructive action UI text with actual behavior.
- Update security claims in settings/README (or implement real encrypted storage).

### Week 2
- Refactor dashboard shared logic.
- Start string externalization and re-enable one lint rule at a time.
- Add privacy disclosure UX for SMS permission.
- Upgrade dependencies and run compatibility checks.

---

## Quick wins checklist

- [ ] Add `app/src/test` with 10+ parser and migration tests.
- [ ] Replace silent catches in DB migrations.
- [ ] Fix “Delete All Local Data” behavior/copy mismatch.
- [ ] Remove or implement AES-256 claims in app + README.
- [ ] Move top 50 visible strings into `strings.xml`.
- [ ] Add CI steps: `testDebugUnitTest`, `lintDebug`, dependency audit.
