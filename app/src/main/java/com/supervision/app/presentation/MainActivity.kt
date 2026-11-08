package com.supervision.app.presentation

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.supervision.app.data.repository.ProtectionPlanRepository
import com.supervision.app.presentation.navigation.AppNavGraph
import com.supervision.app.presentation.navigation.Screen
import com.supervision.app.presentation.theme.SupervisionTheme
import com.supervision.app.service.monitoring.UsageMonitoringService
import com.supervision.app.service.vpn.SupervisionVpnService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var protectionPlanRepository: ProtectionPlanRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SupervisionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val activePlan = protectionPlanRepository.getActivePlan()
                        if (activePlan != null && activePlan.isActive) {
                            startServicesIfPermitted()
                            startDestination = Screen.Dashboard.route
                        } else {
                            startDestination = Screen.Onboarding.route
                        }
                    }

                    startDestination?.let { destination ->
                        AppNavGraph(
                            navController = navController,
                            startDestination = destination
                        )
                    }
                }
            }
        }
    }

    private fun startServicesIfPermitted() {
        try {
            if (VpnService.prepare(this) == null) {
                val vpnIntent = Intent(this, SupervisionVpnService::class.java).apply {
                    action = SupervisionVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(vpnIntent)
                } else {
                    startService(vpnIntent)
                }
                Timber.i("Started SupervisionVpnService from MainActivity")
            }

            val monitoringIntent = Intent(this, UsageMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(monitoringIntent)
            } else {
                startService(monitoringIntent)
            }
            Timber.i("Started UsageMonitoringService from MainActivity")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start background services from MainActivity")
        }
    }
}

