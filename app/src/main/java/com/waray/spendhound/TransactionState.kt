package com.waray.spendhound

/**
 * Global state to track if transactions have changed and need to be reloaded
 * in fragments when they are next visited.
 */
object TransactionState {
    var lastUpdateTimestamp: Long = 0L

    fun notifyChange() {
        lastUpdateTimestamp = System.currentTimeMillis()
    }
}
