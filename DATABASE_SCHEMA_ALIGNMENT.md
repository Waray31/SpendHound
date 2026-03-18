# Database Schema Alignment - Getter and Setter Methods

## Overview
This document outlines the getter and setter methods added to align with the revised database structure for tables: **users**, **transactions**, **groups**, and **borrows**.

---

## 1. BORROWS TABLE - BorrowNowTransaction.kt

### Database Schema
| Field | Type | Description |
|-------|------|-------------|
| id | int8 | Borrow ID (Primary Key) |
| borrower_id | int8 | ID of the borrower |
| lender_id | int8 | ID of the lender |
| borrowed_amount | float8 | Amount borrowed |
| created_at | timestamptz | Timestamp of creation |
| payment_sent_date | timestamptz | Payment sent date |
| status | int2 | Status code |

### Implemented Getters
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

### Implemented Setters
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

### Class Variables
- `borrowId`: String - Unique identifier for the borrow record
- `borrowerID`: String - ID of the person who borrowed
- `lenderID`: String - ID of the person who lent
- `borrowerName`: String - Display name of borrower
- `date`: String - Date of borrow transaction
- `lender`: String - Lender's name/identifier
- `borrowedAmountStr`: String - Amount borrowed
- `status`: String - Status of the borrow (e.g., "Paid", "Unpaid", "Pending")
- `timestamp`: Long - Creation timestamp
- `paymentSentDate`: Long - When payment was sent

---

## 2. TRANSACTIONS TABLE - Transaction.kt

### Database Schema
| Field | Type | Description |
|-------|------|-------------|
| id | int8 | Transaction ID (Primary Key) |
| payment_amount | float8 | Payment amount |
| transaction_type | text | Type of transaction |
| transaction_detail | varchar | Transaction details |
| group_id | int8 | Group ID (Foreign Key) |
| amount_paid_list | float8 | List of amounts paid |
| contributors | _varchar | List of contributor UIDs |
| individual_payment | float8 | Individual payment amount |
| creator_id | int8 | Creator's ID |
| created_at | timestamptz | Timestamp of creation |
| status | int2 | Status code |

### Implemented Getters
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

### Implemented Setters
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

### Class Variables
- `transactionType`: String - Type of transaction
- `paymentAmount`: Double - Amount of payment
- `multilineStr`: String - Multiline description
- `payorsList`: MutableList<String?> - List of payer UIDs
- `amountsPaidList`: MutableList<Double?> - List of amounts paid by each payer
- `usernamePost`: String - UID of the poster/creator
- `totalIndividualPayment`: Double - Total individual payment
- `groupId`: String - Group ID for group transactions
- `groupName`: String - Group name
- `payorsDisplayNames`: MutableList<String?> - Display names of payers (UI support)
- `posterDisplayName`: String - Display name of poster (UI support)

### Helper Methods
- `isUserInvolvedByUid(uid: String?)`: Boolean - Check if user is involved by UID
- `isUserInvolvedByUsername(username: String?)`: Boolean - Check if user is involved by username

---

## 3. GROUPS TABLE - PayerGroup.kt

### Database Schema
| Field | Type | Description |
|-------|------|-------------|
| group_id | int8 | Group ID (Primary Key) |
| group_name | text | Group name |
| createdby_id | int8 | ID of creator |
| member_ids | _int8 | Array of member IDs |
| created_at | timestamptz | Timestamp of creation |

### Implemented Getters
```kotlin
fun getGroupId(): String?
fun getGroupName(): String?
fun getMembers(): MutableList<String?>?
fun getCreatedBy(): String?
fun getMemberDisplayNames(): MutableList<String?>?
```

### Implemented Setters
```kotlin
fun setGroupId(id: String?)
fun setGroupName(name: String?)
fun setMembers(members: MutableList<String?>?)
fun setCreatedBy(createdBy: String?)
fun setMemberDisplayNames(names: MutableList<String?>?)
```

### Class Variables
- `groupId`: String - Unique identifier for the group
- `groupName`: String - Name of the group
- `members`: MutableList<String?> - List of member UIDs
- `createdBy`: String - UID of the group creator
- `memberDisplayNames`: MutableList<String?> - Display names of members (UI support)

---

## 4. USERS TABLE - User.kt

### Database Schema
| Field | Type | Description |
|-------|------|-------------|
| user_id | int8 | User ID (Primary Key) |
| username | text | Username |
| email | text | Email address |
| password | text | Hashed password |
| profile_image_url | varchar | URL to profile image |
| created_at | timestamptz | Timestamp of creation |

