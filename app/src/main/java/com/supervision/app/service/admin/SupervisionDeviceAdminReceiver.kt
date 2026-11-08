package com.supervision.app.service.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class SupervisionDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Timber.i("Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Timber.w("Device admin disabled — enhanced protection reduced")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Disabling device admin will reduce protection. Your active restrictions will continue but enhanced protection features will be unavailable."
    }
}
