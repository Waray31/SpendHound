package com.waray.spendhound.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout

class PullInterceptLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onInterceptCallback: ((MotionEvent) -> Boolean)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return onInterceptCallback?.invoke(ev) ?: super.onInterceptTouchEvent(ev)
    }
}
