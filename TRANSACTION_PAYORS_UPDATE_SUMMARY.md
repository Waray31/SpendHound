# Transaction Payors Table Update Summary

## Schema Changes

The `transaction_payors` table has been updated with the following columns:

1. **`initial_amount_paid`** (numeric) - Stores the original payment amount when transaction is first created. This value never changes.

2. **`current_amount_paid`** (numeric) - Stores the current payment amount. Initially copied from `initial_amount_paid`, but gets updated when editing transaction payments.

3. **`excess_amount`** (numeric) - Stores excess payment when a member pays more than their split share.
   - Formula: `excess_amount = current_amount_paid - split_amount_per_member` (if positive, else 0)

4. **`status`** (int2) - Payment status indicator:
   - `0` = Unpaid (initial_amount_paid = 0)
   - `1` = Settled (current_amount_paid >= split_amount_per_member)
   - `2` = Pending (0 < current_amount_paid < split_amount_per_member)

## Code Changes

### 1. MultiTransactionModels.kt

#### Updated Models:
- **`TransactionPayorInsert`** - Now includes all new fields
- **`TransactionPayorTable`** - Now includes all new fields
- **`TransactionPayorUpdate`** - NEW model for updating payments (only updates current_amount_paid, excess_amount, status)

### 2. MultiTransactionRepository.kt

#### Updated Functions:

**`submitTransactions()`**
- Now calculates `splitAmountPerMember` for each transaction item
- For each group member, calculates:
  - `initialAmountPaid` - from payor entry or 0
  - `currentAmountPaid` - same as initial
  - `excessAmount` - if paid more than split share
  - `status` - based on payment amount vs split share
- Inserts all members at once with proper values (no separate update needed)

**`updatePayorPayment()` - NEW FUNCTION**
- Updates a specific payor's payment amount
- Parameters:
  - `transactionId` - The transaction ID
  - `userId` - The user being updated
  - `transactionItemsId` - The specific item
  - `newAmountPaid` - The new payment amount
  - `splitAmountPerMember` - The expected split amount
- Automatically recalculates:
  - `current_amount_paid` = newAmountPaid
  - `excess_amount` = max(0, newAmountPaid - splitAmountPerMember)
  - `status` = 0 (unpaid), 1 (settled), or 2 (pending)

## Usage Examples

### Creating a Transaction
```kotlin
// The repository automatically handles all calculations
repository.submitTransactions(
    groupId = groupId,
    createdBy = userId,
    title = "Dinner",
    entries = listOf(
        TransactionEntry(
            title = "Pizza",
            amount = 100.0,
            category = "Food",
            payors = listOf(
                PayorEntry(userId = 1, username = "Alice", amount = 100.0)
            )
        )
    ),
    groupMembers = listOf(user1, user2, user3, user4)
)
// If 4 members, split = 25 each
// Alice paid 100, so:
//   initial_amount_paid = 100
//   current_amount_paid = 100
//   excess_amount = 75 (100 - 25)
//   status = 1 (settled)
// Others:
//   initial_amount_paid = 0
//   current_amount_paid = 0
//   excess_amount = 0
//   status = 0 (unpaid)
```

### Updating a Payment
```kotlin
// User 2 now pays their share
repository.updatePayorPayment(
    transactionId = txId,
    userId = 2,
    transactionItemsId = itemId,
    newAmountPaid = 25.0,
    splitAmountPerMember = 25.0
)
// Result:
//   current_amount_paid = 25
//   excess_amount = 0
//   status = 1 (settled)
//   initial_amount_paid remains 0
```

### Partial Payment
```kotlin
// User 3 pays partial amount
repository.updatePayorPayment(
    transactionId = txId,
    userId = 3,
    transactionItemsId = itemId,
    newAmountPaid = 10.0,
    splitAmountPerMember = 25.0
)
// Result:
//   current_amount_paid = 10
//   excess_amount = 0
//   status = 2 (pending)
```

## Important Notes

1. **`initial_amount_paid` is immutable** - Only set during transaction creation, never updated
2. **`current_amount_paid` is mutable** - Updated when editing payments
3. **Excess calculation** - Only positive values stored, negative means they still owe
4. **Status auto-calculation** - Always recalculated based on current_amount_paid vs split amount
5. **Split amount** - Must be calculated as `item_amount / number_of_group_members` before calling update

## Migration Notes

If you have existing data with the old `amount` column:
```sql
-- Migrate existing data
UPDATE transaction_payors 
SET 
  initial_amount_paid = amount,
  current_amount_paid = amount,
  excess_amount = 0,
  status = CASE 
    WHEN amount = 0 THEN 0 
    ELSE 1 
  END;
```
