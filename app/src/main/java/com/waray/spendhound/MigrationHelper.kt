package com.waray.spendhound

import android.util.Log

/**
 * Helper class for migrating existing data.
 * Note: Firebase-specific migration code has been removed as the project has moved to Supabase.
 */
object MigrationHelper {
    private const val TAG = "MigrationHelper"

    interface MigrationCallback {
        fun onComplete(migratedCount: Int)
        fun onError(error: String?)
    }
}
