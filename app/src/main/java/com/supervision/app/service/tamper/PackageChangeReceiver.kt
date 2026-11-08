package com.supervision.app.service.tamper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.supervision.app.data.repository.BlockedRequestRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class PackageChangeReceiver : BroadcastReceiver() {

    @Inject
    lateinit var blockedRequestRepository: BlockedRequestRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val packageName = intent?.data?.schemeSpecificPart ?: return

        when (intent.action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                Timber.w("Package removed: $packageName")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        blockedRequestRepository.recordTamperEvent(
                            type = "PACKAGE_REMOVED",
                            description = "Package $packageName was removed",
                            severity = "LOW"
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_PACKAGE_CHANGED -> {
                Timber.w("Package changed: $packageName")
            }
        }
    }
}

