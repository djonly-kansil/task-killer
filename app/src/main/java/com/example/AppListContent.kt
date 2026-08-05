package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.GeometricAllow
import com.example.ui.theme.GeometricDeny
import com.example.ui.theme.GeometricSuccess

@Composable
fun AppListContent(
    apps: List<AppInfo>,
    viewModel: AppManagerViewModel,
    context: Context,
    isVpnActive: Boolean,
    killingPackages: Set<String>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItemCard(
                app = app,
                isKilling = killingPackages.contains(app.packageName),
                onForceStop = { viewModel.forceStopApp(app.packageName, app.uid, context) },
                onSelectNetworkMode = { mode ->
                    viewModel.setAppNetworkMode(app.packageName, app.uid, mode, context)
                },
                onOpenPermissions = { viewModel.openPermissions(app.packageName, app.appName) },
                onUninstall = { viewModel.uninstallApp(app.packageName, context) },
                onInfo = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun AppItemCard(
    app: AppInfo,
    isKilling: Boolean,
    onForceStop: () -> Unit,
    onSelectNetworkMode: (NetworkAccessMode) -> Unit,
    onOpenPermissions: () -> Unit,
    onUninstall: () -> Unit,
    onInfo: () -> Unit
) {
    var networkMenu by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.icon != null) {
                        AsyncImage(
                            model = app.icon,
                            contentDescription = app.appName,
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.appName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (app.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(GeometricSuccess, RoundedCornerShape(4.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    SmallActionChip(
                        text = networkModeLabel(app.networkAccessMode),
                        icon = networkModeIcon(app.networkAccessMode),
                        color = networkModeColor(app.networkAccessMode),
                        onClick = { networkMenu = true }
                    )
                    DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                        NetworkAccessMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        networkModeLabel(mode),
                                        fontSize = 13.sp,
                                        fontWeight = if (mode == app.networkAccessMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(networkModeIcon(mode), contentDescription = null, tint = networkModeColor(mode))
                                },
                                onClick = {
                                    networkMenu = false
                                    onSelectNetworkMode(mode)
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    SmallActionChip(
                        text = "Izin",
                        icon = Icons.Default.Security,
                        color = GeometricAllow,
                        onClick = onOpenPermissions
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    SmallActionChip(
                        text = if (isKilling) "..." else "Hentikan",
                        icon = Icons.Default.Stop,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = !isKilling,
                        onClick = onForceStop
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    SmallActionChip(
                        text = if (app.isSystemApp) "Info" else "Uninstall",
                        icon = if (app.isSystemApp) Icons.Default.Security else Icons.Default.Delete,
                        color = if (app.isSystemApp) MaterialTheme.colorScheme.onSurfaceVariant else GeometricDeny,
                        onClick = { if (app.isSystemApp) onInfo() else confirmUninstall = true }
                    )
                }
            }
        }
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus ${app.appName}?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("Aplikasi beserta datanya akan dihapus dari perangkat.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    onUninstall()
                }) { Text("Hapus", color = GeometricDeny, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) { Text("Batal") }
            }
        )
    }
}

/** Tombol kecil bergaya pil, tinggi 28dp — ringkas seperti pada desain. */
@Composable
fun SmallActionChip(
    text: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val tint = if (enabled) color else color.copy(alpha = 0.4f)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth().height(28.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun networkModeLabel(mode: NetworkAccessMode): String = when (mode) {
    NetworkAccessMode.ALL -> "Jaringan All"
    NetworkAccessMode.WIFI_ONLY -> "WiFi saja"
    NetworkAccessMode.CELLULAR_ONLY -> "Seluler saja"
    NetworkAccessMode.BLOCKED -> "Blokir"
}

@Composable
fun networkModeColor(mode: NetworkAccessMode): Color = when (mode) {
    NetworkAccessMode.ALL -> GeometricAllow
    NetworkAccessMode.WIFI_ONLY -> MaterialTheme.colorScheme.primary
    NetworkAccessMode.CELLULAR_ONLY -> MaterialTheme.colorScheme.primary
    NetworkAccessMode.BLOCKED -> GeometricDeny
}

fun networkModeIcon(mode: NetworkAccessMode): ImageVector = when (mode) {
    NetworkAccessMode.ALL -> Icons.Default.Public
    NetworkAccessMode.WIFI_ONLY -> Icons.Default.Wifi
    NetworkAccessMode.CELLULAR_ONLY -> Icons.Default.SignalCellularAlt
    NetworkAccessMode.BLOCKED -> Icons.Default.Block
}
