package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SignalCellularAlt
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
    context: Context,
    // REVISI (masalah: tidak ada peringatan "aktifkan VPN dulu" saat ganti mode
    // data): dulu dropdown mode jaringan langsung memanggil setAppNetworkMode()
    // tanpa cek apakah VPN filter sedang menyala -- padahal mode selain ALL baru
    // benar-benar berlaku begitu VPN aktif. Parameter ini WAJIB diisi pemanggil
    // (AppManagerScreen) dengan state.isVpnActive, supaya AppItemCard bisa
    // menampilkan hint sebelum menerapkan mode non-ALL saat VPN masih mati.
    isVpnActive: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItemCard(
                app = app,
                isVpnActive = isVpnActive,
                onForceStop = { viewModel.forceStopApp(app.packageName, app.uid, context) },
                onSelectNetworkMode = { mode -> viewModel.setAppNetworkMode(app.packageName, app.uid, mode, context) },
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
    isVpnActive: Boolean,
    onForceStop: () -> Unit,
    onSelectNetworkMode: (NetworkAccessMode) -> Unit,
    onToggleAutoBoot: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    // REVISI: dulu setiap DropdownMenuItem langsung memanggil onSelectNetworkMode()
    // tanpa cek status VPN. Sekarang, kalau user memilih mode selain ALL saat VPN
    // belum aktif, tampilkan dialog hint dulu -- rule tetap bisa disimpan (sesuai
    // desain: rule persisten & baru berlaku begitu VPN dinyalakan), tapi user jadi
    // sadar dulu bahwa itu belum langsung berpengaruh ke jaringan.
    var pendingVpnHintMode by remember { mutableStateOf<NetworkAccessMode?>(null) }
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
            // Icon
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

            // Name & Package
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            // Chip mode jaringan: ALL / WIFI_ONLY / CELLULAR_ONLY / BLOCKED.
            // Menggantikan toggle ON/OFF lama -- sekarang tap membuka dropdown 4 pilihan.
            // REVISI (kontrol data granular): mode selain ALL baru benar-benar berlaku
            // (termasuk saat app dibuka di foreground) kalau VPN filter sedang aktif.
            var menuExpanded by remember { mutableStateOf(false) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = networkModeLabel(app.networkAccessMode),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = networkModeColor(app.networkAccessMode)
                )
                Spacer(modifier = Modifier.height(2.dp))

                Box {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(networkModeColor(app.networkAccessMode), CircleShape)
                            .clickable { menuExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = networkModeIcon(app.networkAccessMode),
                            contentDescription = "Mode Jaringan",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        NetworkAccessMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(networkModeLabel(mode), fontWeight = if (mode == app.networkAccessMode) FontWeight.Bold else FontWeight.Normal) },
                                leadingIcon = { Icon(networkModeIcon(mode), contentDescription = null, tint = networkModeColor(mode)) },
                                onClick = {
                                    menuExpanded = false
                                    if (mode != NetworkAccessMode.ALL && !isVpnActive) {
                                        pendingVpnHintMode = mode
                                    } else {
                                        onSelectNetworkMode(mode)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Kolom Action (Auto Boot, Kill, Info, Del)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // AUTO BOOT
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

                    // KILL BUTTON
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

    val hintMode = pendingVpnHintMode
    if (hintMode != null) {
        AlertDialog(
            onDismissRequest = { pendingVpnHintMode = null },
            title = { Text("VPN belum aktif") },
            text = {
                Text(
                    "Mode \"${networkModeLabel(hintMode)}\" untuk ${app.appName} akan tersimpan, " +
                        "tapi belum berpengaruh ke jaringan sampai Anda menyalakan VPN di layar utama. " +
                        "Simpan aturan ini sekarang?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectNetworkMode(hintMode)
                    pendingVpnHintMode = null
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { pendingVpnHintMode = null }) { Text("Batal") }
            }
        )
    }
}

private fun networkModeLabel(mode: NetworkAccessMode): String = when (mode) {
    NetworkAccessMode.ALL -> "ALL"
    NetworkAccessMode.WIFI_ONLY -> "WIFI"
    NetworkAccessMode.CELLULAR_ONLY -> "SELULER"
    NetworkAccessMode.BLOCKED -> "BLOKIR"
}

// CATATAN: WIFI_ONLY & CELLULAR_ONLY sementara pakai warna MaterialTheme bawaan
// (tertiary/secondary) karena Color.kt belum tersedia untuk direview -- kalau nanti
// diupload, dua warna ini bisa diselaraskan dengan aksen custom yang sudah ada
// (GeometricSuccess/GeometricError).
@Composable
private fun networkModeColor(mode: NetworkAccessMode): Color = when (mode) {
    NetworkAccessMode.ALL -> GeometricSuccess
    NetworkAccessMode.WIFI_ONLY -> MaterialTheme.colorScheme.tertiary
    NetworkAccessMode.CELLULAR_ONLY -> MaterialTheme.colorScheme.secondary
    NetworkAccessMode.BLOCKED -> GeometricError
}

private fun networkModeIcon(mode: NetworkAccessMode) = when (mode) {
    NetworkAccessMode.ALL -> Icons.Default.Public
    NetworkAccessMode.WIFI_ONLY -> Icons.Default.Wifi
    NetworkAccessMode.CELLULAR_ONLY -> Icons.Default.SignalCellularAlt
    NetworkAccessMode.BLOCKED -> Icons.Default.Block
}
