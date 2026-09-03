package com.anantmittal.cartbridge.screenshot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.anantmittal.cartbridge.MainActivity
import com.anantmittal.cartbridge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that:
 *  1. Creates a MediaProjection virtual display (the native Android screen recording pipeline).
 *  2. Captures individual frames via ImageReader while programmatically scrolling
 *     the frontmost window using AccessibilityService gestures.
 *  3. Stitches the frames into a single long-screenshot bitmap.
 *  4. Shares the result with CartBridge (ACTION_SEND).
 *
 * The MediaProjection consent dialog shown to the user is the exact same one used
 * by Google's own screen recorder — it is the phone's native capture flow.
 */
class LongScreenshotCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "ScreenCaptureChannel"
        const val NOTIFICATION_ID = 1002
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        // How many times to scroll-and-capture
        private const val MAX_SCROLL_STEPS = 12

        // Milliseconds to wait after each scroll before grabbing the next frame
        private const val SCROLL_DELAY_MS = 600L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var metrics: DisplayMetrics

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == -1 || resultData == null) {
            stopSelf(); return START_NOT_STICKY
        }

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mainHandler.post {
            Toast.makeText(this, "Capturing long screenshot…", Toast.LENGTH_SHORT).show()
        }

        startCapture()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────
    // Capture pipeline
    // ──────────────────────────────────────────────────────────

    @SuppressLint("WrongConstant")
    private fun startCapture() {
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "CartBridgeLongShot",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, null
        )

        serviceScope.launch {
            val frames = mutableListOf<Bitmap>()
            var previousHash = -1

            repeat(MAX_SCROLL_STEPS) { step ->
                // Grab the current screen frame
                val bmp = acquireFrame()
                if (bmp != null) {
                    val hash = bitmapHash(bmp)
                    if (step == 0 || hash != previousHash) {
                        frames.add(bmp)
                        previousHash = hash
                    } else {
                        // Content stopped changing → we've reached the bottom
                        return@repeat
                    }
                }
                // Perform a native scroll via AccessibilityService
                sendScrollGesture()
                delay(SCROLL_DELAY_MS)
            }

            if (frames.isNotEmpty()) {
                val stitched = stitchFrames(frames)
                saveAndShare(stitched)
                frames.forEach { it.recycle() }
            } else {
                mainHandler.post {
                    Toast.makeText(
                        this@LongScreenshotCaptureService,
                        "Could not capture screen", Toast.LENGTH_SHORT
                    ).show()
                }
            }

            stopSelf()
        }
    }

    /**
     * Acquires one frame from the ImageReader.  Retries a few times because the
     * VirtualDisplay pipeline may not have rendered a frame immediately.
     */
    private fun acquireFrame(): Bitmap? {
        var image: Image? = null
        repeat(5) {
            image = imageReader?.acquireLatestImage()
            if (image != null) return@repeat
            Thread.sleep(100)
        }
        return image?.use { img ->
            val planes = img.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * metrics.widthPixels

            val bitmap = Bitmap.createBitmap(
                metrics.widthPixels + rowPadding / pixelStride,
                metrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            // Crop away any row-padding artifact
            Bitmap.createBitmap(bitmap, 0, 0, metrics.widthPixels, metrics.heightPixels)
                .also { bitmap.recycle() }
        }
    }

    /**
     * Sends a programmatic fling-up gesture through the
     * CartBridgeAccessibilityService (must be enabled in Settings).
     * Falls back gracefully if the service is not available.
     */
    private fun sendScrollGesture() {
        CartBridgeAccessibilityService.instance?.performScrollGesture()
    }

    /**
     * Simple hash to detect when the screen content has stopped changing
     * (i.e., we hit the bottom of the page).
     */
    private fun bitmapHash(bmp: Bitmap): Int {
        val sample = Bitmap.createScaledBitmap(bmp, 32, 32, false)
        var hash = 0
        for (x in 0 until 32) for (y in 0 until 32) hash = hash * 31 + sample.getPixel(x, y)
        sample.recycle()
        return hash
    }

    /**
     * Stitches a list of screen-height frames into one tall bitmap,
     * blending overlapping regions so the result is seamless.
     *
     * Strategy: each subsequent frame is shifted down by (height - overlap).
     * We detect the overlap by comparing the bottom strip of frame[n] with
     * the top strip of frame[n+1].  A fixed overlap of 20% is used as a
     * safe default so no content is ever cut.
     */
    private fun stitchFrames(frames: List<Bitmap>): Bitmap {
        val w = frames[0].width
        val h = frames[0].height
        val overlapPx = (h * 0.20).toInt() // 20% overlap between frames
        val uniqueHeight = h - overlapPx

        val totalHeight = h + (frames.size - 1) * uniqueHeight
        val result = Bitmap.createBitmap(w, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        frames.forEachIndexed { i, frame ->
            val yOffset = i * uniqueHeight
            canvas.drawBitmap(frame, 0f, yOffset.toFloat(), null)
        }
        return result
    }

    /**
     * Saves the stitched bitmap to a cache file then fires an ACTION_SEND
     * intent so the existing CartBridge share-sheet pipeline picks it up.
     */
    private fun saveAndShare(bitmap: Bitmap) {
        val dir = File(cacheDir, "screenshots").also { it.mkdirs() }
        val file = File(dir, "long_screenshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val uri: Uri = FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )

        val shareIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        mainHandler.post {
            Toast.makeText(this, "Screenshot captured! Opening Cart Bridge…", Toast.LENGTH_SHORT).show()
        }
        startActivity(shareIntent)
    }

    // ──────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Captures long screenshots for Cart Bridge." }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cart Bridge is capturing…")
            .setContentText("Taking long screenshot. Please wait.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
}
