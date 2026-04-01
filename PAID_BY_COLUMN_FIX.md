# Paid By Column Fix - Show Primary Payor

## Issue
In the transaction items table (fragment_transaction.xml), the "Paid by" column was only showing the first payor from the database, which might not be the person who paid the most or the primary payor for that item.

## Root Cause
The `itemPayorMap` was using `firstOrNull()` which returned the first payor record regardless of who actually paid the most:

```kotlin
// OLD CODE - Shows first payor in database order
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    itemId to (payorsByItem[itemId]?.firstOrNull()?.let { usersById[it.userId] } ?: "-")
}
```

This could show someone who paid ₱0 instead of the person who actually paid for the item.

## Solution
Updated the logic to show the payor who paid the most for each item:

```kotlin
// NEW CODE - Shows payor who paid the most
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorForItem = payorsByItem[itemId]
        ?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }  // Only payors who paid
        ?.maxByOrNull { it.currentAmountPaid ?: 0.0 }     // Get who paid the most
        ?.let { usersById[it.userId] } ?: "-"             // Get their username
    itemId to payorForItem
}
```

## Changes Made

### 1. HomeFragment.kt
**Location**: `fetchRecentTransactions()` method

**Before**:
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    itemId to (payorsByItem[itemId]?.firstOrNull()?.let { usersById[it.userId] } ?: "-")
}
```

**After**:
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorForItem = payorsByItem[itemId]
        ?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }
        ?.maxByOrNull { it.currentAmountPaid ?: 0.0 }
        ?.let { usersById[it.userId] } ?: "-"
    itemId to payorForItem
}
```

### 2. TransactionsFragment.kt
**Location**: `fetchTransactionsInRange()` method

**Before**:
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorName = payorsByItem[itemId]?.firstOrNull()?.let { usersById[it.userId] } ?: "-"
    itemId to payorName
}
```

**After**:
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorForItem = payorsByItem[itemId]
        ?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }
        ?.maxByOrNull { it.currentAmountPaid ?: 0.0 }
        ?.let { usersById[it.userId] } ?: "-"
    itemId to payorForItem
}
```

## Logic Breakdown

1. **Get payors for item**: `payorsByItem[itemId]` - Gets all payor records for this item
2. **Filter who paid**: `?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }` - Only include payors who actually paid
3. **Get primary payor**: `?.maxByOrNull { it.currentAmountPaid ?: 0.0 }` - Select the one who paid the most
4. **Get username**: `?.let { usersById[it.userId] }` - Convert user ID to username
5. **Default value**: `?: "-"` - Show "-" if no payors found

## Examples

### Single Payor
- Alice paid ₱100 for groceries
- **Display**: "Alice"

### Multiple Payors - Clear Primary
- Alice paid ₱80, Bob paid ₱20 for electricity
- **Display**: "Alice" (paid the most)

### Multiple Payors - Equal Split
- Alice paid ₱50, Bob paid ₱50 for rent
- **Display**: "Alice" or "Bob" (either one, both paid equally)

### No Payment
- No one has paid yet
- **Display**: "-"

## Benefits

1. **Shows Primary Payor**: Displays the person who contributed the most to each item
2. **Accurate Representation**: Better reflects who actually paid for the item
3. **Clean Display**: Single name per item, not cluttered with multiple names
4. **Logical Priority**: If multiple people paid, shows the main contributor

## Testing Scenarios

1. **Single Payor Item**
   - One person pays ₱100
   - Verify: Shows that person's name

2. **Multiple Payors - Unequal**
   - Alice pays ₱70, Bob pays ₱30
   - Verify: Shows "Alice"

3. **Multiple Payors - Equal**
   - Alice pays ₱50, Bob pays ₱50
   - Verify: Shows one of them (deterministic based on max)

4. **No Payment**
   - Item exists but no one paid
   - Verify: Shows "-"

5. **Partial Payments**
   - Alice paid ₱30, Bob paid ₱0, Charlie paid ₱20
   - Verify: Shows "Alice" (highest non-zero amount)
