package com.example

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: AppManagerViewModel by viewModels()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkShizukuPermission()
            viewModel.checkShizukuStatus()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { viewModel.checkShizukuStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        
        viewModel.loadData(this)

        setContent {
            MyApplicationTheme {
                AppManagerScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateRamInfo(this)
        viewModel.checkShizukuStatus()
    }

    private fun checkShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(100)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateRamInfo(context)
            delay(2000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            CustomBottomNavigationBar(
                selectedIndex = state.currentTab,
                onSelect = { index -> viewModel.selectTab(index) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AppController ", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Pro", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        val isConnected = state.shizukuStatus.contains("Granted")
                        val statusColor = if (isConnected) GeometricSuccess else MaterialTheme.colorScheme.error
                        Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConnected) "SHIZUKU CONNECTED" else "SHIZUKU DISCONNECTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            letterSpacing = 1.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                }
            }

            // Memory Usage Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("MEMORY USAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                                Text(String.format("%.1f GB", state.usedRamGb), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(String.format("/ %.1f GB", state.totalRamGb), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                        val ratio = if (state.totalRamGb > 0) state.usedRamGb / state.totalRamGb else 0f
                        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                strokeWidth = 4.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Text("${(ratio * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val progressValue = if (state.totalRamGb > 0) state.usedRamGb / state.totalRamGb else 0f
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // Section Header dengan Tombol KILL ALL jika di Tab User Apps
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleText = when (state.currentTab) {
                    0 -> "User Apps"
                    1 -> "System Apps"
                    else -> "About"
                }

                Column {
                    Text(titleText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Box(modifier = Modifier.padding(top = 4.dp).width(32.dp).height(3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                }

                // Tombol KILL ALL Hanya Muncul di Tab User Apps (Tab 0)
                if (state.currentTab == 0) {
                    Button(
                        onClick = { viewModel.killAllUserApps(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = GeometricError, contentColor = GeometricOnError),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("KILL ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Area Konten
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (state.currentTab) {
                    0 -> AppListContent(apps = state.userApps, viewModel = viewModel, context = context)
                    1 -> AppListContent(apps = state.systemApps, viewModel = viewModel, context = context)
                    2 -> AboutScreenContent(shizukuStatus = state.shizukuStatus)
                }
            }
        }
    }
}

@Composable
fun AppListContent(
    apps: List<AppInfo>,
    viewModel: AppManagerViewModel,
    context: android.content.Context
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            AppItemCard(
                app = app,
                onForceStop = { viewModel.forceStopApp(app.packageName, context) },
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
    onForceStop: () -> Unit,
    onToggleData: () -> Unit,
    onToggleAutoBoot: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit
) {
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
            // Icon App
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (app.icon != null) {
                    AsyncImage(model = app.icon, contentDescription = app.appName, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                } else {
                    Box(modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                }
            }
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // Nama & Package App
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            // Ikon Power DATA ON/OFF (Tombol Bulat Merah/Hijau)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = if (app.isDataOn) "DATA ON" else "DATA OFF",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (app.isDataOn) Color.White else Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                IconButton(
                    onClick = onToggleData,
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (app.isDataOn) GeometricSuccess else GeometricError, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Data Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Kolom Action (Auto Boot, Kill, Info, Del)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Tombol AUTO BOOT (Hijau jika ON, Abu-Abu jika OFF)
                    Button(
                        onClick = onToggleAutoBoot,
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (app.isAutoBootEnabled) GeometricSuccess else Color.Gray,
                            contentColor = Color.White
                        ),
                        shape = CircleShape
                    ) {
                        Text("AUTO BOOT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    // Tombol KILL (Merah jika Running, Abu-abu jika Off)
                    Button(
                        onClick = onForceStop,
                        enabled = app.isRunning, // Hanya bisa diklik jika aktif
                        modifier = Modifier.height(26.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeometricError,
                            contentColor = GeometricOnError,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.5f),
                            disabledContentColor = Color.LightGray
                        ),
                        shape = CircleShape
                    ) {
                        Text("KILL", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onInfo,
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = CircleShape
                    ) {
                        Text("INFO", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!app.isSystemApp) {
                        OutlinedButton(
                            onClick = onUninstall,
                            modifier = Modifier.height(24.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GeometricError),
                            border = BorderStroke(1.dp, GeometricError.copy(alpha = 0.6f)),
                            shape = CircleShape
                        ) {
                            Text("DEL", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreenContent(shizukuStatus: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AppController Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Version 1.0.0", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Status Service: $shizukuStatus", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
}

@Composable
fun CustomBottomNavigationBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(icon = Icons.Default.Apps, label = "Apps", isSelected = selectedIndex == 0, onClick = { onSelect(0) })
            BottomNavItem(icon = Icons.Default.Security, label = "System", isSelected = selectedIndex == 1, onClick = { onSelect(1) })
            BottomNavItem(icon = Icons.Default.Info, label = "About", isSelected = selectedIndex == 2, onClick = { onSelect(2) })
        }
    }
}

@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundModifier = if (isSelected) {
        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 16.dp, vertical = 8.dp)
    } else {
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    }

    Row(
        modifier = Modifier.clip(CircleShape).then(backgroundModifier).clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
