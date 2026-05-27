# Test Cases: Borrow & Lend Creation Screen

## 1. Mode Selection & UI Initialization
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BN_01** | Borrow Mode | Launch with "BORROW" intent. | Title is "Borrow Money". "Lender" selection list is visible. "Borrower" is fixed as Current User. |
| **TC_BN_02** | Lend Mode | Launch with "LEND" intent. | Title is "Lend Money". "Borrower" selection list is visible. "Lender" is fixed as Current User. |
| **TC_BN_03** | Close/Cancel | Tap "X" or "Cancel" button. | The activity closes without saving any data. |

## 2. Input & Selection
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BN_04** | User Selection | Select a user from the horizontal chips. | The selected user is highlighted. The "To/From" text updates to the user's nickname. |
| **TC_BN_05** | Amount Validation | Enter 0 or leave Amount empty. | Submit button is disabled or shows a validation toast. |
| **TC_BN_06** | Numeric Input | Enter a valid decimal amount (e.g., 150.50). | The amount is accepted and formatted. |
| **TC_BN_07** | Optional Note | Enter text in the Note field. | The note is saved along with the transaction request. |

## 3. Date Selection
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BN_08** | Payback Date Picker | Tap on the "Payback Date" field. | A Date Picker dialog appears. |
| **TC_BN_09** | Past Date Restriction | Select a payback date in the past. | The picker prevents selection or an error is shown (Payback date must be future). |

## 4. Submission
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BN_10** | Successful Borrow Request | Fill all fields and tap "Borrow". | Loading overlay appears. Request is saved to database. Success toast is shown. Screen closes. |
| **TC_BN_11** | Successful Lend Entry | Fill all fields and tap "Lend". | Transaction is recorded. If borrower is registered, they receive a notification. |
| **TC_BN_12** | Duplicate Submission | Tap "Borrow" multiple times rapidly. | Loading overlay prevents multiple requests from being sent. |
