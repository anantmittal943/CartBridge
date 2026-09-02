package com.anantmittal.cartbridge.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

object ImageDownsampler {
    private const val TAG = "ImageDownsampler"
    
    // We limit max dimension to prevent OOM with long scrolling screenshots.
    // 2048 is typically sufficient for OCR to retain readability without huge memory cost.
    private const val MAX_DIMENSION = 2048

    suspend fun downsampleImage(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // Phase 1: Read bounds without loading pixels into memory
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            
            if (width == -1 || height == -1) {
                Log.e(TAG, "Failed to decode image bounds")
                return@withContext null
            }

            // Phase 2: Calculate inSampleSize
            var inSampleSize = 1
            if (height > MAX_DIMENSION || width > MAX_DIMENSION) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                // We divide by 2 aggressively to bring both dimensions under MAX_DIMENSION if possible
                while ((halfHeight / inSampleSize) >= MAX_DIMENSION || (halfWidth / inSampleSize) >= MAX_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            // Phase 3: Decode with sampled size
            val finalOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888 
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, finalOptions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downsampling image", e)
            null
        }
    }
}
