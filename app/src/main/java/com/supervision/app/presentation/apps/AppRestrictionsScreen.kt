package com.supervision.app.presentation.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.supervision.app.domain.model.AppRestriction
import com.supervision.app.domain.model.RestrictionType

@Composable
fun AppRestrictionsScreen(
    viewModel: AppRestrictionsViewModel
) {
    val apps by viewModel.appRestrictions.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Application Restrictions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Essential communication and work applications remain always allowed. Restrict distracting platforms below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(apps.size) { index ->
            val app = apps[index]
            AppRestrictionCard(
                app = app,
                onUpdateType = { viewModel.updateRestrictionType(app, it) }
            )
        }
    }
}

@Composable
private fun AppRestrictionCard(
    app: AppRestriction,
    onUpdateType: (RestrictionType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (app.isEssential) {
                    Text(
                        text = "Essential (Protected)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!app.isEssential) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = app.restrictionType == RestrictionType.ALWAYS_ALLOWED,
                        onClick = { onUpdateType(RestrictionType.ALWAYS_ALLOWED) },
                        label = { Text("Allowed") }
                    )
                    FilterChip(
                        selected = app.restrictionType == RestrictionType.TIME_LIMITED,
                        onClick = { onUpdateType(RestrictionType.TIME_LIMITED) },
                        label = { Text(if (app.dailyLimitMinutes != null) "${app.dailyLimitMinutes}m Limit" else "Time Limited") }
                    )
                    FilterChip(
                        selected = app.restrictionType == RestrictionType.COMPLETELY_BLOCKED,
                        onClick = { onUpdateType(RestrictionType.COMPLETELY_BLOCKED) },
                        label = { Text("Blocked") }
                    )
                }
            }
        }
    }
}

