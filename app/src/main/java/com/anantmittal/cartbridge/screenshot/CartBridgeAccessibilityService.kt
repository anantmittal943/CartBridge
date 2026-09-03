package com.anantmittal.cartbridge.screenshot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility service that CartBridge uses exclusively to perform a
 * programmatic fling-up gesture on whatever is currently on screen.
 *
 * This is the same gesture mechanism used by Google TalkBack, Google Assistant,
 * and Android's own scroll capture — making the scroll behaviour identical
 * to what you'd get from flicking the screen with your finger.
 *
 * The service requires ONE-TIME user activation:
 *   Settings → Accessibility → Cart Bridge → Enable
 */
class CartBridgeAccessibilityService : AccessibilityService() {

    companion object {
        /** Singleton reference set when the service connects. */
        var instance: CartBridgeAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no-op */
    }

    override fun onInterrupt() { /* no-op */
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Performs a fast fling-up gesture in the centre of the screen.
     * The gesture duration (150 ms) mimics a real finger swipe so the target
     * app's native scroll momentum/physics kicks in — identical to the user
     * swiping up themselves.
     */
    fun performScrollGesture() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        val startX = screenWidth / 2f
        val startY = screenHeight * 0.70f  // start from 70% down
        val endY = screenHeight * 0.30f  // fling to 30% (upward)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            /* startTime= */ 0L,
            /* duration= */ 150L
        )

        dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null, null
        )
    }
}
