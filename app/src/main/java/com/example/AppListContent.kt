package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.GeometricError
import com.example.ui.theme.GeometricOnError
import com.example.ui.theme.GeometricSuccess

@Composable
fun AppListContent(
    apps: List<AppInfo>,
    viewModel: AppManagerViewModel,
    context: Context
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            // Perhitungan Status Hijau/Merah:
            // Status Hijau jika Jaringan Device Aktif (Wifi/Seluler ON) DAN Toggle Data App Aktif
            val isDeviceConnected = viewModel.isDeviceNetworkActive(context)
            val isDataActive = isDeviceConnected && app.isDataOn

            AppItemCard(
                app = app,
                isDataActive = isDataActive,
                onForceStop = { viewModel.forceStopApp(app.packageName, app.uid, context) },
                onToggleData = { viewModel.toggleDataNetwork(app.packageName, app.uid, app.isDataOn) },
                onToggleAutoBoot = { viewModel.toggleAutoBoot(app.packageName, app.isAutoBootEnabled) },
                onInfo = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    context.startActivity(intent)
                },
                onUninstall = { viewModel.uninstallApp(app.packageName, context) }
            )
        }
    }
}

@Composable
fun AppItemCard(
    app: AppInfo,
    isDataActive: Boolean,
    onForceStop: () -> Unit,
    onToggleData: () -> Unit,
    onToggleAutoBoot: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    val statusColor = if (isDataActive) GeometricSuccess else GeometricError

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    AsyncImage(model = app.icon, contentDescription = app.appName, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Status DATA (Hijau jika salah satu/keduanya ON, Merah jika keduanya OFF)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = if (isDataActive) "DATA ON" else "DATA OFF",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(statusColor, CircleShape)
                        .clickable { onToggleData() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Data Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(
                        onClick = onToggleAutoBoot,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (app.isAutoBootEnabled) GeometricSuccess else Color.Gray,
                            contentColor = Color.White
                        ),
                        shape = CircleShape
                    ) {
                        Text("AUTO BOOT", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }

                    // KILL BUTTON (Tombol disembunyikan/disabled jika tidak running)
                    Button(
                        onClick = onForceStop,
                        enabled = app.isRunning,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeometricError,
                            contentColor = GeometricOnError,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.4f),
                            disabledContentColor = Color.LightGray
                        ),
                        shape = CircleShape
                    ) {
                        Text("KILL", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onInfo,
                        modifier = Modifier.height(22.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = CircleShape
                    ) {
                        Text("INFO", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!app.isSystemApp) {
                        OutlinedButton(
                            onClick = onUninstall,
                            modifier = Modifier.height(22.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GeometricError),
                            border = BorderStroke(1.dp, GeometricError.copy(alpha = 0.6f)),
                            shape = CircleShape
                        ) {
                            Text("DEL", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
