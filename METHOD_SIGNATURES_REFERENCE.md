# Complete Method Signatures Reference

## BorrowNowTransaction.kt

### Getters
```kotlin
fun getBorrowId(): String?
fun getBorrowerID(): String?
fun getLenderID(): String?
fun getBorrowerName(): String?
fun getDate(): String?
fun getLender(): String?
fun getBorrowedAmountStr(): String?
fun getStatus(): String?
fun getTimestamp(): Long
fun getPaymentSentDate(): Long
```

### Setters
```kotlin
fun setBorrowId(id: String?)
fun setBorrowerID(id: String?)
fun setLenderID(id: String?)
fun setBorrowerName(name: String?)
fun setDate(date: String?)
fun setLender(lender: String?)
fun setBorrowedAmountStr(amount: String?)
fun setStatus(status: String?)
fun setTimestamp(timestamp: Long)
fun setPaymentSentDate(date: Long)
```

---

## OwedTransaction.kt

### Getters
```kotlin
fun getDate(): String?
fun getBorrower(): String?
fun getBorrowedAmountStr(): String?
fun getStatus(): String?
fun getPaymentSentDate(): String?
fun getBorrowId(): String?
fun getMonthYear(): String?
fun getDay(): String?
```

### Setters
```kotlin
fun setDate(date: String?)
fun setBorrower(borrower: String?)
fun setBorrowedAmountStr(amount: String?)
fun setStatus(status: String?)
fun setPaymentSentDate(date: String?)
fun setBorrowId(id: String?)
fun setMonthYear(monthYear: String?)
fun setDay(day: String?)
```

---

## BorrowTransaction.kt

### Getters
```kotlin
fun getDate(): String?
fun getBorrowee(): String?
fun getBorrowedAmountStr(): String?
fun getStatus(): String?
fun getBorroweeDisplayName(): String?
fun getPaymentSentDate(): String?
fun getBorrowId(): String?
fun getMonthYear(): String?
fun getDay(): String?
```

### Setters
```kotlin
fun setDate(date: String?)
fun setBorrowee(borrowee: String?)
fun setBorrowedAmountStr(amount: String?)
fun setStatus(status: String?)
fun setBorroweeDisplayName(name: String?)
fun setPaymentSentDate(date: String?)
fun setBorrowId(id: String?)
fun setMonthYear(monthYear: String?)
fun setDay(day: String?)
```

---

## User.kt

### Getters (Immutable Data Class)
```kotlin
fun getUsername(): String?
fun getEmail(): String?
fun getProfileImageUrl(): String?
fun getBalances(): UserBalance?
fun getId(): String?
```

### Setters
```kotlin
// No setters - immutable data class
// Use data class copy() method to create modified instances
```

---

## UserBalance.kt

### Getters
```kotlin
fun getTotalBillSpent(): Double
fun getTotalBillPayment(): Double
fun getTotalIndividualSpent(): Double
fun getTotaldebt(): Double
fun getTotalreceivable(): Double
```

### Setters
```kotlin
fun setTotalBillSpent(amount: Double)
fun setTotalBillPayment(amount: Double)
fun setTotalIndividualSpent(amount: Double)
fun setTotaldebt(amount: Double)
fun setTotalreceivable(amount: Double)
```

---

## Transaction.kt

### Getters
```kotlin
fun getTransactionType(): String?
fun getPaymentAmount(): Double
fun getMultilineStr(): String?
fun getPayorsList(): MutableList<String?>?
fun getAmountsPaidList(): MutableList<Double?>?
fun getUsernamePost(): String?
fun getTotalIndividualPayment(): Double
fun getGroupId(): String?
fun getGroupName(): String?
fun getPayorsDisplayNames(): MutableList<String?>?
fun getPosterDisplayName(): String?
```

### Setters
```kotlin
fun setTransactionType(type: String?)
fun setPaymentAmount(amount: Double)
fun setMultilineStr(str: String?)
fun setPayorsList(list: MutableList<String?>?)
fun setAmountsPaidList(list: MutableList<Double?>?)
fun setUsernamePost(username: String?)
fun setTotalIndividualPayment(amount: Double)
fun setGroupId(id: String?)
fun setGroupName(name: String?)
fun setPayorsDisplayNames(names: MutableList<String?>?)
fun setPosterDisplayName(name: String?)
```

### Helper Methods
```kotlin
fun isUserInvolvedByUid(uid: String?): Boolean
fun isUserInvolvedByUsername(username: String?): Boolean
```

---

## PayerGroup.kt

### Getters
```kotlin
fun getGroupId(): String?
fun getGroupName(): String?
fun getMembers(): MutableList<String?>?
fun getCreatedBy(): String?
fun getMemberDisplayNames(): MutableList<String?>?
```

### Setters
```kotlin
fun setGroupId(id: String?)
fun setGroupName(name: String?)
fun setMembers(members: MutableList<String?>?)
fun setCreatedBy(createdBy: String?)
fun setMemberDisplayNames(names: MutableList<String?>?)
```

---

