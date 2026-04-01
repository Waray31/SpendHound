# Transaction Payors Fix - Only Insert Actual Payors

## Issue
The `transaction_payors` table was inserting ALL group members for each item, even those who didn't pay. This caused incorrect amount calculations in the payors section.

### Example of the Bug:
**Transaction with 3 items**:
- Item 1: mon02 paid ₱10
- Item 2: mon02 paid ₱10  
- Item 3: mon02 paid ₱10

**Expected**: mon02 paid ₱30 total
**Actual Bug**: mon02 paid ₱90 (₱30 × 3 items)

This happened because mon02 was inserted 3 times (once per item) with ₱30 each time, then the UPDATE query updated all 3 records, resulting in ₱90 total.

### User's Example:
**Input**:
- mon02 paid ₱30
- mon04 paid ₱60
- mona paid ₱360
- mon09 paid ₱0

**Expected Display**:
- mon02: 30/112.50
- mon04: 60/112.50
- mona: 360/112.50
- mon09: 0/112.50

**Actual Bug Display**:
- mon02: 90/112.50 (30 × 3)
- mon04: 180/112.50 (60 × 3)
- mona: 337.50/112.50 (wrong calculation)
- mon09: 0/112.50

## Root Cause

### Old Logic (WRONG):
```kotlin
// For EACH item, insert ALL group members
val allMemberRecords = groupMembers.map { member ->
    val payorEntry = entry.payors.find { it.userId == member.id }
    val initialPaid = payorEntry?.amount ?: 0.0
    
    TransactionPayorInsert(
        transactionId = txId,
        userId = member.id!!,
        initialAmountPaid = initialPaid,
        currentAmountPaid = 0.0,  // Set to 0, will update later
        excessAmount = 0.0,
        transactionItemsId = itemId,
        status = 0
    )
}
client.postgrest.from("transaction_payors").insert(allMemberRecords)

// Later: Update ALL records for each user
client.postgrest.from("transaction_payors").update(
    TransactionPayorUpdate(
        currentAmountPaid = currentPaid,  // This updates ALL records!
        excessAmount = excess,
        status = status
    )
) {
    filter {
        eq("transaction_id", txId)
        eq("user_id", member.id!!)
    }
}
```

**Problem**: 
1. Inserts 4 members × 3 items = 12 records
2. UPDATE query updates all 3 records per user
3. When displaying, sums all 3 records per user → multiplied amounts

## Solution

### New Logic (CORRECT):
```kotlin
// For EACH item, insert ONLY payors who actually paid
val payorRecords = entry.payors
    .filter { it.amount > 0.0 }  // Only payors who paid
    .map { payor ->
        TransactionPayorInsert(
            transactionId = txId,
            userId = payor.userId,
            initialAmountPaid = payor.amount,
            currentAmountPaid = payor.amount,  // Set to actual amount for this item
            excessAmount = 0.0,  // Will calculate after all items
            transactionItemsId = itemId,
            status = 0
        )
    }

if (payorRecords.isNotEmpty()) {
    client.postgrest.from("transaction_payors").insert(payorRecords)
}

// Later: Update only excess and status (not current_amount_paid)
client.postgrest.from("transaction_payors").update(
    mapOf(
        "excess_amount" to excess,
        "status" to status
    )
) {
    filter {
        eq("transaction_id", txId)
        eq("user_id", userId)
    }
}
```

**Benefits**:
1. Only inserts payors who actually paid (amount > 0)
2. Sets `current_amount_paid` to actual amount per item immediately
3. UPDATE only modifies `excess_amount` and `status`, not amounts
4. Correct totals when summing across items

## Changes Made

### MultiTransactionRepository.kt

**Step 3 - Insert Payors**:

**Before**:
```kotlin
// Insert ALL group members
val allMemberRecords = groupMembers.map { member ->
    val payorEntry = entry.payors.find { it.userId == member.id }
    val initialPaid = payorEntry?.amount ?: 0.0
    
    TransactionPayorInsert(
        transactionId = txId,
        userId = member.id!!,
        initialAmountPaid = initialPaid,
        currentAmountPaid = 0.0,
        excessAmount = 0.0,
        transactionItemsId = itemId,
        status = 0
    )
}
client.postgrest.from("transaction_payors").insert(allMemberRecords)
```

