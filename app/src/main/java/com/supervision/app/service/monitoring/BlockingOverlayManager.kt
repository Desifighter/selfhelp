package com.supervision.app.service.monitoring

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.supervision.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a high-priority fullscreen SYSTEM_ALERT_WINDOW overlay (Layer 2038).
 * Renders above all application windows including Freeform / Floating Windows,
 * Split-screen, and Picture-in-Picture on Android 14/15/16.
 */
@Singleton
class BlockingOverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: FrameLayout? = null
    private var isOverlayAdded = false
    private var currentBlockedPackage: String? = null

    private var subtitleTextView: TextView? = null
    private val lastDismissedTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun getLastDismissedTime(packageName: String): Long = lastDismissedTimes[packageName] ?: 0L

    fun showOverlay(packageName: String, appName: String, reason: String = "Restricted under active self-control plan") {
        if (!Settings.canDrawOverlays(context)) {
            Timber.w("SYSTEM_ALERT_WINDOW permission not granted; cannot display overlay")
            return
        }

        mainHandler.post {
            try {
                if (isOverlayAdded && overlayView != null) {
                    if (currentBlockedPackage != packageName) {
                        currentBlockedPackage = packageName
                        subtitleTextView?.text = "$appName is restricted\n($reason)"
                    }
                    return@post
                }

                currentBlockedPackage = packageName
                val view = createOverlayView(packageName, appName, reason)
                overlayView = view

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(view, params)
                isOverlayAdded = true
                Timber.i("Fullscreen blocking overlay displayed for: $packageName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to display blocking overlay")
            }
        }
    }

    fun dismissOverlay() {
        mainHandler.post {
            try {
                if (isOverlayAdded && overlayView != null) {
                    windowManager?.removeView(overlayView)
                    Timber.i("Fullscreen blocking overlay dismissed for: $currentBlockedPackage")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove blocking overlay")
            } finally {
                overlayView = null
                isOverlayAdded = false
                currentBlockedPackage = null
                subtitleTextView = null
            }
        }
    }

    fun isShowing(): Boolean = isOverlayAdded

    fun getCurrentlyBlockedPackage(): String? = currentBlockedPackage

    private fun createOverlayView(packageName: String, appName: String, reason: String): FrameLayout {
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    handleDismissAndExit()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Color.parseColor("#EE090D16")) // Deep dark semi-translucent backdrop
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dpToPx(28)
            setPadding(pad, pad, pad, pad)

            val cardBackground = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B")) // Slate 800 card
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155")) // Slate 700 border
            }
            background = cardBackground
        }

        val containerParams = FrameLayout.LayoutParams(
            dpToPx(340),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )

        // 1. Red Shield Icon
        val shieldIcon = ImageView(context).apply {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_shield)?.mutate()
            drawable?.setTint(Color.parseColor("#EF4444")) // Crimson red
            setImageDrawable(drawable)
            layoutParams = LinearLayout.LayoutParams(dpToPx(72), dpToPx(72)).apply {
                bottomMargin = dpToPx(20)
            }
        }
        container.addView(shieldIcon)

        // 2. Title
        val titleView = TextView(context).apply {
            text = "Access Restricted"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#F8FAFC")) // Slate 50
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(10)
            }
        }
        container.addView(titleView)

        // 3. Subtitle / Reason
        val subView = TextView(context).apply {
            text = "$appName is restricted\n($reason)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(3).toFloat(), 1.1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(24)
            }
        }
        subtitleTextView = subView
        container.addView(subView)

        // 4. Return to Home Button
        val homeButton = Button(context).apply {
            text = "Return to Home"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)

            val buttonBackground = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626")) // Red 600
                cornerRadius = dpToPx(12).toFloat()
            }
            background = buttonBackground

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(48)
            )

            setOnClickListener {
                handleDismissAndExit()
            }
        }
        container.addView(homeButton)

        root.addView(container, containerParams)
        return root
    }

    private fun handleDismissAndExit() {
        val targetPkg = currentBlockedPackage
        if (!targetPkg.isNullOrBlank()) {
            lastDismissedTimes[targetPkg] = System.currentTimeMillis()
        }
        dismissOverlay()

        // 1. Navigate to HOME
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start HOME intent from overlay")
        }

        // 2. Kill background process of target package
        if (!targetPkg.isNullOrBlank()) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                am?.killBackgroundProcesses(targetPkg)
                Timber.i("Overlay dismissed: killed background process for: $targetPkg")
            } catch (e: Exception) {
                Timber.e(e, "Failed to kill process on overlay dismiss")
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

