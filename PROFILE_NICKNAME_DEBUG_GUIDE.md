# Profile Nickname Loading Debug Guide

## Issue
The `nicknameTextView` is not displaying the username when visiting the profile tab.

## Recent Changes Made
1. Fixed database column names in the `loadNicknameAndData()` function to use snake_case (Supabase convention)
   - Changed from: `"totalBillSpent"`, `"totalBillPayment"`, etc.
   - Changed to: `"total_bill_spent"`, `"total_bill_payment"`, etc.
2. Added comprehensive logging to help diagnose the issue

## How to Debug

### Step 1: Check the Logcat Output
When you open the app and navigate to the Profile tab, look for these log messages:

```
D/ProfileFragment: loadNicknameAndData - authId: [your_auth_id]
D/ProfileFragment: Fetching user with authId: [your_auth_id]
D/ProfileFragment: User fetched: [user_object]
D/ProfileFragment: Username: [username_from_db]
D/ProfileFragment: Setting nicknameTextView to: [username_from_db]
```

### Step 2: Interpret the Logs

**If you see all logs and username is displayed:**
- ✅ The issue is fixed! The database query is working.

**If you see "authId: null":**
- The Supabase Auth session is not initialized or the user is not logged in.
- Check that login is working correctly.

**If you see "User fetched: null":**
- The database query returned no results.
- Possible causes:
  1. The `auth_id` in the Supabase users table doesn't match the current user's auth ID
  2. The user record doesn't exist in the database
  3. There's an issue with the filter condition

**If you see an exception in the error logs:**
- Check if there's a serialization error with the User data class
- Verify that the column names match exactly what's in the Supabase `users` table

### Step 3: Verify Supabase Schema

Make sure the Supabase `users` table has these exact columns:
- `user_id` (int8)
- `auth_id` (text) - This should match the current authenticated user's ID
- `username` (text)
- `total_bill_spent` (numeric)
- `total_bill_payment` (numeric)
- `total_receivable` (numeric)
- `total_debt` (numeric)
- `total_individual_spent` (numeric)

### Step 4: Check User Record

In Supabase, verify that:
1. A user record exists with your current `auth_id`
2. The `username` field is populated (not null or empty)
3. All other fields have valid values (or at least are not causing deserialization errors)

## Code Location
- **File**: `ProfileFragment.kt`
- **Method**: `loadNicknameAndData()`
- **Lines**: 227-280

## What the Code Does
1. Gets the current authenticated user's ID from Supabase Auth
2. Queries the `users` table to find the matching user record
3. Extracts the `username` field
4. Sets the `nicknameTextView` text to the username
5. Also loads financial totals (balance, unpaid, owe, debt)

## Next Steps If Issue Persists
1. Check the Android Logcat for error messages
2. Verify the Supabase connection is working (other screens should load data)
3. Confirm the user record exists in Supabase with correct `auth_id`
4. Check if there are any serialization issues with the User data class

