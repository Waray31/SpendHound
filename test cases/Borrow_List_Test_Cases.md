# Test Cases: Borrow & Lend List Screen

## 1. Owed vs Debt Navigation
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BORROW_01** | Tab Switching | Tap on "Owed" and "Debt" headers. | The screen switches between transactions where others owe you (Owed) and where you owe others (Debt). |
| **TC_BORROW_02** | Tab Color Feedback | Switch tabs. | The active tab is highlighted (orange/bold) and the inactive tab is dimmed. |

## 2. Filtering
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BORROW_03** | Date Range Spinner | Select "This Month", "Last Month", or "All Time". | The list filters transactions based on the selected time period. |
| **TC_BORROW_04** | Status Filter Tabs | Tap "All", "Paid", "Unpaid", or "Pending". | Filters the list based on transaction status. |
| **TC_BORROW_05** | Custom Date Picker | Select "Custom Date" and choose a range. | The list correctly reflects transactions only within that specific range. |

## 3. Actions (Lender Side - Owed)
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BORROW_06** | Mark as Received | Tap "Received" on a pending/unpaid item. | Confirmation dialog appears. On confirm, status updates to "Paid" and balances are adjusted. |
| **TC_BORROW_07** | Approve Borrow Request | Tap "Approve" on a new borrow request. | Transaction status changes to "Unpaid" (Active debt). |
| **TC_BORROW_08** | Decline Request | Tap "Decline" on a borrow request. | Confirmation dialog appears. On confirm, status updates to "Declined". |

## 4. Actions (Borrower Side - Debt)
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BORROW_09** | Notify Payment | Tap "Pay" on an unpaid debt item. | Confirmation dialog appears. Status updates to "Pending Payment", and the lender is notified. |
| **TC_BORROW_10** | Remove Request | Tap "Remove" on a declined or draft request. | Transaction is removed from the list. |
| **TC_BORROW_11** | Try Again | Tap "Try Again" on a declined request. | Resubmits the borrow request for lender approval. |

## 5. Edge Cases & UI
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_BORROW_12** | Empty State | View a tab with no transactions. | Displays "No Owed Transactions" or "No Debt Transactions" message. |
| **TC_BORROW_13** | Loading State | Perform an action that triggers network sync. | Loading overlay/spinner appears and prevents multiple clicks until completion. |
