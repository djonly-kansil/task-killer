package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.GeometricAllow
import com.example.ui.theme.GeometricDeny

/**
 * Layar detail RAM: info RAM tetap di atas, di bawahnya daftar aplikasi yang
 * sedang berjalan beserta pemakaian RAM-nya.
 */
@Composable
fun RamDetailScreen(
    state: AppManagerState,
    context: Context,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectApp: (RunningAppRam?) -> Unit,
    onForceStop: (RunningAppRam) -> Unit
) {
    // Tombol/gesture kembali sistem: tutup dialog dulu bila terbuka, jika tidak kembali ke halaman utama.
    BackHandler(enabled = true) {
        if (state.ramDetailTarget != null) onSelectApp(null) else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                "Pemakaian RAM",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Info RAM tetap di bagian atas
        RamUsageCard(
            state = state,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Text(
            "Aplikasi Berjalan (${state.ramApps.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
        )

        when {
            state.isRamLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.ramApps.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Tidak ada data proses. Pastikan Shizuku aktif.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.ramApps, key = { it.packageName }) { app ->
                    RamAppRow(app = app, onClick = { onSelectApp(app) }, onForceStop = { onForceStop(app) })
                }
            }
        }
    }

    state.ramDetailTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onSelectApp(null) },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Column {
                    Text(target.appName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(target.packageName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            text = {
                Column {
                    InfoLine("Pemakaian RAM", String.format("%.0f MB", target.ramMb))
                    InfoLine("UID", target.uid.toString())
                    InfoLine("Jenis", if (target.isSystemApp) "Aplikasi sistem" else "Aplikasi pengguna")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSelectApp(null)
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${target.packageName}")
                    }
                    context.startActivity(intent)
                }) { Text("Info sistem", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { onSelectApp(null) }) { Text("Tutup") }
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun RamAppRow(app: RunningAppRam, onClick: () -> Unit, onForceStop: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    AsyncImage(
                        model = app.icon,
                        contentDescription = app.appName,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                String.format("%.0f MB", app.ramMb),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GeometricAllow
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(GeometricDeny.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onForceStop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Stop, contentDescription = "Hentikan", tint = GeometricDeny, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}
