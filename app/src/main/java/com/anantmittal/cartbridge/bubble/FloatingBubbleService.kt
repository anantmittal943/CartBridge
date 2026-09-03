package com.anantmittal.cartbridge.bubble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.anantmittal.cartbridge.MainActivity
import com.anantmittal.cartbridge.R
import com.anantmittal.cartbridge.screenshot.ScreenCaptureRequestActivity
import kotlin.math.abs

class FloatingBubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "FloatingBubbleChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP_BUBBLE"
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var menuView: View? = null

    // Bubble drag tracking
    private var initialX = 0;
    private var initialY = 0
    private var initialTouchX = 0f;
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { windowManager.removeView(it) }
        menuView?.let { windowManager.removeView(it) }
    }

    // ──────────────────────────────────────────────────────────
    // Bubble
    // ──────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showBubble() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_CartBridge)
        bubbleView = LayoutInflater.from(themedContext).inflate(R.layout.view_floating_bubble, null)

        bubbleView!!.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) toggleMenu()
                    true
                }

                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    // ──────────────────────────────────────────────────────────
    // Popup Menu
    // ──────────────────────────────────────────────────────────

    @SuppressLint("InflateParams")
    private fun toggleMenu() {
        if (menuView != null) {
            dismissMenu(); return
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_CartBridge)
        menuView = LayoutInflater.from(themedContext).inflate(R.layout.view_bubble_menu, null)

        // Long Screenshot
        menuView!!.findViewById<LinearLayout>(R.id.menu_long_screenshot).setOnClickListener {
            dismissMenu()
            // Short delay so the menu disappears before capture begins
            bubbleView?.postDelayed({ requestLongScreenshot() }, 300)
        }

        // Open CartBridge
        menuView!!.findViewById<LinearLayout>(R.id.menu_open_cartbridge).setOnClickListener {
            dismissMenu()
            val i = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(i)
        }

        // Close bubble
        menuView!!.findViewById<LinearLayout>(R.id.menu_close_bubble).setOnClickListener {
            stopSelf()
        }

        windowManager.addView(menuView, menuParams)
    }

    private fun dismissMenu() {
        menuView?.let { windowManager.removeView(it); menuView = null }
    }

    // ──────────────────────────────────────────────────────────
    // Screenshot
    // ──────────────────────────────────────────────────────────

    private fun requestLongScreenshot() {
        val intent = Intent(this, ScreenCaptureRequestActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    // ──────────────────────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Cart Bridge Bubble",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Keeps the floating Cart Bridge bubble active." }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cart Bridge is active")
            .setContentText("Floating bubble is running. Tap to open app.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .addAction(0, "Dismiss", stopPending)
            .build()
    }
}
