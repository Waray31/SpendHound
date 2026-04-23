package com.waray.spendhound

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.waray.spendhound.data.local.AppDatabase
import java.io.File

class SpendHoundApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        DeclareDatabase.initialize(this)
        setupCoil()
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
