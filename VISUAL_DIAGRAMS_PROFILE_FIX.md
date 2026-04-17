# Visual Diagrams: Profile First Load Fix

## 🔄 Problem → Solution → Result Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         THE PROBLEM                             │
│                                                                 │
│  User taps Profile tab (1st time)                              │
│         ↓                                                       │
│  App shows loading overlay                                     │
│         ↓                                                       │
│  loadNicknameAndData() executes                                │
│         ├─ ✅ Queries users table → Gets username              │
│         ├─ ❌ Uses HARDCODED zeros for balances                │
│         └─ Sets balance = 0.0, unpaid = 0.0, etc.             │
│         ↓                                                       │
│  UI Display:                                                    │
│  ├─ Nickname: ✅ "John"                                        │
│  ├─ Balance: ❌ "0.0"  (should be $50.00)                      │
│  ├─ Unpaid: ❌ "0.0"   (should be $20.00)                      │
│  ├─ Owe: ❌ "0.0"      (should be $100.00)                     │
│  └─ Debt: ❌ "0.0"     (should be $30.00)                      │
│         ↓                                                       │
│  User sees incorrect data → Confused ❌                        │
│                                                                 │
│  User taps Profile tab again (2nd time)                        │
│         ↓                                                       │
│  Eventually, data shows correctly ✅                           │
│  (But user had to tap twice!)                                  │
│                                                                 │
│  ROOT CAUSE:                                                    │
│  ❌ Missing user_balance table query                           │
│  ❌ Hardcoded zero values                                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✅ The Solution in Action

```
┌─────────────────────────────────────────────────────────────────┐
│                       THE SOLUTION                              │
│                                                                 │
│  User taps Profile tab (1st time)                              │
│         ↓                                                       │
│  App shows loading overlay                                     │
│         ↓                                                       │
│  loadNicknameAndData() executes (NEW LOGIC):                   │
│         │                                                       │
│         ├─ Step 1: Query users table                           │
│         │   WHERE auth_id = "abc123def456"                     │
│         │   SELECT user_id, username, profile_image_url        │
│         │   Result: user_id = 42, username = "John"            │
│         │                                                       │
│         ├─ Step 2: Query user_balance table ✅ NEW!            │
│         │   WHERE user_id = 42                                 │
│         │   SELECT all balance columns                          │
│         │   Result: unpaid_total_group = 20, balance = 50, ... │
│         │                                                       │
│         ├─ Step 3: Extract real values ✅ NEW!                 │
│         │   balance = 50.0 (from database)                     │
│         │   unpaid = 20.0 (from database)                      │
│         │   owe = 100.0 (from database)                        │
│         │   debt = 30.0 (from database)                        │
│         │                                                       │
│         └─ Step 4: Update UI on Main thread                    │
│             nicknameTextView.text = "John"                     │
│             totalBalancedTextView.text = "$50.00"              │
│                                                                 │
│  Loading overlay disappears                                    │
│         ↓                                                       │
│  UI Display (on FIRST visit):                                  │
│  ├─ Nickname: ✅ "John"                                        │
│  ├─ Balance: ✅ "$50.00"  (CORRECT!)                           │
│  ├─ Unpaid: ✅ "$20.00"   (CORRECT!)                           │
│  ├─ Owe: ✅ "$100.00"     (CORRECT!)                           │
│  └─ Debt: ✅ "$30.00"     (CORRECT!)                           │
│         ↓                                                       │
│  User sees correct data immediately ✅                        │
│  NO SECOND TAP NEEDED!                                         │
│                                                                 │
│  WHAT CHANGED:                                                  │
│  ✅ Added user_balance table query                             │
│  ✅ Replaced hardcoded zeros with real data                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🗺️ Database Structure Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    SUPABASE DATABASE                         │
│                                                              │
│  ┌─────────────────────────────┐                            │
│  │     users table             │                            │
│  │  (PROFILE INFORMATION)      │                            │
│  │                             │                            │
│  │  user_id (PK) ............42│                            │
│  │  auth_id (FK) ........uuid42│                            │
│  │  username ..........john    │                            │
│  │  profile_image_url .../img  │                            │
│  │  created_at ....timestamp   │                            │
│  └────────┬────────────────────┘                            │
│           │                                                  │
│           │  (user_id = 42)                                 │
│           │  [Foreign Key Join]                             │
│           ↓                                                  │
│  ┌─────────────────────────────────────────────┐            │
│  │    user_balance table                       │            │
│  │  (FINANCIAL SUMMARY)  ✅ THE FIX!           │            │
│  │                                             │            │
│  │  user_id (PK/FK) ..............42           │            │
│  │  unpaid_total_group ........20.00           │            │
│  │  unpaid_total_individual ...30.00           │            │
│  │  receivable_total_group ...100.00           │            │
│  │  receivable_total_individual ..0.00         │            │
│  │  balance_total_group .......50.00           │            │
│  │  balance_total_individual ...0.00           │            │
│  │  created_at .............timestamp          │            │
│  └─────────────────────────────────────────────┘            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 🔀 Data Flow Diagram

```
START: User Taps Profile Tab
    │
    ↓
