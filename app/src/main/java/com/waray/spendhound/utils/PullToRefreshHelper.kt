package com.waray.spendhound.utils

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.widget.NestedScrollView

class PullToRefreshHelper(
    private val scrollView: NestedScrollView,
    private val indicator: View,
    private val onRefresh: () -> Unit,
    touchTarget: View = scrollView
) {
    private val pullThreshold = 180f
    private var startY = 0f
    private var isPulling = false
    private var isRefreshing = false
    private val touchView: View = touchTarget

    private val isAtTop get() = !scrollView.canScrollVertically(-1)

    init { attach() }

    // Called from PullInterceptLayout.onInterceptTouchEvent — steals gesture from children
    fun onInterceptTouch(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isAtTop && !isRefreshing) {
                    startY = ev.rawY
                    isPulling = true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPulling && isAtTop && !isRefreshing) {
                    val dy = ev.rawY - startY
                    if (dy > 8f) return true // steal the gesture once clearly pulling down
                }
                return false
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach() {
        indicator.translationY = -200f
        indicator.visibility = View.GONE

        touchView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isAtTop && !isRefreshing) {
                        startY = event.rawY
                        isPulling = true
                    }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (isPulling && isAtTop && !isRefreshing && dy > 0) {
                        val progress = (dy / pullThreshold).coerceIn(0f, 1f)
                        indicator.visibility = View.VISIBLE
                        indicator.alpha = progress
                        indicator.scaleX = 0.5f + progress * 0.5f
                        indicator.scaleY = 0.5f + progress * 0.5f
                        indicator.translationY = -200f + (200f * progress)
                    } else if (!isAtTop || dy < 0) {
                        if (indicator.visibility == View.VISIBLE) hideIndicator()
                        isPulling = false
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPulling && !isRefreshing) {
                        val dy = event.rawY - startY
                        if (dy >= pullThreshold) {
                            triggerRefresh()
                        } else {
                            hideIndicator()
                        }
                    }
                    isPulling = false
                    false
                }
                else -> false
            }
        }
    }

    private fun triggerRefresh() {
        isRefreshing = true
        indicator.visibility = View.GONE
        indicator.translationY = -200f
        onRefresh()
    }

    fun stopRefreshing() {
        isRefreshing = false
        hideIndicator()
    }

    private fun hideIndicator() {
        ObjectAnimator.ofFloat(indicator, "alpha", indicator.alpha, 0f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    indicator.visibility = View.GONE
                    indicator.alpha = 1f
                    indicator.scaleX = 1f
                    indicator.scaleY = 1f
                    indicator.translationY = -200f
                }
            })
            start()
        }
    }
}
