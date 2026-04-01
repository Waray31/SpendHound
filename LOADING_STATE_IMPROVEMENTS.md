# Loading State Improvements

## Overview
Implemented proper loading state management across the app to prevent user interaction during loading and automatically cancel loading when navigating between tabs.

## Changes Made

### 1. LoadingManager Utility (New File)
**Location**: `app/src/main/java/com/waray/spendhound/utils/LoadingManager.kt`

**Features**:
- Manages loading overlay visibility with pending load counter
- Blocks background interaction by setting `clickable=true` and `focusable=true` on overlay
- Lifecycle-aware: automatically cancels loading when fragment/activity is stopped
- Prevents loading operations when fragment is not active

**Key Methods**:
- `showLoading()`: Increments counter and shows overlay
- `hideLoading()`: Decrements counter and hides overlay when count reaches 0
- `forceHide()`: Immediately hides overlay regardless of counter
- `onStop()`: Automatically called when fragment stops, cancels all loading
- `onResume()`: Resets stopped state when fragment resumes

### 2. HomeFragment.kt Updates

**Before**:
```kotlin
private var loadingOverlay_home: View? = null
private var pendingLoads = 0

private fun showLoading() {
    pendingLoads++
    loadingOverlay_home?.visibility = View.VISIBLE
}

private fun hideLoading() {
    pendingLoads = Math.max(0, pendingLoads - 1)
    if (pendingLoads == 0) loadingOverlay_home?.visibility = View.GONE
}
```

**After**:
```kotlin
private var loadingManager: LoadingManager? = null

override fun onCreateView(...) {
    val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay_home)
    loadingManager = LoadingManager(loadingOverlay, viewLifecycleOwner.lifecycle)
}

private fun showLoading() {
    loadingManager?.showLoading()
}

private fun hideLoading() {
    loadingManager?.hideLoading()
}
```

### 3. fragment_home.xml Updates

**Added attributes to loading overlay**:
```xml
<LinearLayout
    android:id="@+id/loadingOverlay_home"
    android:clickable="true"
    android:focusable="true"
    ...>
```

These attributes prevent user interaction with background content during loading.

### 4. activity_main.xml - Removed Duplicate Progress Bar

**Removed**:
```xml
<FrameLayout
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center"
    android:elevation="20dp">

    <ProgressBar
        android:id="@+id/progressBar"
        style="@style/CustomProgressBarStyle"
        android:background="@drawable/glassy_background"
        android:padding="20dp"
        android:visibility="gone" />
</FrameLayout>
```

This was the duplicate progress bar with box background that appeared on first access to home page.

### 5. MainActivity.kt Updates

**Removed**:
- `progressBar` variable declaration
- `progressBar?.visibility = View.VISIBLE` in onCreate
- `progressBar?.visibility = View.GONE` in fetchCurrentUserDetails

The MainActivity no longer manages its own progress bar since each fragment handles its own loading state.

## Benefits

### 1. Prevents Background Interaction
- Loading overlay now blocks all touch events on background content
- Users cannot accidentally tap buttons or interact with UI during loading
- Navigation tabs remain accessible (not blocked by overlay)

### 2. Automatic Loading Cancellation
- When user navigates away from a tab, loading is automatically cancelled
- Prevents loading state from persisting when switching tabs
- Uses Android lifecycle to detect when fragment stops

### 3. No Duplicate Progress Bars
- Removed the box-background progress bar from MainActivity
- Each fragment manages its own loading overlay
- Cleaner, more consistent loading UI

### 4. Lifecycle-Aware
- LoadingManager observes fragment lifecycle
- Automatically cleans up when fragment is destroyed
- Prevents memory leaks and orphaned loading states

## Usage in Other Fragments

To implement in other fragments:

```kotlin
class YourFragment : Fragment() {
    private var loadingManager: LoadingManager? = null

    override fun onCreateView(...): View {
        val view = inflater.inflate(R.layout.your_fragment, container, false)
        
        val loadingOverlay = view.findViewById<View>(R.id.loadingOverlay)
        loadingManager = LoadingManager(loadingOverlay, viewLifecycleOwner.lifecycle)
        
        return view
    }

    private fun showLoading() {
        loadingManager?.showLoading()
    }

    private fun hideLoading() {
        loadingManager?.hideLoading()
    }
}
```

And in the layout XML:

```xml
<LinearLayout
    android:id="@+id/loadingOverlay"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/glassy_background"
    android:clickable="true"
    android:focusable="true"
    android:gravity="center"
    android:orientation="vertical"
    android:visibility="gone">
    
    <ProgressBar ... />
    <TextView ... />
</LinearLayout>
```

## Testing Scenarios

1. **Background Interaction Blocked**
   - Start loading in any fragment
   - Try tapping buttons or scrolling
   - Verify that background is not interactive

2. **Navigation Tabs Accessible**
   - Start loading in Home tab
   - Verify navigation tabs are still tappable
   - Switch to another tab
   - Verify loading stops in Home tab

3. **Tab Switching Cancels Loading**
   - Start loading in Home tab
   - Switch to Transactions tab
   - Switch back to Home tab
   - Verify loading overlay is hidden

4. **No Duplicate Progress Bars**
   - Open app for first time
   - Navigate to Home tab
   - Verify only one progress bar appears (no box background)
