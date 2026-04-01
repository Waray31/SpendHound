# Fix: Add Transaction Button Not Displaying in Single Payor Mode

## Issue
When adding multiple expense transactions in single payor mode, the "Add Transaction" button was not displayed even when all inputs were filled, unless the user switched to multiple payor mode.

## Root Cause
1. In single payor mode, when a global payor was selected, the payor amounts were not being set to match the transaction amounts
2. The validation logic checked if `totalPaid == transactionAmount`, but payors had `amount = 0.0`
3. Validation was not triggered after global payor selection or amount changes

## Files Modified

### 1. MultiTransactionViewModel.kt
**Function:** `updateGlobalPayors()`

**Before:**
```kotlin
fun updateGlobalPayors(selectedPayors: List<PayorEntry>) {
    val currentList = _transactions.value.map { it.copy(payors = selectedPayors.toMutableList()) }
    _transactions.value = currentList
}
```

**After:**
```kotlin
fun updateGlobalPayors(selectedPayors: List<PayorEntry>) {
    val currentList = _transactions.value.map { transaction ->
        // In single payor mode, set the payor amount to the transaction amount
        val payorsWithAmounts = selectedPayors.map { payor ->
            payor.copy(amount = transaction.amount)
        }.toMutableList()
        transaction.copy(payors = payorsWithAmounts)
    }
    _transactions.value = currentList
}
```

**Change:** Now sets each payor's amount to equal the transaction amount when updating global payors.

### 2. MultiTransactionActivity.kt
**Function:** `setupListeners()` - Global Payor Spinner

**Before:**
```kotlin
binding.spinnerGlobalPayor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        val member = currentMembers.getOrNull(pos)
        if (member != null && !viewModel.isMultiplePayorsMode.value) {
            viewModel.updateGlobalPayors(listOf(PayorEntry(member.id!!, member.username!!)))
        }
    }
    override fun onNothingSelected(p0: AdapterView<*>?) {}
}
```

**After:**
```kotlin
binding.spinnerGlobalPayor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
        val member = currentMembers.getOrNull(pos)
        if (member != null && !viewModel.isMultiplePayorsMode.value) {
            viewModel.updateGlobalPayors(listOf(PayorEntry(member.id!!, member.username!!)))
            validateSubmission()  // ADDED
        }
    }
    override fun onNothingSelected(p0: AdapterView<*>?) {}
}
```

**Change:** Added `validateSubmission()` call after updating global payors to trigger button visibility check.

### 3. MultiTransactionAdapter.kt
**Function:** `bind()` - Amount TextWatcher

**Before:**
```kotlin
amountWatcher = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        entry.amount = s.toString().toDoubleOrNull() ?: 0.0
        if (!expandedSplits.contains(position) && entry.payors.size == 1) {
            entry.payors[0].amount = entry.amount
        }
        if (isMultiplePayorsMode) updateRemainingAmount(entry)
        onAmountChanged()
    }
}
```

**After:**
```kotlin
amountWatcher = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {
        entry.amount = s.toString().toDoubleOrNull() ?: 0.0
        if (!expandedSplits.contains(position) && entry.payors.size == 1) {
            entry.payors[0].amount = entry.amount
        }
        if (isMultiplePayorsMode) updateRemainingAmount(entry)
        onAmountChanged()
        onValidationChanged()  // ADDED
    }
}
```

**Change:** Added `onValidationChanged()` call after amount changes to trigger button visibility check.

## Validation Logic
The `validateSubmission()` function checks:
1. Transactions list is not empty
2. Title is filled (for multi-transaction mode)
3. All transaction amounts > 0
4. All categories are selected
5. **Total paid equals transaction amount** (this was failing before)

## Flow After Fix

### Single Payor Mode:
1. User selects a group → Members loaded
2. User selects global payor from spinner
3. `updateGlobalPayors()` is called → Sets payor amounts = transaction amounts
4. `validateSubmission()` is called → Checks if totalPaid == amount
5. Button becomes visible ✅

### When Amount Changes:
1. User enters amount in transaction
2. Amount TextWatcher triggers
3. If single payor exists, updates payor amount
4. `onValidationChanged()` is called
5. Button visibility updates ✅

## Testing Scenarios

### Scenario 1: Add Single Transaction
1. Select group
2. Select global payor
3. Enter amount
4. Select category
5. **Expected:** Button appears immediately

### Scenario 2: Add Multiple Transactions
1. Select group
2. Enter title
3. Select global payor
4. Add multiple items with amounts and categories
5. **Expected:** Button appears when all valid

### Scenario 3: Change Amount After Payor Selection
1. Select global payor
2. Enter amount
3. Change amount
4. **Expected:** Button remains visible (payor amount updates)

## Result
✅ Button now displays correctly in single payor mode when all inputs are valid
✅ No need to switch to multiple payor mode to trigger validation
✅ Real-time validation as user fills in the form