**After**:
```kotlin
// Insert ONLY payors who paid
val payorRecords = entry.payors
    .filter { it.amount > 0.0 }
    .map { payor ->
        TransactionPayorInsert(
            transactionId = txId,
            userId = payor.userId,
            initialAmountPaid = payor.amount,
            currentAmountPaid = payor.amount,  // Actual amount for this item
            excessAmount = 0.0,
            transactionItemsId = itemId,
            status = 0
        )
    }

if (payorRecords.isNotEmpty()) {
    client.postgrest.from("transaction_payors").insert(payorRecords)
}
```

**Step 5 - Update Payors**:

**Before**:
```kotlin
groupMembers.forEach { member ->
    val totalInitialPaid = userTotalInitialPayments[member.id] ?: 0.0
    // ... calculations ...
    
    client.postgrest.from("transaction_payors").update(
        TransactionPayorUpdate(
            currentAmountPaid = currentPaid,  // Updates all records
            excessAmount = excess,
            status = status
        )
    ) {
        filter {
            eq("transaction_id", txId)
            eq("user_id", member.id!!)
        }
    }
}
```

**After**:
```kotlin
userTotalInitialPayments.forEach { (userId, totalInitialPaid) ->
    // ... calculations ...
    
    // Update only excess and status, not current_amount_paid
    client.postgrest.from("transaction_payors").update(
        mapOf(
            "excess_amount" to excess,
            "status" to status
        )
    ) {
        filter {
            eq("transaction_id", txId)
            eq("user_id", userId)
        }
    }
}
```

## Data Flow

### Creating Transaction with 3 Items

**Input**:
- Item 1 (₱100): mon02 pays ₱30
- Item 2 (₱150): mon04 pays ₱60
- Item 3 (₱200): mona pays ₱360

**Step 1**: Insert transaction (total = ₱450)

**Step 2-4**: For each item:
- Insert transaction_item
- Insert transaction_payors (ONLY who paid):
  - Item 1: mon02 (₱30)
  - Item 2: mon04 (₱60)
  - Item 3: mona (₱360)
- Insert transaction_splits (ALL members)

**Step 5**: Calculate and update excess/status:
- Total split per member: ₱450 / 4 = ₱112.50
- mon02: paid ₱30, excess = 0, status = pending
- mon04: paid ₱60, excess = 0, status = pending
- mona: paid ₱360, excess = ₱247.50, status = settled
- mon09: not in payors table (didn't pay)

**Display in Payors Section**:
- mon02: 30/112.50 ✓
- mon04: 60/112.50 ✓
- mona: 360/112.50 ✓
- mon09: 0/112.50 ✓

## Benefits

1. **Correct Amounts**: No more multiplication of amounts
2. **Cleaner Data**: Only stores actual payment records
3. **Better Performance**: Fewer database records
4. **Accurate Display**: Payors section shows correct totals
5. **Logical Structure**: transaction_payors only contains who actually paid

## Testing Scenarios

### Scenario 1: Single Item, Single Payor
- Item: ₱100
- Alice pays ₱100
- **Result**: 1 record in transaction_payors
- **Display**: Alice 100/100

### Scenario 2: Single Item, Multiple Payors
- Item: ₱100
- Alice pays ₱60, Bob pays ₱40
- **Result**: 2 records in transaction_payors
- **Display**: Alice 60/50, Bob 40/50

### Scenario 3: Multiple Items, Same Payor
- Item 1: ₱100, Alice pays ₱100
- Item 2: ₱50, Alice pays ₱50
- **Result**: 2 records in transaction_payors (one per item)
- **Display**: Alice 150/150 (sum of both)

### Scenario 4: Multiple Items, Different Payors
- Item 1: ₱100, Alice pays ₱100
- Item 2: ₱50, Bob pays ₱50
- **Result**: 2 records in transaction_payors
- **Display**: Alice 100/75, Bob 50/75

### Scenario 5: User Didn't Pay
- Item: ₱100
- Alice pays ₱100, Bob pays ₱0
- **Result**: 1 record in transaction_payors (only Alice)
- **Display**: Alice 100/50, Bob 0/50 (Bob shown from splits, not payors)
