package com.supervision.app.presentation.schedules

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SchedulesScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Schedules & Protection Plans",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Protection plans enforce restrictions automatically across configured time windows and active days.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Current Active Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "• Pornography & Adult Content: Always Blocked (24/7)", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "• Instagram: 30 minutes daily limit (Resets at midnight)", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "• Social Media Doomscrolling: Restricted", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "• Active Days: Monday through Sunday", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "• Offline Enforcement: Enabled", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                    Text(text = "• Boot Persistence: Enabled", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}
