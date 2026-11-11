package com.supervision.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.presentation.restriction.RestrictionActivity
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
    lateinit var protectionPlanRepository: ProtectionPlanRepository

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

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // 1. Anti-Tamper: Block attempts to uninstall Supervision or deactivate Device Admin
            if (pkg == "com.android.settings" ||
                pkg == "com.google.android.packageinstaller" ||
                pkg == "com.android.packageinstaller") {

                serviceScope.launch {
                    try {
                        val activePlan = protectionPlanRepository.getActivePlan()
                        if (activePlan != null && !activePlan.isExpired) {
                            val rootNode = rootInActiveWindow
                            val hasSupervision = rootNode?.findAccessibilityNodeInfosByText("Supervision")?.isNotEmpty() == true
                            val hasDeactivate = rootNode?.findAccessibilityNodeInfosByText("Deactivate")?.isNotEmpty() == true ||
                                    rootNode?.findAccessibilityNodeInfosByText("Uninstall")?.isNotEmpty() == true ||
                                    rootNode?.findAccessibilityNodeInfosByText("Force stop")?.isNotEmpty() == true

                            if (hasSupervision && hasDeactivate) {
                                Timber.w("Anti-Tamper triggered: Prevented uninstall or admin deactivation attempt")
                                performGlobalAction(GLOBAL_ACTION_HOME)

                                val blockIntent = Intent(this@SupervisionAccessibilityService, RestrictionActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(RestrictionActivity.EXTRA_BLOCKED_PACKAGE, "Supervision Protection")
                                    putExtra(RestrictionActivity.EXTRA_RESTRICTION_TYPE, "ANTI_TAMPER")
                                    putExtra(RestrictionActivity.EXTRA_CATEGORY, "Uninstall & Tamper Protection Active")
                                }
                                startActivity(blockIntent)
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error evaluating anti-tamper")
                    }
                }
                return
            }

            // Never intercept our own app or system UI/launcher
            if (pkg == applicationContext.packageName ||
                pkg == "com.android.systemui" ||
                pkg.contains("launcher", ignoreCase = true)) {
                return
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
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
    }

    override fun onInterrupt() {
        Timber.w("SupervisionAccessibilityService interrupted")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

