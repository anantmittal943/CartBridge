package com.anantmittal.cartbridge.screenshot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * A fully transparent, no-UI Activity whose sole purpose is to prompt the user
 * with the native Android "Start recording / Share screen?" consent dialog
 * (MediaProjection). It then hands the result to LongScreenshotCaptureService.
 */
class ScreenCaptureRequestActivity : Activity() {

    companion object {
        const val REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove window background so it appears truly transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // This shows Android's native screen-capture permission dialog
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CODE
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // User approved → start the capture service with the projection token
                val serviceIntent = Intent(this, LongScreenshotCaptureService::class.java).apply {
                    putExtra(LongScreenshotCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(LongScreenshotCaptureService.EXTRA_RESULT_DATA, data)
                }
                startForegroundService(serviceIntent)
            }
            finish() // Close the transparent activity immediately
        }
    }
}
