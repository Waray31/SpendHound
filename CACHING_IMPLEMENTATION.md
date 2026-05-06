# Caching Implementation Summary

## Overview
All main fragments now implement instant cached data display with background refresh:
- **HomeFragment** - spending, debt, receivable, analytics
- **TransactionsFragment** - transaction list
- **BorrowFragment** - owed/debt lists
- **ProfileFragment** - already implemented (reference)

## Pattern Applied

### 1. Data Flow
```
Repository (cachedFlow) → ViewModel (StateFlow) → Fragment (applyCachedState + observer)
```

### 2. Fragment Changes

#### Added to each fragment:
- `hasLoadedOnce` flag - tracks if cached data was shown
- `applyCachedState()` - reads `viewModel.stateFlow.value` synchronously in `onViewCreated`
- Silent observer - skips UI updates when data unchanged, preserves scroll position

#### Key Methods:

**applyCachedState()** - called BEFORE observeViewModel()
```kotlin
private fun applyCachedState() {
    val cached = viewModel.data.value ?: return
    // Apply cached data to UI instantly (same frame)
    hasLoadedOnce = true
}
```

**observeViewModel()** - silent updates only
```kotlin
private fun observeViewModel() {
    viewLifecycleOwner.lifecycleScope.launch {
        viewModel.data.collectLatest { data ->
            data ?: return@collectLatest
            // Skip if data unchanged
            if (data == cachedData) return@collectLatest
            // Preserve scroll position
            val scrollY = scrollView?.scrollY ?: 0
            // Update UI
            scrollView?.post { scrollView.scrollTo(0, scrollY) }
        }
    }
}
```

## Behavior

### First Visit (Cold Start)
1. `applyCachedState()` - no cached data, returns immediately
2. `observeViewModel()` - waits for network fetch
3. Skeleton shows until data arrives
4. Data displayed, cached to Room DB

### Revisit (Warm Start)
1. `applyCachedState()` - reads cached StateFlow.value, displays instantly (0ms)
2. `observeViewModel()` - background refresh if stale (>5min)
3. If fresh data differs, silently updates without scroll jump
4. If unchanged, no UI update occurs

### Pull to Refresh
1. Calls `viewModel.invalidate(userId)` - clears cache
2. Forces fresh network fetch
3. Updates UI when complete
4. Preserves scroll position

## Cache TTL (Time To Live)

Defined in `CacheKeys.kt`:
- Home: 5 minutes
- Transactions: 5 minutes
- Borrows: 5 minutes
- Profile: 10 minutes
- Crew: 5 minutes

## Files Modified

### HomeFragment.kt
- Added `hasLoadedOnce`, `applyCachedState()`
- Updated `observeViewModel()` - silent updates only
- Updated `updateRecentTransactionsUI()` - scroll preservation

### TransactionsFragment.kt
- Added `hasLoadedOnce`, `applyCachedState()`
- Updated `observeViewModel()` - scroll preservation
- Updated `showLoading()` - guards skeleton behind `hasLoadedOnce`

### BorrowFragment.kt
- Added `hasLoadedOnce`, `applyCachedState()`
- Updated `observeViewModel()` - silent no-op when unchanged

### ProfileFragment.kt
- Already implemented (reference implementation)
- No changes needed

## Repository Layer (Already Implemented)

All repositories use `AppDatabase.cachedFlow()`:
```kotlin
fun getData(userId: Long): Flow<Data> = db.cachedFlow(
    key = CacheKeys.key(userId),
    staleTtlMs = CacheKeys.STALE_TTL,
    type = typeOf<Data>()
) {
    // Network fetch logic
}
```

## Benefits

1. **Zero blank frames** - cached data shows instantly on tab switch
2. **Silent updates** - background refresh doesn't disrupt user
3. **Scroll preservation** - position maintained during refresh
4. **Reduced network calls** - only fetches when stale
5. **Offline support** - cached data available without network
