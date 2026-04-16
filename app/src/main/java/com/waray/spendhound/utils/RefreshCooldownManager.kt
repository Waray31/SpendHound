package com.waray.spendhound.utils

object RefreshCooldownManager {
    private const val COOLDOWN_MS = 3000L // 3 seconds cooldown
    private val lastRefreshTimes = mutableMapOf<String, Long>()

    fun canRefresh(tab: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastRefreshTimes[tab] ?: 0L
        return (now - last) > COOLDOWN_MS
    }

    fun markRefreshed(tab: String) {
        lastRefreshTimes[tab] = System.currentTimeMillis()
    }
}

