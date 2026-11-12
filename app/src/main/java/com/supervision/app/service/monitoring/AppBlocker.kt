package com.supervision.app.service.monitoring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.presentation.restriction.RestrictionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlocker @Inject constructor(
    private val blockingOverlayManager: BlockingOverlayManager
) {

    private val lastBlockTime = ConcurrentHashMap<String, Long>()

    fun blockApp(
        context: Context,
        packageName: String,
        appName: String = packageName,
        reason: String = "Social Media / Screen Time Limit"
    ) {
        val now = System.currentTimeMillis()
        val last = lastBlockTime[packageName] ?: 0L

        val hasOverlayPermission = Settings.canDrawOverlays(context)

        // 1. Show Layer 2038 overlay (covers Fullscreen, Freeform, PIP, and Multi-Window instantly)
        if (hasOverlayPermission) {
            blockingOverlayManager.showOverlay(packageName, appName, reason)
        }

        if (now - last < 2000L) {
            // Debounce rapid re-triggers within 2 seconds
            return
        }
        lastBlockTime[packageName] = now
        Timber.i("Enforcing blocking & task eviction for: $packageName")

        // 2. Send HOME intent to dismiss restricted application from foreground immediately
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to HOME")
        }

        // 3. Fallback: Only launch full-screen RestrictionActivity if SYSTEM_ALERT_WINDOW is not available
        // to prevent stacking multiple duplicate "Return to Home" screens
        if (!hasOverlayPermission) {
            val blockIntent = Intent(context, RestrictionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(RestrictionActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                putExtra(RestrictionActivity.EXTRA_RESTRICTION_TYPE, "APP_BLOCKED")
                putExtra(RestrictionActivity.EXTRA_CATEGORY, reason)
            }
            try {
                context.startActivity(blockIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start RestrictionActivity directly")
            }

            try {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    packageName.hashCode(),
                    blockIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, SupervisionApp.CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_shield)
                    .setContentTitle("Access Restricted")
                    .setContentText("$appName is restricted under your active plan")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(packageName.hashCode(), notification)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send fullScreenIntent notification")
            }
        }

        // 4. Forcefully kill the background process once dismissed from foreground
        CoroutineScope(Dispatchers.IO).launch {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            delay(150)
            try {
                am?.killBackgroundProcesses(packageName)
                Timber.i("Killed background process at +150ms for: $packageName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to kill background process")
            }
            delay(500)
            try {
                am?.killBackgroundProcesses(packageName)
                Timber.i("Killed background process at +650ms for: $packageName")
            } catch (e: Exception) {
                Timber.e(e, "Failed to kill background process")
            }
        }
    }
}
