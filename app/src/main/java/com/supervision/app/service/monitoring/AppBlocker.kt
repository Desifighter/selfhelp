package com.supervision.app.service.monitoring

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.supervision.app.R
import com.supervision.app.SupervisionApp
import com.supervision.app.presentation.restriction.RestrictionActivity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlocker @Inject constructor() {

    private val lastBlockTime = ConcurrentHashMap<String, Long>()

    fun blockApp(context: Context, packageName: String) {
        val now = System.currentTimeMillis()
        val last = lastBlockTime[packageName] ?: 0L
        if (now - last < 2000L) {
            // Debounce rapid re-triggers within 2 seconds
            return
        }
        lastBlockTime[packageName] = now
        Timber.i("Enforcing blocking overlay for: $packageName")

        // 1. Send HOME intent to dismiss restricted application from foreground
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to navigate to HOME")
        }

        // 2. Launch full-screen RestrictionActivity overlay
        val blockIntent = Intent(context, RestrictionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(RestrictionActivity.EXTRA_BLOCKED_PACKAGE, packageName)
            putExtra(RestrictionActivity.EXTRA_RESTRICTION_TYPE, "APP_BLOCKED")
            putExtra(RestrictionActivity.EXTRA_CATEGORY, "Social Media / Screen Time Limit")
        }
        try {
            context.startActivity(blockIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start RestrictionActivity directly")
        }

        // 3. Post fullScreenIntent high-priority notification to guarantee display over restricted app on Android 14/15/16
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
                .setContentText("$packageName is restricted under your active plan")
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
}


