# Test Cases: Create Group Screen

## 1. Member Selection
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_GROUP_01** | Search User | Type a username in the search bar. | The user list filters to show matching users from the database or crew list. |
| **TC_GROUP_02** | Select Member | Tap on a user from the search results. | The user is added to the "Selected Members" horizontal list at the top. |
| **TC_GROUP_03** | Remove Member | Tap the "X" on a user in the "Selected Members" list. | The user is removed from the selection. |
| **TC_GROUP_04** | Minimum Members | Try to proceed without selecting any members. | The "Next" button remains disabled or shows a prompt to select at least one person. |

## 2. Group Details Dialog
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_GROUP_05** | Dialog Appearance | Select members and tap "Next". | A dialog appears asking for Group Name and Group Image. |
| **TC_GROUP_06** | Group Name Validation | Leave Group Name empty and try to save. | Error message "Group name is required" appears. |
| **TC_GROUP_07** | Set Group Image | Tap the image placeholder in the dialog. | Image picker opens. Selected image is displayed as a circular crop. |

## 3. Group Creation
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_GROUP_08** | Finalize Creation | Enter name and tap "Create". | Loading indicator appears. Group is created in Supabase. Members are added to the group. Success toast shown. |
| **TC_GROUP_09** | Large Group Support | Select 10+ members and create group. | Group is created successfully without timeout or performance issues. |

## 4. Navigation & Edge Cases
| Test Case ID | Description | Steps | Expected Result |
| :--- | :--- | :--- | :--- |
| **TC_GROUP_10** | Back Navigation | Tap the back arrow on the toolbar. | Activity closes and returns to the previous screen (usually the Groups list). |
| **TC_GROUP_11** | Already in Group | Attempt to add a user who is already in a group with you. | (Depends on logic) User should still be selectable for a *new* group. |
| **TC_GROUP_12** | Offline Creation | Attempt to create a group without internet. | Error message "Connection failed" or similar is displayed. |