### Implemented Getters
```kotlin
fun getUsername(): String?
fun getEmail(): String?
fun getProfileImageUrl(): String?
fun getBalances(): UserBalance?
fun getId(): String?
```

### Class Variables
- `username`: String - User's username
- `email`: String - User's email address
- `profileImageUrl`: String - URL to user's profile image
- `balances`: UserBalance - User's balance information
- `id`: String - User's unique identifier (Supabase ID)

---

## 5. USER BALANCE - UserBalance.kt

### Implemented Getters
```kotlin
fun getTotalBillSpent(): Double
fun getTotalBillPayment(): Double
fun getTotalIndividualSpent(): Double
fun getTotaldebt(): Double
fun getTotalreceivable(): Double
```

### Implemented Setters
```kotlin
fun setTotalBillSpent(amount: Double)
fun setTotalBillPayment(amount: Double)
fun setTotalIndividualSpent(amount: Double)
fun setTotaldebt(amount: Double)
fun setTotalreceivable(amount: Double)
```

### Class Variables
- `totalBillSpent`: Double - Sum of paymentAmount in transactions where user is in payorsList
- `totalBillPayment`: Double - Sum of user's individual amounts from amountsPaidList
- `totalIndividualSpent`: Double - Sum of totalIndividualPayment for each transaction
- `totaldebt`: Double - Sum of borrow amounts where user is borrower with status ≠ "Paid"
- `totalreceivable`: Double - Sum of borrow amounts where user is lender with status ≠ "Paid"

---

## 6. DEBT TRANSACTION - BorrowTransaction.kt

### Implemented Getters
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

### Implemented Setters
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

### Class Variables
- `date`: String - Date of debt transaction
- `borrowee`: String - UID of the borrowee
- `borrowedAmountStr`: String - Amount borrowed
- `status`: String - Status of the debt
- `borroweeDisplayName`: String - Display name of borrowee (UI support)
- `paymentSentDate`: String - When payment was sent
- `borrowId`: String - Reference to borrow ID
- `monthYear`: String - Month-Year for filtering
- `day`: String - Day for filing

---

## 7. OWED TRANSACTION - OwedTransaction.kt

### Implemented Getters
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

### Implemented Setters
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

### Class Variables
- `date`: String - Date of owed transaction
- `borrower`: String - UID of the borrower
- `borrowedAmountStr`: String - Amount owed
- `status`: String - Status of the owed amount
- `paymentSentDate`: String - When payment was sent
- `borrowId`: String - Reference to borrow ID
- `monthYear`: String - Month-Year for filtering
- `day`: String - Day for filing

---

## Usage Guidelines

### When to Use Getters
```kotlin
// Getting user information
val username = user.getUsername()
val userId = user.getId()
val balances = user.getBalances()

// Getting transaction details
val amount = transaction.getPaymentAmount()
val payors = transaction.getPayorsList()
val groupId = transaction.getGroupId()

// Getting borrow information
val borrowAmount = borrow.getBorrowedAmountStr()
val status = borrow.getStatus()
val lenderId = borrow.getLenderID()
```

### When to Use Setters
```kotlin
// Setting user information
user.setEmail("newemail@example.com")
user.setProfileImageUrl("new_url")

// Setting transaction details
transaction.setPaymentAmount(100.0)
transaction.setStatus("Completed")
transaction.setGroupId("group_123")

// Setting borrow information
borrow.setStatus("Paid")
borrow.setPaymentSentDate(System.currentTimeMillis())
```

---

## Summary of Changes

| File | Getters Added | Setters Added |
|------|----------------|----------------|
| BorrowNowTransaction.kt | 10 | 10 |
| Transaction.kt | 11 | 11 |
| PayerGroup.kt | 5 | 5 |
| User.kt | 5 | 0 (immutable data class) |
| UserBalance.kt | 5 | 5 |
| BorrowTransaction.kt | 9 | 9 |
| OwedTransaction.kt | 8 | 8 |
| **TOTAL** | **53** | **48** |

---

## Notes

1. **Data Class Immutability**: The `User` class is a Kotlin data class with immutable properties, so setters are not applicable.

2. **Display Name Support**: `Transaction`, `PayerGroup`, `BorrowTransaction`, and `OwedTransaction` include display name fields for UI rendering while storing UIDs in the database.

3. **Redundancy Warnings**: Some null-safety calls (`.toString()`) generate warnings but are intentionally kept for defensive programming.

4. **Unused Function Warnings**: Setter methods that are not currently used will show warnings, but they are available for future use and prevent schema changes.

5. **Type Consistency**: All ID fields are stored as String for consistency with Supabase/Firebase ID formats.

---

## Compilation Status
✅ All files compile successfully with only benign warnings about unused functions.

