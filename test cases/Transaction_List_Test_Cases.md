# Test Cases: Transaction List Screen

## 1. Filtering & Sorting
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_TX_01** | Date Range Filter | Select "Last Month" from the date spinner. | The list updates to only show transactions from the previous calendar month. |
| **TC_TX_02** | Custom Date Range | Select "Custom Date", pick a start and end date. | The list updates to show only transactions within the selected range. |
| **TC_TX_03** | Group Filter | Select a specific Group from the group spinner. | The list filters to show only transactions belonging to the selected group. |
| **TC_TX_04** | Status Tabs | Switch between "All", "Paid", and "Unpaid" tabs. | The list filters transactions based on their current payment status. |

## 2. List Management
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_TX_05** | Transaction Count | Apply filters and observe the count text. | The header displays the correct number of transactions matching the current filters. |
| **TC_TX_06** | Empty State | Apply filters that result in no matches. | An empty state layout (e.g., "No transactions found") is displayed. |
| **TC_TX_07** | Pull to Refresh | Swipe down on the transaction list. | The list is refreshed with the latest data from the database. |

## 3. Archived Transactions
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_TX_08** | Expand/Collapse Archive | Tap on the "Archived Transactions" header. | The archived section expands to show old transactions or collapses to hide them. |
| **TC_TX_09** | Archive Filtering | Apply filters while archive is expanded. | Archived transactions are also filtered based on date, group, or status. |

## 4. Interaction & Navigation
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_TX_10** | Transaction Click | Tap on a transaction item. | Navigates to the Edit/Details screen for that transaction. |
| **TC_TX_11** | Long Press Action | Long press a transaction item. | (If implemented) Shows a context menu for options like Delete, Archive, or Settle. |
| **TC_TX_12** | Infinite Scroll | Scroll to the bottom of the list. | (If implemented) Loads the next page of transactions automatically. |
