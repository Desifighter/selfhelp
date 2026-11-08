package com.supervision.app.service.monitoring

import android.content.Context
import android.content.Intent
import com.supervision.app.presentation.restriction.RestrictionActivity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBlocker @Inject constructor() {

    fun blockApp(context: Context, packageName: String) {
        Timber.i("Launching restriction screen for: $packageName")
        val intent = Intent(context, RestrictionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(RestrictionActivity.EXTRA_BLOCKED_PACKAGE, packageName)
            putExtra(RestrictionActivity.EXTRA_RESTRICTION_TYPE, "APP_BLOCKED")
        }
        context.startActivity(intent)
    }
}

