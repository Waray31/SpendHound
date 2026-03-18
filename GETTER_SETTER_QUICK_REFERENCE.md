# Quick Reference: Getter/Setter Methods Alignment

## At a Glance

### ✅ Files Updated
1. **BorrowNowTransaction.kt** - Borrows table data model
2. **OwedTransaction.kt** - Owed transactions (lender's view)
3. **BorrowTransaction.kt** - Debt transactions (borrower's view)
4. **User.kt** - Users table data model
5. **UserBalance.kt** - User balance details
6. **Transaction.kt** - Transactions table data model
7. **PayerGroup.kt** - Groups table data model

---

## Method Naming Convention

All getters follow the pattern: `get[FieldName]()`
All setters follow the pattern: `set[FieldName](value)`

### Example
```
Field: borrowId
Getter: fun getBorrowId(): String?
Setter: fun setBorrowId(id: String?)
```

---

## Borrows Table Methods

### BorrowNowTransaction (Primary Borrow Model)
| Field | Getter | Setter |
|-------|--------|--------|
| borrowId | `getBorrowId()` | `setBorrowId(id)` |
| borrowerID | `getBorrowerID()` | `setBorrowerID(id)` |
| lenderID | `getLenderID()` | `setLenderID(id)` |
| borrowerName | `getBorrowerName()` | `setBorrowerName(name)` |
| date | `getDate()` | `setDate(date)` |
| lender | `getLender()` | `setLender(lender)` |
| borrowedAmountStr | `getBorrowedAmountStr()` | `setBorrowedAmountStr(amount)` |
| status | `getStatus()` | `setStatus(status)` |
| timestamp | `getTimestamp()` | `setTimestamp(timestamp)` |
| paymentSentDate | `getPaymentSentDate()` | `setPaymentSentDate(date)` |

---

## Transactions Table Methods

### Transaction (Main Transactions Model)
**Getters:**
- `getTransactionType()` → String?
- `getPaymentAmount()` → Double
- `getMultilineStr()` → String?
- `getPayorsList()` → MutableList<String?>?
- `getAmountsPaidList()` → MutableList<Double?>?
- `getUsernamePost()` → String?
- `getTotalIndividualPayment()` → Double
- `getGroupId()` → String?
- `getGroupName()` → String?
- `getPayorsDisplayNames()` → MutableList<String?>?
- `getPosterDisplayName()` → String?

**Setters:**
- `setTransactionType(type)`, `setPaymentAmount(amount)`, `setMultilineStr(str)`
- `setPayorsList(list)`, `setAmountsPaidList(list)`, `setUsernamePost(username)`
- `setTotalIndividualPayment(amount)`, `setGroupId(id)`, `setGroupName(name)`
- `setPayorsDisplayNames(names)`, `setPosterDisplayName(name)`

---

## Groups Table Methods

### PayerGroup (Groups Model)
| Field | Getter | Setter |
|-------|--------|--------|
| groupId | `getGroupId()` | `setGroupId(id)` |
| groupName | `getGroupName()` | `setGroupName(name)` |
| members | `getMembers()` | `setMembers(members)` |
| createdBy | `getCreatedBy()` | `setCreatedBy(createdBy)` |
| memberDisplayNames | `getMemberDisplayNames()` | `setMemberDisplayNames(names)` |

---

## Users Table Methods

### User (Users Model - Data Class)
**Getters Only (Data Class):**
- `getUsername()` → String?
- `getEmail()` → String?
- `getProfileImageUrl()` → String?
- `getBalances()` → UserBalance?
- `getId()` → String?

### UserBalance (Balance Details)
| Field | Getter | Setter |
|-------|--------|--------|
| totalBillSpent | `getTotalBillSpent()` | `setTotalBillSpent(amount)` |
| totalBillPayment | `getTotalBillPayment()` | `setTotalBillPayment(amount)` |
| totalIndividualSpent | `getTotalIndividualSpent()` | `setTotalIndividualSpent(amount)` |
| totaldebt | `getTotaldebt()` | `setTotaldebt(amount)` |
| totalreceivable | `getTotalreceivable()` | `setTotalreceivable(amount)` |

---

## Borrowed/Owed Transaction Models

### BorrowTransaction (Debt - Borrower's View)
| Field | Getter | Setter |
|-------|--------|--------|
| date | `getDate()` | `setDate(date)` |
| borrowee | `getBorrowee()` | `setBorrowee(borrowee)` |
| borrowedAmountStr | `getBorrowedAmountStr()` | `setBorrowedAmountStr(amount)` |
| status | `getStatus()` | `setStatus(status)` |
| borroweeDisplayName | `getBorroweeDisplayName()` | `setBorroweeDisplayName(name)` |
| paymentSentDate | `getPaymentSentDate()` | `setPaymentSentDate(date)` |
| borrowId | `getBorrowId()` | `setBorrowId(id)` |
| monthYear | `getMonthYear()` | `setMonthYear(monthYear)` |
| day | `getDay()` | `setDay(day)` |

### OwedTransaction (Owed - Lender's View)
| Field | Getter | Setter |
|-------|--------|--------|
| date | `getDate()` | `setDate(date)` |
| borrower | `getBorrower()` | `setBorrower(borrower)` |
| borrowedAmountStr | `getBorrowedAmountStr()` | `setBorrowedAmountStr(amount)` |
| status | `getStatus()` | `setStatus(status)` |
| paymentSentDate | `getPaymentSentDate()` | `setPaymentSentDate(date)` |
| borrowId | `getBorrowId()` | `setBorrowId(id)` |
| monthYear | `getMonthYear()` | `setMonthYear(monthYear)` |
| day | `getDay()` | `setDay(day)` |

---

## Common Usage Patterns

### Get User Info
```kotlin
val userId = user.getId()
val username = user.getUsername()
val userBalance = user.getBalances()?.getTotaldebt()
```

### Update Balance
```kotlin
val balance = userBalance.getTotaldebt()
userBalance.setTotaldebt(balance + amount)
```

### Get Transaction Details
```kotlin
val transactionType = transaction.getTransactionType()
val amount = transaction.getPaymentAmount()
val payors = transaction.getPayorsList()
```

### Get Borrow Status
```kotlin
val borrowStatus = borrow.getStatus()
val amount = borrow.getBorrowedAmountStr()
val lenderId = borrow.getLenderID()
```

### Get Group Info
```kotlin
val groupName = group.getGroupName()
val members = group.getMembers()
val creator = group.getCreatedBy()
```

---

## Database to Kotlin Mapping

| DB Table | Kotlin Class |
|----------|--------------|
| borrows | BorrowNowTransaction |
| transactions | Transaction |
| groups | PayerGroup |
| users | User + UserBalance |

### Status Values (Common)
- Borrows: "For Lender Approval", "Unpaid", "Paid", "Declined", "Pending Payment", "Removed"
- Transactions: Varies by transaction type

---

## Important Notes

✅ **Safe to Use**: All methods are compiled and tested
✅ **Consistent Naming**: Follow Java bean convention
✅ **Null Safety**: Properly handle nullable types
⚠️ **Display Names**: Include display names for UI while storing UIDs in DB
⚠️ **Type Consistency**: IDs are String (for Supabase/Firebase compatibility)

---

## Related Files
- Database Schema Details: `DATABASE_SCHEMA_ALIGNMENT.md`
- BorrowFragment: Uses most of these methods
- MainActivity: Accesses getters from transactions and borrows

