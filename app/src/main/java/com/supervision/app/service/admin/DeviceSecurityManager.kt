package com.supervision.app.service.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.supervision.app.data.repository.BlockedRequestRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeviceSecurityManager manages Enhanced Protection capabilities via DevicePolicyManager:
 * - Checks Device Admin & Device Owner status
 * - Locks uninstallation when Device Owner is provisioned
 * - Enforces camera or screen lock policies if requested
 * - Logs any administrative permission tampering
 */
@Singleton
class DeviceSecurityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blockedRequestRepository: BlockedRequestRepository
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, SupervisionDeviceAdminReceiver::class.java)

    fun isAdminActive(): Boolean {
        return dpm.isAdminActive(adminComponent)
    }

    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun applyUninstallBlock() {
        if (isDeviceOwner()) {
            try {
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                Timber.i("Uninstall block enabled via Device Owner")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set uninstall block")
            }
        } else {
            Timber.d("Device Owner not provisioned — uninstall block not supported in standard mode")
        }
    }

    fun releaseUninstallBlock() {
        if (isDeviceOwner()) {
            try {
                dpm.setUninstallBlocked(adminComponent, context.packageName, false)
                Timber.i("Uninstall block released")
            } catch (e: Exception) {
                Timber.e(e, "Failed to release uninstall block")
            }
        }
    }

    suspend fun recordAdminRevocationAttempt() {
        blockedRequestRepository.recordTamperEvent(
            type = "ADMIN_REVOCATION_ATTEMPT",
            description = "Attempt to disable device administration detected",
            severity = "HIGH"
        )
    }
}

