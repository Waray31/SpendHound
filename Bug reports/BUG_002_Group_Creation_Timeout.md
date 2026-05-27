# Bug Report: Group Creation Timeout on Slow Network

**Title:** App hangs on "Creating Group..." overlay when network latency is high.

**Severity:** Medium

**Status:** Open

**Steps to Reproduce:**
1. Navigate to "Create Group" screen.
2. Select 3-5 members.
3. Provide a group name and image.
4. Using an emulator or network tool, throttle connection to "Slow 3G".
5. Click "Create".
6. Wait for 30 seconds.

**Expected Result:**
The app should either successfully create the group or show a user-friendly timeout error after a reasonable duration (e.g., 15 seconds) and dismiss the loading overlay.

**Actual Result:**
The loading overlay stays indefinitely. The user is forced to force-close the app to regain control.

**Screenshots:**
*   Sample screenshot file
