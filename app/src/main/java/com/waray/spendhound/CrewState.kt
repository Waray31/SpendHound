package com.waray.spendhound

/**
 * Global state to track if crew has changed and need to be reloaded
 * in fragments when they are next visited.
 */
object CrewState {
    var lastUpdateTimestamp: Long = 0L

    fun notifyChange() {
        lastUpdateTimestamp = System.currentTimeMillis()
    }
}
