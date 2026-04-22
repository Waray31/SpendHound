package com.waray.spendhound.utils

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val MAX_DIMENSION = 400
    private const val QUALITY = 60

    fun compressImage(contentResolver: ContentResolver, uri: Uri): ByteArray? {
        val input = contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(input) ?: return null
        input.close()
        val scaled = scaleBitmap(original)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        if (scaled != original) scaled.recycle()
        original.recycle()
        return out.toByteArray()
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= MAX_DIMENSION && h <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
