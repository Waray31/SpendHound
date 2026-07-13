package com.waray.spendhound

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.waray.spendhound.data.local.AppDatabase
import com.waray.spendhound.utils.NetworkMonitor
import com.waray.spendhound.workers.PendingTransactionSyncWorker
import java.io.File

class SpendHoundApplication : Application() {

    companion object {
        lateinit var instance: SpendHoundApplication
            private set

        fun scheduleSync(context: android.content.Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<PendingTransactionSyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "PendingTransactionSync",
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (e: Exception) {
                android.util.Log.e("SpendHoundApp", "Failed to schedule sync: ${e.message}")
            }
        }
    }

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("SpendHoundApp", "onCreate: START")
        
        try {
            DeclareDatabase.initialize(this)
        } catch (t: Throwable) {
            android.util.Log.e("SpendHoundApp", "onCreate: Database initialization failed: ${t.message}", t)
        }

        try {
            NetworkMonitor.start(this)
        } catch (t: Throwable) {
            android.util.Log.e("SpendHoundApp", "onCreate: NetworkMonitor start failed: ${t.message}", t)
        }

        try {
            setupCoil()
        } catch (t: Throwable) {
            android.util.Log.e("SpendHoundApp", "onCreate: Coil setup failed: ${t.message}", t)
        }
        
        // Initial sync on app start
        try {
            scheduleSync(this)
        } catch (t: Throwable) {
            android.util.Log.e("SpendHoundApp", "onCreate: Initial sync failed: ${t.message}", t)
        }
        android.util.Log.d("SpendHoundApp", "onCreate: FINISH")
    }

    override fun onTerminate() {
        super.onTerminate()
        NetworkMonitor.stop()
    }

    private fun setupCoil() {
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 25% of app RAM
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .respectCacheHeaders(false) // Always cache Supabase URLs
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
