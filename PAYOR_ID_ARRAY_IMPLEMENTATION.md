# Payor ID Array Implementation

## Overview
Added a `payor_id` array column to the `transaction_items` table to directly store which users paid for each item. This simplifies the logic and makes it clearer who paid for what.

## Database Schema Change

### transaction_items Table
**New Column**: `payor_id` (array of bigint, nullable)

This column stores an array of user IDs who paid for the item. It can contain:
- Single payor: `[123]`
- Multiple payors: `[123, 456, 789]`
- No payors yet: `null` or `[]`

## Changes Made

### 1. MultiTransactionModels.kt

**Added `payor_id` field to both INSERT and SELECT models**:

```kotlin
@Serializable
data class TransactionItemInsert(
    @SerialName("transaction_id")   val transactionId: Long,
    @SerialName("amount")           val amount: Double,
    @SerialName("category")         val category: String,
    @SerialName("item_description") val itemDescription: String? = null,
    @SerialName("payor_id")         val payorId: List<Long>? = null,  // NEW
    @SerialName("created_at")       val createdAt: String? = null
)

@Serializable
data class TransactionItemFull(
    @SerialName("id")               val id: Long? = null,
    @SerialName("transaction_id")   val transactionId: Long = 0,
    @SerialName("amount")           val amount: Double = 0.0,
    @SerialName("category")         val category: String? = null,
    @SerialName("item_description") val itemDescription: String? = null,
    @SerialName("payor_id")         val payorId: List<Long>? = null,  // NEW
    @SerialName("created_at")       val createdAt: String? = null
)
```

### 2. MultiTransactionRepository.kt

**Updated `submitTransactions()` to save payor IDs**:

```kotlin
entries.forEach { entry ->
    // Extract payor IDs from payors who actually paid
    val payorIds = entry.payors
        .filter { it.amount > 0.0 }
        .map { it.userId }
    
    val itemResponse = client.postgrest.from("transaction_items").insert(
        TransactionItemInsert(
            transactionId = txId,
            amount = entry.amount,
            category = entry.category,
            itemDescription = entry.title.ifBlank { null },
            payorId = payorIds.ifEmpty { null },  // Save payor IDs
            createdAt = createdAt
        )
    ) { select() }.decodeSingle<TransactionItemFull>()
    
    // ... rest of the code
}
```

### 3. HomeFragment.kt

**Simplified `itemPayorMap` creation**:

**Before** (querying transaction_payors):
```kotlin
val payorsByItem = allPayors.groupBy { it.transactionItemsId }

val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorForItem = payorsByItem[itemId]
        ?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }
        ?.maxByOrNull { it.currentAmountPaid ?: 0.0 }
        ?.let { usersById[it.userId] } ?: "-"
    itemId to payorForItem
}
```

**After** (using payor_id from item):
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorNames = item.payorId
        ?.mapNotNull { usersById[it] }
        ?.joinToString(", ") ?: "-"
    itemId to payorNames
}
```

### 4. TransactionsFragment.kt

**Applied same simplification**:

**Before**:
```kotlin
val payorsByItem = allPayors.groupBy { it.transactionItemsId }

val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorForItem = payorsByItem[itemId]
        ?.filter { (it.currentAmountPaid ?: 0.0) > 0.0 }
        ?.maxByOrNull { it.currentAmountPaid ?: 0.0 }
        ?.let { usersById[it.userId] } ?: "-"
    itemId to payorForItem
}
```

**After**:
```kotlin
val itemPayorMap = items.associate { item ->
    val itemId = item.id ?: 0L
    val payorNames = item.payorId
        ?.mapNotNull { usersById[it] }
        ?.joinToString(", ") ?: "-"
    itemId to payorNames
}
```

## Benefits

### 1. Simplified Logic
- No need to query and group `transaction_payors` by item ID
- Direct access to payor information from the item itself
- Clearer data relationship

### 2. Better Performance
- Fewer database queries needed
- No need to filter and process payor records
- Faster item display

### 3. Clearer Data Model
- Explicit relationship between items and payors
- Easy to see who paid for each item at a glance
- Reduces confusion about data relationships

### 4. Easier Maintenance
- Single source of truth for item payors
- Less code to maintain
- Simpler debugging

### 5. Flexible Display
- Can easily show all payors: "Alice, Bob, Charlie"
- Can show count: "3 payors"
- Can show first payor: "Alice and 2 others"

## Data Flow

### Creating Transaction
1. User selects payors for each item in UI
2. `TransactionEntry.payors` contains list of `PayorEntry` with amounts
3. Repository extracts user IDs where amount > 0
4. Saves array of user IDs to `transaction_items.payor_id`
5. Also creates records in `transaction_payors` for detailed tracking

### Displaying Transaction
1. Fetch `transaction_items` with `payor_id` array
2. Map user IDs to usernames using `usersById`
3. Join names with commas for display
4. Show in "Paid by" column

## Examples

### Single Payor
```kotlin
// Database
payor_id: [123]

// Display
"Alice"
```

### Multiple Payors
```kotlin
// Database
payor_id: [123, 456, 789]

// Display
"Alice, Bob, Charlie"
```

### No Payors Yet
```kotlin
// Database
payor_id: null

// Display
"-"
```

### Empty Array
```kotlin
// Database
payor_id: []

// Display
"-"
```

## Migration Notes

For existing transactions in the database:
1. The `payor_id` column will be `null` for old records
2. The code handles `null` gracefully by showing "-"
3. New transactions will have the `payor_id` populated
4. Optional: Run a migration script to populate `payor_id` from `transaction_payors` for old records

## Testing Scenarios

1. **Create Single Payor Transaction**
   - Add item with one payor
   - Verify: `payor_id` = `[user_id]`
   - Verify: Display shows single name

2. **Create Multiple Payor Transaction**
   - Add item with multiple payors
   - Verify: `payor_id` = `[user_id1, user_id2, ...]`
   - Verify: Display shows all names separated by commas

3. **Create Transaction with No Payors**
   - Add item without selecting payors
   - Verify: `payor_id` = `null` or `[]`
   - Verify: Display shows "-"

4. **View Old Transaction**
   - Open transaction created before this change
   - Verify: Display shows "-" (graceful handling of null)

5. **Mixed Items**
   - Item 1: Single payor
   - Item 2: Multiple payors
   - Item 3: No payor
   - Verify: Each displays correctly
