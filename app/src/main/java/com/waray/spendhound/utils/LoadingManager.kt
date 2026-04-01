package com.waray.spendhound.utils

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * Manages loading overlay state for fragments and activities.
 * Prevents user interaction with background content during loading.
 * Automatically cancels loading when fragment/activity is stopped.
 */
class LoadingManager(
    private val loadingOverlay: View?,
    private val lifecycle: Lifecycle
) : LifecycleObserver {

    private var pendingLoads = 0
    private var isStopped = false

    init {
        lifecycle.addObserver(this)
    }

    /**
     * Show loading overlay and block background interaction
     */
    fun showLoading() {
        if (isStopped) return
        pendingLoads++
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
        }
    }

    /**
     * Hide loading overlay if no pending loads remain
     */
    fun hideLoading() {
        if (isStopped) return
        pendingLoads = maxOf(0, pendingLoads - 1)
        if (pendingLoads == 0) {
            loadingOverlay?.visibility = View.GONE
        }
    }

    /**
     * Force hide loading overlay regardless of pending loads
     */
    fun forceHide() {
        pendingLoads = 0
        loadingOverlay?.visibility = View.GONE
    }

    /**
     * Cancel all loading when fragment/activity is stopped
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        isStopped = true
        forceHide()
    }

    /**
     * Reset stopped state when fragment/activity resumes
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        isStopped = false
    }

    /**
     * Cleanup when fragment/activity is destroyed
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        lifecycle.removeObserver(this)
        forceHide()
    }
}
