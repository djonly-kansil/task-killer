package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeometricError
import com.example.ui.theme.GeometricOnError
import com.example.ui.theme.GeometricSuccess
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

   
    var isTunnelActive by remember { mutableStateOf(false) }

   
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    fun onToggleVpn() {
        if (state.isVpnActive) {
            viewModel.stopVpn(context)
        } else {
            val prepareIntent = viewModel.getVpnPrepareIntent(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                viewModel.startVpn(context)
            }
        }
    }

   
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateRamInfo(context)
            isTunnelActive = VpnController.isTunnelActive(context)
            delay(1000)
        }
    }

    
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            // Header Top Bar
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

            
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (state.isVpnActive) GeometricSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("VPN FILTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        }
                        Text(
                            text = when {
                                !state.isVpnActive -> "VPN OFF — switch ini master, VPN mati total"
                                isTunnelActive -> "VPN ON — tunnel aktif"
                                else -> "VPN ON — menyiapkan tunnel..."
                            },
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Switch(
                        checked = state.isVpnActive,
                        onCheckedChange = { onToggleVpn() },
                        colors = SwitchDefaults.colors(checkedTrackColor = GeometricSuccess)
                    )
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

            // Section Header & Kill All Button
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

            
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.loadData(context) },
                modifier = Modifier.fillMaxSize()
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    when (state.currentTab) {
                        0 -> AppListContent(apps = state.userApps, viewModel = viewModel, context = context, isVpnActive = state.isVpnActive)
                        1 -> AppListContent(apps = state.systemApps, viewModel = viewModel, context = context, isVpnActive = state.isVpnActive)
                        2 -> AboutScreenContent(shizukuStatus = state.shizukuStatus)
                    }
                }
            }
        }
    }

    if (state.permissionTargetPackage != null) {
        PermissionsDialog(
            appName = state.permissionTargetName ?: state.permissionTargetPackage!!,
            packageName = state.permissionTargetPackage!!,
            permissions = state.permissions,
            isLoading = state.isPermissionsLoading,
            busyPermission = state.permissionBusy,
            onToggle = { viewModel.togglePermission(it) },
            onDismiss = { viewModel.closePermissions() }
        )
    }
}

@Composable
fun PermissionsDialog(
    appName: String,
    packageName: String,
    permissions: List<AppPermission>,
    isLoading: Boolean,
    busyPermission: String?,
    onToggle: (AppPermission) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("TUTUP") }
        },
        title = {
            Column {
                Text("Izin Aplikasi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(appName, fontSize = 12.sp)
                Text(packageName, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            }
        },
        text = {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                permissions.isEmpty() -> Text("Tidak ada izin yang bisa dibaca untuk aplikasi ini.", fontSize = 12.sp)

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(permissions, key = { "${it.kind}:${it.name}" }) { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (perm.kind == PermissionKind.APPOPS) "APPOPS" else "RUNTIME",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if (busyPermission == perm.name) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Switch(
                                    checked = perm.isGranted,
                                    onCheckedChange = { onToggle(perm) }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}
