# Bug Report: Home Screen Chart and Balance Discrepancy

**Title:** "Total Balance" amount does not match the sum of "You Owe" and "You're Owed" on the Home Screen.

**Severity:** High

**Status:** Open

**Steps to Reproduce:**
1. Log in to the SpendHound application.
2. Observe the "You Owe" and "You're Owed" cards on the Home Fragment.
3. Note the "Total Balance" value at the top of the screen (or in Profile).
4. Add a new expense where the user is a payer (increasing "You're Owed").
5. Return to the Home Screen and check the values.

**Expected Result:**
The "Total Balance" should be the net difference: `You're Owed - You Owe`. If "You're Owed" is ₱500 and "You Owe" is ₱200, the Total Balance should reflect ₱300.

**Actual Result:**
The "Total Balance" remains at ₱0.00 or shows a cached value that doesn't include the most recent transactions, while the Owe/Owed cards update correctly.

**Screenshots:**
*   Sample screenshot file
