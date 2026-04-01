# Transaction Payor Revisions

## Overview
Updated the transaction payor system to properly handle excess payments across multiple transaction items and display payment status visually in the UI.

## Changes Made

### 1. MultiTransactionRepository.kt - Payment Logic Update

**Previous Logic:**
- `initial_amount_paid` = amount paid by user per item
- `current_amount_paid` = amount paid by user per item (same as initial)
- Calculated per item independently

**New Logic:**
- Aggregates all payments per user across ALL items in a transaction
- `initial_amount_paid` = full amount paid by user for each item (stored per item)
- `current_amount_paid` = user's total payment capped at their total split amount (calculated across all items)
- `excess_amount` = total amount paid beyond the total split share
- `status` = calculated based on total `current_amount_paid` vs total split amount

**Example 1: Single Item with Excess**
- Transaction item: ₱300
- Split among 3 members: ₱100 each
- User A pays ₱150

Result:
- `initial_amount_paid` = 150.0 (saved with excess)
- `current_amount_paid` = 100.0 (capped at split amount)
- `excess_amount` = 50.0 (150 - 100)
- `status` = 1 (settled)

**Example 2: Multiple Items with Aggregate Calculation**
- Transaction total: ₱270 (Item 1: ₱200 + Item 2: ₱70)
- Split among 3 members: ₱90 each (total individual payment)
- User A pays ₱80 for Item 1 and ₱20 for Item 2 = ₱100 total

Result:
- Item 1: `initial_amount_paid` = 80.0
- Item 2: `initial_amount_paid` = 20.0
- Total initial paid = 100.0
- `current_amount_paid` = 90.0 (capped at total split ₱90)
- `excess_amount` = 10.0 (100 - 90)
- `status` = 1 (settled, because current_amount_paid >= total split)

**Example 3: User Request Scenario**
- Transaction total: ₱270 (Item 1: ₱200 + Item 2: ₱70)
- Split among 3 members: ₱90 each
- User pays ₱80 + ₱20 = ₱100 total, but individual payment should be ₱90

Result:
- `initial_amount_paid` = 100 (sum of all item payments)
- `current_amount_paid` = 90 (capped at individual payment)
- `excess_amount` = 10 (100 - 90)

### 2. item_payor_horizontal.xml - Visual Status Indicator

**Added:**
- Status badge overlay on profile image using FrameLayout
- Badge shows a checkmark icon with colored background
- Badge visibility controlled by payment status

**Badge States:**
- Hidden: Unpaid (amount = 0)
- Orange: Paid Partially (0 < amount < split)
- Green: Paid (amount >= split)

### 3. PayorAdapter.kt - Status Badge Display

**Updated:**
- Added `payorStatusBadge` ImageView to ViewHolder
- Modified `updateStatusUI()` to show/hide and color the badge
- Badge uses `setColorFilter()` to match status color (orange/green)

## Payment Calculation Flow

1. **Insert Phase**: Create transaction_payor records for each item with initial_amount_paid
2. **Aggregation Phase**: Sum all initial payments per user across all items
3. **Calculation Phase**: 
   - `total_initial_paid` = sum of all initial_amount_paid for user
   - `total_split` = total transaction amount / number of members
   - `current_amount_paid` = min(total_initial_paid, total_split)
   - `excess_amount` = max(0, total_initial_paid - total_split)
4. **Update Phase**: Update all transaction_payor records with calculated values

## Status Calculation Logic

```kotlin
val status = when {
    currentPaid == 0.0 -> 0  // unpaid
    currentPaid >= totalSplitPerMember -> 1  // settled
    else -> 2  // pending (partial payment)
}
```

## Benefits

1. **Accurate Tracking**: Initial payment amount is preserved for each item
2. **Aggregate Calculation**: Current amount and excess calculated across all items
3. **Proper Editing**: Current amount can be updated without losing initial payment data
4. **Visual Feedback**: Users can quickly see who has paid/partially paid via badge on profile image
5. **Excess Handling**: Excess payments are properly calculated across all transaction items

## Testing Scenarios

1. **Full Payment with Excess (Single Item)**
   - User pays more than their split share
   - Badge shows green checkmark
   - Status shows "Paid"

2. **Full Payment with Excess (Multiple Items)**
   - User pays for multiple items, total exceeds their split
   - Badge shows green checkmark
   - Status shows "Paid"
   - Excess calculated from total payments

3. **Partial Payment**
   - User pays less than their total split share
   - Badge shows orange checkmark
   - Status shows "Paid Partially"

4. **No Payment**
   - User hasn't paid anything
   - No badge shown
   - Status shows "Unpaid"

5. **Edit Payment**
   - When editing, `current_amount_paid` is updated
   - `initial_amount_paid` remains unchanged
   - Excess and status recalculated automatically
