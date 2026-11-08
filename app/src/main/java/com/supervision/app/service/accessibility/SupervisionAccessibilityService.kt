package com.supervision.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.supervision.app.data.repository.AppRestrictionRepository
import com.supervision.app.domain.model.RestrictionType
import com.supervision.app.presentation.restriction.RestrictionActivity
import com.supervision.app.service.monitoring.AppBlocker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SupervisionAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appBlocker: AppBlocker

    @Inject
    lateinit var appRestrictionRepository: AppRestrictionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var lastInterceptTime = 0L

    companion object {
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.brave.browser",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.duckduckgo.mobile.android"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        Timber.i("SupervisionAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // 1. YouTube App instant interception
        if (packageName == "com.google.android.youtube") {
            serviceScope.launch(Dispatchers.IO) {
                val restriction = appRestrictionRepository.getRestriction(packageName)
                if (restriction?.restrictionType == RestrictionType.COMPLETELY_BLOCKED) {
                    launch(Dispatchers.Main) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        appBlocker.blockApp(this@SupervisionAccessibilityService, packageName)
                    }
                }
            }
            return
        }

        // 2. In-browser YouTube Shorts detection
        if (BROWSER_PACKAGES.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return
            inspectNodeForShorts(rootNode)
        }
    }

    private fun inspectNodeForShorts(node: AccessibilityNodeInfo) {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""

        val combined = "$text $contentDesc $viewId".lowercase()

        if (containsYouTubeShorts(combined)) {
            val now = System.currentTimeMillis()
            if (now - lastInterceptTime > 2000L) {
                lastInterceptTime = now
                Timber.w("Detected YouTube Shorts in browser: $combined")

                // Dismiss browser page
                performGlobalAction(GLOBAL_ACTION_BACK)

                // Launch restriction overlay
                val intent = Intent(this, RestrictionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(RestrictionActivity.EXTRA_BLOCKED_PACKAGE, "YouTube Shorts")
                    putExtra(RestrictionActivity.EXTRA_RESTRICTION_TYPE, "URL_BLOCKED")
                    putExtra(RestrictionActivity.EXTRA_CATEGORY, "YouTube Shorts (Prohibited)")
                }
                startActivity(intent)
            }
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            inspectNodeForShorts(child)
        }
    }

    private fun containsYouTubeShorts(content: String): Boolean {
        return content.contains("youtube.com/shorts") ||
                content.contains("m.youtube.com/shorts") ||
                content.contains("youtu.be/") ||
                content.contains("/shorts/")
    }

    override fun onInterrupt() {
        Timber.w("SupervisionAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

