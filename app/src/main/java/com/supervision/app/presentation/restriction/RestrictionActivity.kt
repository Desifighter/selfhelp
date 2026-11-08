package com.supervision.app.presentation.restriction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supervision.app.presentation.theme.SupervisionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RestrictionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_RESTRICTION_TYPE = "extra_restriction_type"
        const val EXTRA_CATEGORY = "extra_category"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: "Application"
        val restrictionType = intent.getStringExtra(EXTRA_RESTRICTION_TYPE) ?: "RESTRICTED"
        val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "Prohibited Content"

        setContent {
            SupervisionTheme {
                RestrictionScreen(
                    packageName = packageName,
                    restrictionType = restrictionType,
                    category = category,
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun RestrictionScreen(
    packageName: String,
    restrictionType: String,
    category: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFFB71C1C).copy(alpha = 0.2f), shape = RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Access Restricted",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Access Restricted",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You chose to protect your time and attention. This content is currently restricted under your Supervision plan.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0BEC5),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Restriction Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Target: $packageName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5)
                    )
                    Text(
                        text = "Category: $category",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5)
                    )
                    Text(
                        text = "Policy: Plan Active (Enforced)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF81C784)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Return to Safe Screen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
