// ═══════════════════════════════════════════════════════════════════════════
// AddExpenseActivity.java — DIFF for HybridTransactionAgent
//
// If you applied the previous SmartTransactionAgent patch, this REPLACES it.
// If starting fresh, apply all 5 changes below directly.
// ═══════════════════════════════════════════════════════════════════════════


// ══════════════════════════════════════════════════════════════════
// CHANGE 1 — Replace agent field type near top of class
// ══════════════════════════════════════════════════════════════════

// FIND (previous patch used SmartTransactionAgent):
    private AppDatabase db;
    private SmartTransactionAgent agent;   // remove this if present from prev patch
    private Transaction editingTransaction = null;

// REPLACE WITH:
    private AppDatabase db;
    private HybridTransactionAgent agent;   // ← Hybrid (TFLite + frequency)
    private Transaction editingTransaction  = null;

    // Track whether the user explicitly overrode the auto-suggested category
    // (already exists in AddExpenseActivity — do NOT add duplicate field)
    // private boolean userOverrodeCategory = false;


// ══════════════════════════════════════════════════════════════════
// CHANGE 2 — Initialise HybridTransactionAgent in onCreate
// ══════════════════════════════════════════════════════════════════

// FIND:
        db = AppDatabase.getInstance(this);

// REPLACE WITH:
        db    = AppDatabase.getInstance(this);
        agent = HybridTransactionAgent.getInstance(this);


// ══════════════════════════════════════════════════════════════════
// CHANGE 3 — Replace TextWatcher afterTextChanged with hybrid suggestions
// ══════════════════════════════════════════════════════════════════

// FIND (the entire afterTextChanged block inside etMerchant.addTextChangedListener):
            @Override
            public void afterTextChanged(Editable s) {
                if (editingTransaction != null) return;
                String typed = s.toString().trim();
                if (typed.length() < 3) return;

                String suggested = CategoryEngine.autoCategory(typed);
                if (!suggested.equals("💼 Others")) {
                    autoSuggestedCategory = suggested;
                    userOverrodeCategory  = false;
                    String[] catList = CategoryEngine.getCategoryNames();
                    for (int i = 0; i < catList.length; i++) {
                        if (catList[i].equals(suggested)) {
                            spCategory.setSelection(i);
                            break;
                        }
                    }
                }
            }

// REPLACE WITH:
            @Override
            public void afterTextChanged(Editable s) {
                if (editingTransaction != null) return;
                String typed = s.toString().trim();
                if (typed.length() < 2) return;

                // Parse the amount currently in the field (may be empty → 0)
                double currentAmount = 0;
                try {
                    String amtStr = etAmount.getText() != null
                                    ? etAmount.getText().toString().trim() : "";
                    if (!amtStr.isEmpty()) currentAmount = Double.parseDouble(amtStr);
                } catch (NumberFormatException ignored) {}

                String currentPayment = spPayment.getSelectedItem() != null
                                        ? spPayment.getSelectedItem().toString() : "UPI";

                // ── Hybrid agent: TFLite + frequency map ────────────────
                agent.suggestAsync(typed, currentAmount, selectedDate, currentPayment,
                    suggestion -> {
                        if (suggestion == null || userOverrodeCategory) {
                            // No ML data — fall back to static CategoryEngine
                            String fallback = CategoryEngine.autoCategory(typed);
                            if (!fallback.equals("💼 Others") && !userOverrodeCategory) {
                                autoSuggestedCategory = fallback;
                                selectCategory(fallback);
                            }
                            return;
                        }

                        // Apply category suggestion
                        if (!userOverrodeCategory && suggestion.category != null) {
                            autoSuggestedCategory = suggestion.category;
                            selectCategory(suggestion.category);
                        }

                        // Apply payment method suggestion
                        if (suggestion.paymentMethod != null) {
                            String[] payments = {"UPI", "Credit Card", "Debit Card",
                                                 "Cash", "Bank Transfer"};
                            for (int i = 0; i < payments.length; i++) {
                                if (suggestion.paymentMethod.toLowerCase()
                                        .contains(payments[i].toLowerCase())) {
                                    spPayment.setSelection(i);
                                    break;
                                }
                            }
                        }

                        // Show avg amount as hint (only if field is empty)
                        if (suggestion.avgAmount > 0
                                && (etAmount.getText() == null
                                    || etAmount.getText().toString().isEmpty())) {
                            etAmount.setHint(String.format("~₹%.0f (avg)", suggestion.avgAmount));
                        }

                        // Show last notes as placeholder hint
                        if (suggestion.notesHint != null && !suggestion.notesHint.isEmpty()
                                && (etNotes.getText() == null
                                    || etNotes.getText().toString().isEmpty())) {
                            etNotes.setHint(suggestion.notesHint);
                        }
                    });
            }


// ══════════════════════════════════════════════════════════════════
// CHANGE 4 — Call agent.learnAsync() when saving a NEW transaction
// ══════════════════════════════════════════════════════════════════

// FIND (inside AppExecutors.db().execute() after db.transactionDao().insert(tx)):
                AppExecutors.db().execute(() -> {
                    db.transactionDao().insert(tx);
                    if (!isSelf) {

// REPLACE WITH:
                final boolean wasCorrection = userOverrodeCategory;
                AppExecutors.db().execute(() -> {
                    db.transactionDao().insert(tx);
                    agent.learn(tx, wasCorrection);    // ← Hybrid learn (all 3 layers)
                    if (!isSelf) {


// ══════════════════════════════════════════════════════════════════
// CHANGE 5 — Call agent.learnAsync() when UPDATING an existing transaction
// ══════════════════════════════════════════════════════════════════

// FIND (inside AppExecutors.db().execute() after db.transactionDao().update(...)):
                AppExecutors.db().execute(() -> {
                    db.transactionDao().update(editingTransaction);
                    // Recalc budget

// REPLACE WITH:
                final boolean wasEdit = (originalCategory != null
                        && !originalCategory.equals(category));  // true = category was changed
                AppExecutors.db().execute(() -> {
                    db.transactionDao().update(editingTransaction);
                    agent.learn(editingTransaction, wasEdit);     // ← Hybrid learn
                    // Recalc budget


// ══════════════════════════════════════════════════════════════════
// CHANGE 6 — Add selectCategory() helper method (prevents duplicating loop)
// Add this private method inside AddExpenseActivity class, after updateDateLabel()
// ══════════════════════════════════════════════════════════════════

    /** Selects the given category in spCategory spinner without triggering the override listener. */
    private void selectCategory(String category) {
        String[] catList = CategoryEngine.getCategoryNames();
        for (int i = 0; i < catList.length; i++) {
            if (catList[i].equals(category)) {
                userOverrodeCategory = false;   // reset — this is agent-driven, not user-driven
                spCategory.setSelection(i);
                userOverrodeCategory = false;   // reset again after listener may have fired
                break;
            }
        }
    }
