package com.waray.spendhound

/**
 * Global state to track if groups have changed and need to be reloaded
 * in fragments/activities when they are next visited.
 */
object GroupsState {
    var lastUpdateTimestamp: Long = 0L

    fun notifyChange() {
        lastUpdateTimestamp = System.currentTimeMillis()
    }
}
