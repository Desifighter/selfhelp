package com.supervision.app.presentation.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
                text = "Restricted platforms are paused with greyscale indicators. Work and communication essentials remain permanently protected.",
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
    val isBlocked = app.restrictionType == RestrictionType.COMPLETELY_BLOCKED
    val isTimeLimited = app.restrictionType == RestrictionType.TIME_LIMITED

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBlocked) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImage(
                        packageName = app.packageName,
                        isGreyedOut = isBlocked,
                        modifier = Modifier.size(44.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isBlocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                            )

                            if (isBlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF616161))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PAUSED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (app.isEssential) {
                    Text(
                        text = "Work Protected",
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
                        label = { Text("Blocked (Grey)") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    isGreyedOut: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            val fallback = ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
            fallback?.let { d ->
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                d.setBounds(0, 0, canvas.width, canvas.height)
                d.draw(canvas)
                bmp.asImageBitmap()
            }
        }
    }

    if (bitmap != null) {
        val colorFilter = if (isGreyedOut) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else null

        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            colorFilter = colorFilter
        )
    }
}