## Index by Field Type

### String? Getters (23)
- Borrows: borrowId, borrowerID, lenderID, borrowerName, date, lender, borrowedAmountStr, status
- Owed: date, borrower, borrowedAmountStr, status, paymentSentDate, borrowId, monthYear, day
- Debt: date, borrowee, borrowedAmountStr, status, borroweeDisplayName, paymentSentDate, borrowId, monthYear, day
- User: username, email, profileImageUrl, id
- Groups: groupId, groupName, createdBy

### Double Getters (5)
- UserBalance: totalBillSpent, totalBillPayment, totalIndividualSpent, totaldebt, totalreceivable
- Transaction: paymentAmount, totalIndividualPayment

### Long Getters (2)
- Borrows: timestamp, paymentSentDate

### List Getters (4)
- Transaction: payorsList, amountsPaidList, payorsDisplayNames
- Groups: members, memberDisplayNames

### Complex Getters (1)
- User: balances (UserBalance)

---

## Index by Operation

### Database Write Operations (Create/Update)
**Use these setters when saving data to database:**
- `setBorrowId()`, `setBorrowerID()`, `setLenderID()` - Set borrow identifiers
- `setPaymentAmount()`, `setBorrowedAmountStr()` - Set amounts
- `setStatus()` - Update status
- `setGroupId()`, `setGroupName()` - Set group info
- `setMembers()`, `setCreatedBy()` - Set group members

### Database Read Operations (Display/Calculate)
**Use these getters when retrieving and displaying data:**
- `getBorrowedAmountStr()`, `getPaymentAmount()` - Get transaction amounts
- `getStatus()` - Check transaction/borrow status
- `getGroupName()`, `getMembers()` - Display group info
- `getUsername()`, `getEmail()` - Display user info

### Balance Calculations
**Use these for financial calculations:**
- `getTotaldebt()`, `getTotalreceivable()` - Get debt/receivable
- `getTotalBillSpent()`, `getTotalBillPayment()` - Get bill totals
- `setTotaldebt()`, `setTotalreceivable()` - Update debts

### User Identification
**Use these for user tracking:**
- `getBorrowerID()`, `getLenderID()` - Get user IDs
- `getId()`, `getUsername()` - Get user identifiers
- `getPayorsList()` - Get list of payers

### Display/UI Operations
**Use these for UI rendering:**
- `getBorrowerName()`, `getLender()` - Display name
- `getPayorsDisplayNames()`, `getPosterDisplayName()` - Display names
- `getBorroweeDisplayName()`, `getMemberDisplayNames()` - Display names

---

## Common Patterns

### Pattern 1: Get and Update Balance
```kotlin
val currentDebt = user.getBalances()?.getTotaldebt() ?: 0.0
val newDebt = currentDebt + amount
user.getBalances()?.setTotaldebt(newDebt)
```

### Pattern 2: Check Borrow Status
```kotlin
val status = borrow.getStatus()
if (status == "Paid") {
    // Handle paid borrow
}
```

### Pattern 3: Get Transaction Participants
```kotlin
val payors = transaction.getPayorsList()
val creator = transaction.getUsernamePost()
```

### Pattern 4: Manage Group Members
```kotlin
val members = group.getMembers()
members?.add(newMemberId)
group.setMembers(members)
```

### Pattern 5: Track Payment Dates
```kotlin
val paymentDate = borrow.getPaymentSentDate()
borrow.setPaymentSentDate(System.currentTimeMillis())
```

---

## Type Conversion Notes

### When Converting IDs
- Database: int8 (64-bit integer)
- Kotlin: String (for Firebase/Supabase compatibility)
- Use direct String conversion: `.toString()` or casting

### When Converting Amounts
- Database: float8 (PostgreSQL double precision)
- Kotlin: Double or String (for display)
- Parse: `.toDouble()` or `.toInt()` as needed

### When Converting Lists
- Database: Arrays (_int8, _varchar, float8[])
- Kotlin: MutableList<String?> or MutableList<Double?>
- Handle null elements safely

### When Converting Timestamps
- Database: timestamptz (PostgreSQL timestamp with timezone)
- Kotlin: Long (milliseconds since epoch)
- Use: `System.currentTimeMillis()`

---

## Validation Tips

### Before Setting
```kotlin
// Validate before setting string values
if (!username.isNullOrEmpty()) {
    user.setUsername(username)
}

// Validate before setting amounts
if (amount > 0) {
    transaction.setPaymentAmount(amount)
}
```

### After Getting
```kotlin
// Handle null returns
val amount = transaction.getPaymentAmount()
val safeAmount = if (amount > 0) amount else 0.0

// Safe list operations
val payors = transaction.getPayorsList()
payors?.forEach { payer -> ... }
```

---

## Performance Considerations

- All getters are direct field access (O(1))
- All setters are direct field assignment (O(1))
- List operations (payorsList, members) are O(n) for iteration
- Use `?.` safe call operator for nullable returns

---

*Last Updated: March 18, 2026*
*Total Signatures: 103 methods*

