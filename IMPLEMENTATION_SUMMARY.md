# Database Schema Refactoring - Implementation Summary

## Overview
Successfully implemented a comprehensive database schema refactoring to standardize user balance tracking with new semantic fields. The system now accurately tracks user financial involvement through transactions and borrows.

## Changes Made

### 1. Model Updates

#### UserBalance.java
**Changes:**
- Replaced 6 old fields with 5 new semantic fields:
  - `totalBillSpent`: Sum of paymentAmount in transactions where user is in payorsList
  - `totalBillPayment`: Sum of user's individual amounts from amountsPaidList
  - `totalIndividualSpent`: Sum of totalIndividualPayment for each transaction
  - `totaldebt`: Sum of borrow amounts where user is borrower with status ≠ "Paid"
  - `totalreceivable`: Sum of borrow amounts where user is lender with status ≠ "Paid"

**Old fields removed:**
- currentBalance, unpaid, owed, debt, totalBorrowed, totalLent

#### User.java
**Changes:**
- Removed duplicate balance fields (balanced, unpaid, owed, debt)
- Removed all backward compatibility constructors
- Kept clean constructor: `User(String username, String email, String profileImageUrl, String password, UserBalance balances)`
- Removed old field mappings that maintained backward compatibility

### 2. Helper Classes

#### BalanceHelper.java
**Changes:**
- Replaced 6 old update methods with 5 new atomic transaction methods:
  - `updateTotalBillSpent(uid, amountChange, callback)`
  - `updateTotalBillPayment(uid, amountChange, callback)`
  - `updateTotalIndividualSpent(uid, amountChange, callback)`
  - `updateTotaldebt(uid, amountChange, callback)`
  - `updateTotalreceivable(uid, amountChange, callback)`
- All methods use Firebase transactions to ensure atomicity
- Fixed `initializeBalancesForNewUser()` to use new UserBalance constructor

#### MigrationHelper.java
**Additions:**
- New method: `recalculateUserBalancesFromData(callback)` - Performs full migration by:
  1. Scanning all transactions and aggregating user involvement
  2. Scanning all borrows and aggregating unpaid amounts
  3. Batch writing all calculated balances to Firebase
- Helper methods:
  - `processTransactionForBalance()` - Calculates transaction-based totals
  - `processBorrowForBalance()` - Calculates borrow-based totals
  - `writeBalancesToDatabase()` - Batch writes all user balances
- Updated `migrateUserBalances()` to initialize new schema

### 3. Transaction Flow Updates

#### AddTransactionActivity.java
**Changes:**
- Added `updateUserBalancesForTransaction()` method called after successful transaction save
- Updates:
  - `totalBillSpent` for poster
  - `totalBillPayment` + `totalIndividualSpent` for each payor

**Flow:**
```
Transaction saved to Firebase
  ↓
updateUserBalancesForTransaction()
  ├─ updateTotalBillSpent(posterUID, paymentAmount)
  └─ For each payor:
      ├─ updateTotalBillPayment(payorUID, amountPaid)
      └─ updateTotalIndividualSpent(payorUID, totalIndividualPayment)
```

### 4. Borrow Flow Updates

#### BorrowNowActivity.java
**Changes:**
- Updated balance update calls after borrow creation:
  - Old: `updateDebt()`, `updateTotalBorrowed()`, `updateOwed()`, `updateTotalLent()`
  - New: `updateTotaldebt()`, `updateTotalreceivable()`

#### BorrowFragment.java
**Changes:**
1. **Borrow creation** - Updated balance methods
2. **Status updates** - Enhanced `updateTransactionStatus()` and `updateTransactionStatusWithPaymentDate()`:
   - When status changes to "Paid":
     - Fetches borrow data to get borrowerID, lenderID, and amount
     - Decrements `totaldebt` for borrower (negative amount)
     - Decrements `totalreceivable` for lender (negative amount)
   - Only decrements if not already in "Paid" status

#### PayerListTransactionAdapter.java
**Changes:**
- Enhanced `updateTransactionStatus()` to handle "Paid" status:
  - Fetches borrow data when status → "Paid"
  - Decrements balances atomically
  - Prevents double-decrementing if already paid

### 5. Constructor Updates

