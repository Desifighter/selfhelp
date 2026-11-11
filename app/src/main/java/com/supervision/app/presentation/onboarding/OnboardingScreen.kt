package com.supervision.app.presentation.onboarding

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QueryBuilder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.supervision.app.domain.model.ProtectionMode
import com.supervision.app.domain.model.UnknownDomainPolicy

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.checkPermissions()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                canProceed = when (uiState.currentStep) {
                    1 -> uiState.isVpnPermissionGranted && uiState.isUsageStatsGranted && uiState.isOverlayPermissionGranted
                    else -> true
                },
                onNext = {
                    if (uiState.currentStep == uiState.totalSteps - 1) {
                        viewModel.activateProtection(onFinish)
                    } else {
                        viewModel.nextStep()
                    }
                },
                onBack = { viewModel.previousStep() }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StepIndicator(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(targetState = uiState.currentStep, label = "OnboardingStep") { step ->
                when (step) {
                    0 -> WelcomeStep()
                    1 -> PermissionsStep(
                        isVpnGranted = uiState.isVpnPermissionGranted,
                        isUsageGranted = uiState.isUsageStatsGranted,
                        isOverlayGranted = uiState.isOverlayPermissionGranted,
                        onRequestVpn = {
                            val intent = VpnService.prepare(context)
                            if (intent != null) {
                                vpnLauncher.launch(intent)
                            } else {
                                viewModel.checkPermissions()
                            }
                        },
                        onRequestUsage = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onRequestOverlay = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                    2 -> ProtectionModeStep(
                        selectedMode = uiState.selectedProtectionMode,
                        onSelectMode = { viewModel.setProtectionMode(it) }
                    )
                    3 -> UnknownDomainPolicyStep(
                        selectedPolicy = uiState.selectedUnknownPolicy,
                        onSelectPolicy = { viewModel.setUnknownDomainPolicy(it) }
                    )
                    4 -> PlanDurationStep(
                        selectedDays = uiState.selectedPlanDurationDays,
                        onSelectDays = { viewModel.setPlanDurationDays(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentStep) 24.dp else 10.dp, 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= currentStep) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome to Supervision",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Autonomous digital self-control designed to protect your focus, time, and attention.\n\nNo accountability partner required. You set the rules before temptation arrives.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun PermissionsStep(
    isVpnGranted: Boolean,
    isUsageGranted: Boolean,
    isOverlayGranted: Boolean,
    onRequestVpn: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Supervision runs completely offline and on-device. These permissions are required for filtering, limit enforcement, and instant blocking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        PermissionCard(
            title = "VPN Service (Local DNS Filtering)",
            description = "Intercepts DNS requests locally to block adult and distracting domains. No browsing data leaves your phone.",
            isGranted = isVpnGranted,
            icon = Icons.Default.Security,
            onRequest = onRequestVpn
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Usage Access (Screen Time Limits)",
            description = "Allows Supervision to count foreground minutes for apps like Instagram and detect when daily limits expire.",
            isGranted = isUsageGranted,
            icon = Icons.Default.QueryBuilder,
            onRequest = onRequestUsage
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Display Over Other Apps (Instant Blocker)",
            description = "Allows Supervision to display the full-screen restriction overlay immediately when a blocked app is launched.",
            isGranted = isOverlayGranted,
            icon = Icons.Default.Shield,
            onRequest = onRequestOverlay
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onRequest: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Granted",
                        tint = Color(0xFF2E7D32)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@Composable
private fun ProtectionModeStep(
    selectedMode: ProtectionMode,
    onSelectMode: (ProtectionMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Select Protection Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose the strength of enforcement for your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectMode(ProtectionMode.STANDARD) },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedMode == ProtectionMode.STANDARD)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == ProtectionMode.STANDARD,
                    onClick = { onSelectMode(ProtectionMode.STANDARD) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Standard Protection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Uses standard Android permissions, VPN, and background monitoring. Works on any Android device.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectMode(ProtectionMode.ENHANCED) },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedMode == ProtectionMode.ENHANCED)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedMode == ProtectionMode.ENHANCED,
                    onClick = { onSelectMode(ProtectionMode.ENHANCED) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Enhanced Protection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Enables Device Administration and anti-tamper monitoring to resist uninstallation during active protection.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun UnknownDomainPolicyStep(
    selectedPolicy: UnknownDomainPolicy,
    onSelectPolicy: (UnknownDomainPolicy) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Unknown Domain Policy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "What should happen when you visit a domain that has never been seen or classified before?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectPolicy(UnknownDomainPolicy.STRICT) },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedPolicy == UnknownDomainPolicy.STRICT)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPolicy == UnknownDomainPolicy.STRICT,
                    onClick = { onSelectPolicy(UnknownDomainPolicy.STRICT) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Strict Mode (Recommended)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Temporarily block unknown domains until classified. Prevents bypassing blocklists with newly created adult domains.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectPolicy(UnknownDomainPolicy.BALANCED) },
            colors = CardDefaults.cardColors(
                containerColor = if (selectedPolicy == UnknownDomainPolicy.BALANCED)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPolicy == UnknownDomainPolicy.BALANCED,
                    onClick = { onSelectPolicy(UnknownDomainPolicy.BALANCED) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Balanced Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Allow unknown domains while classifying them in the background. Automatically blocks and saves them if adult content is detected.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PlanDurationStep(
    selectedDays: Int,
    onSelectDays: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Protection Duration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Commit to a protection period. Decisions are made ahead of time to safeguard your willpower.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        listOf(7, 14, 30, 90).forEach { days ->
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDays(days) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedDays == days)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDays == days,
                        onClick = { onSelectDays(days) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$days Days Commitment",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OnboardingBottomBar(
    currentStep: Int,
    totalSteps: Int,
    canProceed: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 0) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Button(
            onClick = onNext,
            enabled = canProceed
        ) {
            Text(if (currentStep == totalSteps - 1) "Activate Protection" else "Next")
        }
    }
}