┌──────────────────────────────┐
│ Get Auth ID                  │
│ mAuth?.currentUserOrNull()   │
│        ↓                      │
│   authId = "abc123..."       │
└──────────────────────────────┘
    │
    ↓
┌──────────────────────────────────────┐
│ Step 1: Query users Table             │
│ WHERE auth_id = "abc123..."           │
│                                       │
│ Results:                              │
│  ├─ user_id = 42                      │
│  ├─ username = "John"                 │
│  └─ profile_image_url = "/img/.."     │
└──────────────────────────────────────┘
    │
    ├─ Store user.id = 42
    │  (For next query)
    │
    ↓
┌──────────────────────────────────────┐
│ Step 2: Query user_balance Table      │
│ WHERE user_id = 42                    │
│                                       │
│ Results:                              │
│  ├─ unpaid_total_group = 20.00        │
│  ├─ unpaid_total_individual = 30.00   │
│  ├─ receivable_total_group = 100.00   │
│  ├─ receivable_total_individual = 0.0 │
│  ├─ balance_total_group = 50.00       │
│  └─ balance_total_individual = 0.0    │
└──────────────────────────────────────┘
    │
    ↓
┌──────────────────────────────────────┐
│ Extract Values                        │
│                                       │
│ balance = 50.00                       │
│ unpaid = 20.00                        │
│ currentOwe = 0.0                      │
│ currentDebt = 30.00                   │
└──────────────────────────────────────┘
    │
    ↓
┌──────────────────────────────────────┐
│ Update UI (Main Thread)               │
│                                       │
│ nicknameTextView.text = "John"        │
│ totalBalancedTextView.text = "$50.00" │
│ unpaidTextView.text = "$20.00"        │
│ oweTextView.text = "$0.00"            │
│ debtTextView.text = "$30.00"          │
└──────────────────────────────────────┘
    │
    ↓
END: User Sees Correct Data on First Visit ✅
```

---

## 🏗️ Architecture Comparison

### BEFORE (Wrong Architecture)

```
┌─────────────────────────────────────────────────┐
│ ProfileFragment                                 │
│                                                 │
│  loadNicknameAndData()                          │
│    ├─ authId from Supabase Auth                 │
│    └─ Query users table                         │
│        ├─ Get: username ✓                       │
│        └─ Get: profile_image ✓                  │
│    └─ Use HARDCODED VALUES for balances ❌      │
│        ├─ balance = 0.0                         │
│        ├─ unpaid = 0.0                          │
│        ├─ owe = 0.0                             │
│        └─ debt = 0.0                            │
│    └─ Update UI ✗ (with zeros)                  │
│                                                 │
│  Result: Blank/zero values on first visit ❌   │
│                                                 │
└─────────────────────────────────────────────────┘
```

### AFTER (Correct Architecture)

```
┌──────────────────────────────────────────────────┐
│ ProfileFragment                                  │
│                                                  │
│  loadNicknameAndData()                           │
│    ├─ authId from Supabase Auth                  │
│    │                                             │
│    ├─ Step 1: Query users table                  │
│    │   └─ Get: user_id, username, image ✓       │
│    │                                             │
│    ├─ Step 2: Query user_balance table ✓ NEW!   │
│    │   └─ Get: all 6 balance columns ✓          │
│    │                                             │
│    ├─ Step 3: Extract real values ✓             │
│    │   ├─ balance = actual value                 │
│    │   ├─ unpaid = actual value                  │
│    │   ├─ owe = actual value                     │
│    │   └─ debt = actual value                    │
│    │                                             │
│    └─ Step 4: Update UI ✓ (with real data)      │
│                                                  │
│  Result: Correct values on first visit ✅       │
│                                                  │
└──────────────────────────────────────────────────┘
```

---

## 🔄 Async/Coroutine Pattern

```
┌─────────────────────────────────────────────────────────────┐
│ lifecycleScope.launch (Main thread)                         │
│                                                             │
│   try {                                                     │
│       ┌──────────────────────────────────────────────────┐  │
│       │ withContext(Dispatchers.IO) {                    │  │
│       │   // Database query #1 on background thread      │  │
│       │   val user = DeclareDatabase.usersTable...       │  │
│       │ }                                                │  │
│       └──────────────────────────────────────────────────┘  │
│                                                             │
│       ┌──────────────────────────────────────────────────┐  │
│       │ withContext(Dispatchers.IO) {                    │  │
│       │   // Database query #2 on background thread      │  │
│       │   val balance = DeclareDatabase.userBalance...   │  │
│       │ }                                                │  │
│       └──────────────────────────────────────────────────┘  │
│                                                             │
│       ┌──────────────────────────────────────────────────┐  │
│       │ withContext(Dispatchers.Main) {                  │  │
│       │   // UI updates on Main thread (required)        │  │
│       │   nicknameTextView?.text = username              │  │
│       │   totalBalancedTextView?.text = balance          │  │
│       │   loadingManager.hideLoading()                   │  │
│       │ }                                                │  │
│       └──────────────────────────────────────────────────┘  │
│                                                             │
│   } catch (e: Exception) {                                  │
│       Log.e("Error: ${e.message}")                          │
│       withContext(Dispatchers.Main) {                       │
│           loadingManager.hideLoading()                      │
│       }                                                     │
│   }                                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘

