# Compilation Error Fixes - Transaction Payors Schema Update

## Files Fixed

### 1. RecentTransactionAdapter.kt
**Issues Fixed:**
- Line ~290: Changed `set("amount", itemPaidAmount)` to `set("current_amount_paid", itemPaidAmount)`
- Line ~305: Updated `TransactionPayorInsert` to use new schema:
  - Added `initialAmountPaid = itemPaidAmount`
  - Changed `amount` to `currentAmountPaid = itemPaidAmount`
  - Added `excessAmount` calculation
  - Added `status` calculation
- Added excess and status calculation logic for both UPDATE and INSERT operations

**Logic Added:**
```kotlin
val excess = if (itemPaidAmount > splitAmount) itemPaidAmount - splitAmount else 0.0
val status = when {
    itemPaidAmount == 0.0 -> 0  // unpaid
    itemPaidAmount >= splitAmount -> 1  // settled
    else -> 2  // pending
}
```

### 2. HomeFragment.kt
**Issues Fixed:**
- Line ~280: Changed `payors.filter { it.userId == uid }.sumOf { it.amount }` to `.sumOf { it.currentAmountPaid }`
- Line ~282: Changed `payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.amount } }` to `.sumOf { it.currentAmountPaid }`
- Line ~175: Changed `paid = userPayorsByTx[txId]?.sumOf { it.amount }` to `.sumOf { it.currentAmountPaid }`

### 3. TransactionsFragment.kt
**Issues Fixed:**
- Line ~340: Changed `payors.filter { it.userId == uid }.sumOf { it.amount }` to `.sumOf { it.currentAmountPaid }`
- Line ~410: Changed `payors.groupBy { it.userId }.mapValues { e -> e.value.sumOf { it.amount } }` to `.sumOf { it.currentAmountPaid }`

## Summary of Changes

### Old Schema Reference (REMOVED):
```kotlin
TransactionPayorTable.amount  // No longer exists
TransactionPayorInsert.amount // No longer exists
```

### New Schema Reference (ADDED):
```kotlin
TransactionPayorTable.initialAmountPaid  // Immutable
TransactionPayorTable.currentAmountPaid  // Mutable
TransactionPayorTable.excessAmount       // Calculated
TransactionPayorTable.status             // Calculated

TransactionPayorInsert.initialAmountPaid
TransactionPayorInsert.currentAmountPaid
TransactionPayorInsert.excessAmount
TransactionPayorInsert.transactionItemsId
TransactionPayorInsert.status
```

## Key Points

1. **Reading Data**: All queries now use `currentAmountPaid` instead of `amount`
2. **Updating Data**: Updates now set `current_amount_paid`, `excess_amount`, and `status`
3. **Inserting Data**: Inserts now provide all 5 required fields with proper calculations
4. **Status Logic**: 0=unpaid, 1=settled, 2=pending
5. **Excess Logic**: Only positive values (paid - split), else 0

## Build Status

All compilation errors related to the `transaction_payors` schema change have been resolved. The project should now build successfully.

## Testing Required

1. View existing transactions (HomeFragment, TransactionsFragment)
2. Edit transaction payment amounts (RecentTransactionAdapter)
3. Create new transactions (MultiTransactionActivity)
4. Verify status calculations are correct
5. Verify excess amounts are calculated properly
6. Check that initial_amount_paid remains unchanged after edits