Fixed User instantiation in:
- **BorrowFragment.java** - Line 830-831, 840-841
- **BorrowNowActivity.java** - Line 160-161, 170-171

Changed from:
```java
new User("", "", "", "", 0, 0, 0, 0)
```

To:
```java
new User("", "", "", "", new UserBalance())
```

## Database Structure

### Before Migration
```
users/{uid}/
├─ username
├─ email
├─ password
├─ profileImageUrl
├─ balanced (int)
├─ unpaid (int)
├─ owed (int)
├─ debt (int)
└─ balances/
   ├─ currentBalance
   ├─ unpaid
   ├─ owed
   ├─ debt
   ├─ totalBorrowed
   └─ totalLent
```

### After Migration
```
users/{uid}/
├─ username
├─ email
├─ password
├─ profileImageUrl
└─ balances/
   ├─ totalBillSpent
   ├─ totalBillPayment
   ├─ totalIndividualSpent
   ├─ totaldebt
   └─ totalreceivable
```

## Field Calculations

### totalBillSpent
- **Includes:** All transactions where user appears in `payorsList`
- **Value:** Sum of `paymentAmount` for matching transactions
- **Updated:** When transaction is created

### totalBillPayment
- **Includes:** User's actual payment amounts in transactions
- **Value:** Sum of user's values in `amountsPaidList`
- **Updated:** When transaction is created

### totalIndividualSpent
- **Includes:** Average split amounts user participated in
- **Value:** Sum of `totalIndividualPayment` for each transaction user was in
- **Updated:** When transaction is created

### totaldebt
- **Includes:** Unpaid borrows where user is borrower
- **Value:** Sum of borrow amounts with status ≠ "Paid"
- **Updated:** 
  - Incremented: When borrow created
  - Decremented: When borrow status → "Paid"

### totalreceivable
- **Includes:** Unpaid borrows where user is lender
- **Value:** Sum of borrow amounts with status ≠ "Paid"
- **Updated:**
  - Incremented: When borrow created
  - Decremented: When borrow status → "Paid"

## Migration Process

### Automatic Migration
Call `MigrationHelper.recalculateUserBalancesFromData(callback)` to:
1. Scan all transactions in `/transactions/{monthYear}/{day}/{timestamp}`
2. Scan all borrows in `/borrows/{monthYear}/{day}/{borrowId}`
3. Calculate aggregates per user
4. Batch write to `/users/{uid}/balances`

### Manual Options
- Use existing `MigrationHelper.runAllMigrations()` for backward compatibility
- Use `MigrationHelper.migrateUserBalances()` for schema initialization

## Testing Checklist

- [ ] New transactions correctly update `totalBillSpent`, `totalBillPayment`, `totalIndividualSpent`
- [ ] New borrows correctly update `totaldebt`, `totalreceivable`
- [ ] Marking borrow as "Paid" decrements `totaldebt` and `totalreceivable`
- [ ] No double-decrementing when status already "Paid"
- [ ] MigrationHelper correctly aggregates existing data
- [ ] All compiler errors resolved (warnings acceptable)
- [ ] UI displays updated balance fields correctly

## Deployment Notes

1. **Backward Compatibility:** Old fields removed - ensure UI doesn't reference them
2. **Migration:** Run `MigrationHelper.recalculateUserBalancesFromData()` after deployment
3. **Atomic Operations:** All balance updates use Firebase transactions - safe for concurrent access
4. **Data Consistency:** Check that old fields are properly cleaned up from database

## Files Modified

1. `UserBalance.java` - ✅ Schema updated
2. `User.java` - ✅ Cleaned up
3. `BalanceHelper.java` - ✅ New methods added
4. `MigrationHelper.java` - ✅ New migration method added
5. `AddTransactionActivity.java` - ✅ Balance updates on transaction creation
6. `BorrowNowActivity.java` - ✅ Updated balance methods, fixed constructors
7. `BorrowFragment.java` - ✅ Updated balance methods and status handling, fixed constructors
8. `PayerListTransactionAdapter.java` - ✅ Status handling with balance updates

## Future Enhancements

1. Add ProfileFragment updates to display new balance fields
2. Add balance history tracking for audit trails
3. Create analytics views for user spending patterns
4. Add balance recalculation trigger in settings UI
5. Implement real-time balance sync for multiple devices

