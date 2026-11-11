package com.supervision.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.service.monitoring.AppBlocker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * SupervisionAccessibilityService — Ultra-fast 0ms event interceptor.
 * Ejects restricted applications before they can render or settle into the task stack,
 * preventing them from running in the background or lingering in recent apps.
 */
@AndroidEntryPoint
class SupervisionAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appRestrictionRepository: AppRestrictionRepository

    @Inject
    lateinit var appBlocker: AppBlocker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("SupervisionAccessibilityService connected and ready")
        serviceScope.launch {
            try {
                appRestrictionRepository.initializeEssentialApps()
            } catch (e: Exception) {
                Timber.e(e, "Error initializing apps in AccessibilityService")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // Never intercept our own app or system UI/launcher
            if (pkg == applicationContext.packageName ||
                pkg == "com.android.systemui" ||
                pkg.contains("launcher", ignoreCase = true)) {
                return
            }

            serviceScope.launch {
                val restriction = appRestrictionRepository.getRestriction(pkg)
                if (restriction != null) {
                    // Essential enterprise, communication, payment & transit apps must NEVER be blocked
                    if (restriction.isEssential || restriction.restrictionType == RestrictionType.ALWAYS_ALLOWED) {
                        return@launch
                    }

                    if (restriction.restrictionType == RestrictionType.COMPLETELY_BLOCKED) {
                        Timber.i("AccessibilityService detected completely blocked app: $pkg")

                        // 1. Immediately send HOME to abort the window launch
                        performGlobalAction(GLOBAL_ACTION_HOME)

                        // 2. Trigger overlay and process termination via AppBlocker
                        appBlocker.blockApp(this@SupervisionAccessibilityService, pkg)

                        // 3. Force-terminate the process immediately
                        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        am?.killBackgroundProcesses(pkg)

                        // 4. Repeated termination after window settles
                        delay(200)
                        am?.killBackgroundProcesses(pkg)
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Timber.w("SupervisionAccessibilityService interrupted")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