Key Benefits:
✅ Database queries don't block UI (IO thread)
✅ UI updates only on Main thread (Android requirement)
✅ Exception handling included
✅ Loading state properly managed
```

---

## 📊 Timeline Comparison

### BEFORE (Problem)

```
Time T=0s:    User taps Profile tab
             │
             ├─► Show loading overlay
             │
T=0.5s:      │
             ├─► Query users table
             │   └─ Gets: username ✓
             │
             ├─► Use hardcoded zeros ❌
             │
             ├─► Hide loading overlay
             │
             ├─► UI shows: nickname ✓, balances ✗ (0.0)
             │
T=1.0s:      │ User sees WRONG DATA on first visit ❌
             │
             │ ... (user frustrated) ...
             │
             ├─► User taps Profile tab again (2nd time)
             │
T=2.0s:      ├─► Eventually data refreshes/caches
             │
             ├─► UI shows: ALL DATA ✓ (finally!)
             │
T=2.5s:      ✓ Now user sees correct values
```

### AFTER (Solution)

```
Time T=0s:    User taps Profile tab
             │
             ├─► Show loading overlay
             │
T=0.5s:      │
             ├─► Query users table
             │   └─ Gets: user_id, username ✓
             │
             ├─► Query user_balance table ✓ NEW!
             │   └─ Gets: all balance values ✓
             │
             ├─► Extract real values ✓
             │
             ├─► Hide loading overlay
             │
             ├─► UI shows: nickname ✓, balances ✓ (REAL!)
             │
T=1.0s:      ✓ User sees CORRECT DATA on FIRST visit!
             │
             ├─► User happy, no second tap needed!
```

---

## 🎯 SQL Schema Visualization

```
Supabase Database
│
├─ users table
│  ├─ user_id: int8 (PK)                  [Primary Key]
│  │   42, 43, 44, ...
│  │
│  ├─ auth_id: uuid (FK to Auth)          [Lookup Key]
│  │   "abc123", "def456", ...
│  │
│  ├─ username: text
│  │   "john", "jane", ...
│  │
│  ├─ profile_image_url: varchar
│  │   "/images/john.jpg", ...
│  │
│  └─ ... other fields
│
└─ user_balance table
   ├─ user_id: int8 (PK/FK to users)      [Foreign Key]
   │   42, 43, 44, ...
   │
   ├─ unpaid_total_group: numeric
   │   20.00, 15.50, ...
   │
   ├─ unpaid_total_individual: numeric
   │   30.00, 25.75, ...
   │
   ├─ receivable_total_group: numeric
   │   100.00, 50.00, ...
   │
   ├─ receivable_total_individual: numeric
   │   0.00, 10.00, ...
   │
   ├─ balance_total_group: numeric
   │   50.00, 30.00, ...
   │
   ├─ balance_total_individual: numeric
   │   0.00, 5.00, ...
   │
   └─ created_at: timestamptz
       "2026-04-17 10:30:00", ...

Join Key: user_id
users.user_id = user_balance.user_id
```

---

## ✨ Summary Diagram

```
PROBLEM                    →    SOLUTION              →    RESULT

Hardcoded zeros  ──┐                                      ✅ Real data
                   ├────→ Query user_balance table ──→ ✅ First-load works
Missing query    ──┘                                      ✅ No 2nd tap
                                                          ✅ Happy users
```

---

**Diagrams complete!** 🎉

