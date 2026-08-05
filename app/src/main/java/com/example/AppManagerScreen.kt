package com.example

import android.app.Activity
import android.content.Intent
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeometricAllow
import com.example.ui.theme.GeometricDeny
import com.example.ui.theme.GeometricLocked
import com.example.ui.theme.GeometricSuccess
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val s = LocalStrings.current

    val snackbarHostState = remember { SnackbarHostState() }

    var isTunnelActive by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpn(context)
        }
    }

    fun enableVpn() {
        val prepareIntent = viewModel.getVpnPrepareIntent(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.startVpn(context)
        }
    }

    fun onToggleVpn() {
        if (state.isVpnActive) viewModel.stopVpn(context) else enableVpn()
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
            if (!state.showRamDetail) {
                CustomBottomNavigationBar(
                    selectedIndex = state.currentTab,
                    onSelect = { index -> viewModel.selectTab(index) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.showRamDetail) {
                RamDetailScreen(
                    state = state,
                    context = context,
                    onBack = { viewModel.closeRamDetail() },
                    onRefresh = { viewModel.loadRamApps(context) },
                    onSelectApp = { viewModel.selectRamApp(it) },
                    onForceStop = { viewModel.forceStopApp(it.packageName, it.uid, context) }
                )
                return@Box
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.appTitle, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            Text(s.appTitleAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            val isConnected = state.shizukuStatus.contains("Granted")
                            val statusColor = if (isConnected) GeometricSuccess else MaterialTheme.colorScheme.error
                            Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isConnected) s.shizukuConnected else s.shizukuDisconnected,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = s.settings, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                    }

                }

                // VPN card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
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
                                Text(s.vpnFilter, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                            }
                            Text(
                                text = when {
                                    !state.isVpnActive -> s.vpnOff
                                    isTunnelActive -> s.vpnOnActive
                                    else -> s.vpnOnPreparing
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

                // Memory card (klik -> layar RAM detail)
                Card(
                    onClick = { viewModel.openRamDetail(context) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(s.memoryUsage, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
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

                // Baris aksi cepat
                if (state.currentTab != 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionCard(
                            icon = Icons.Default.Public,
                            label = s.networkAccessAll,
                            color = GeometricAllow,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBulkNetworkBusy,
                            onClick = { viewModel.openBulkNetworkSheet() }
                        )
                        QuickActionCard(
                            icon = Icons.Default.Stop,
                            label = s.killAll,
                            color = GeometricDeny,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.killAllUserApps(context) }
                        )
                        QuickActionCard(
                            icon = Icons.Default.FilterList,
                            label = s.filterAndSort,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.openSortSheet() }
                        )
                    }
                }

                // Judul daftar
                val titleText = when (state.currentTab) {
                    0 -> s.userApps
                    1 -> s.systemApps
                    else -> s.info
                }
                Column(modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp)) {
                    Text(titleText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(36.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
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
                            0 -> AppListContent(
                                apps = viewModel.visibleApps(state.userApps),
                                viewModel = viewModel,
                                context = context,
                                isVpnActive = state.isVpnActive,
                                killingPackages = state.killingPackages
                            )
                            1 -> AppListContent(
                                apps = viewModel.visibleApps(state.systemApps),
                                viewModel = viewModel,
                                context = context,
                                isVpnActive = state.isVpnActive,
                                killingPackages = state.killingPackages
                            )
                            2 -> AboutScreenContent(shizukuStatus = state.shizukuStatus)
                        }
                    }
                }
            }
        }
    }

    if (state.showSortSheet) {
        SortFilterSheet(
            sortMode = state.sortMode,
            filter = state.appFilter,
            onSort = { viewModel.setSortMode(it) },
            onFilter = { viewModel.setAppFilter(it) },
            onDismiss = { viewModel.closeSortSheet() }
        )
    }

    if (state.showBulkNetworkSheet) {
        BulkNetworkSheet(
            onSelect = { viewModel.setNetworkModeForAll(it, context) },
            onDismiss = { viewModel.closeBulkNetworkSheet() }
        )
    }

    if (state.showVpnHint) {
        VpnHintDialog(
            onEnableVpn = {
                viewModel.dismissVpnHint()
                enableVpn()
            },
            onDismiss = { viewModel.dismissVpnHint() }
        )
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
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.height(74.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(28.dp).background(color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(15.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
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
    val s = LocalStrings.current
    var query by remember(packageName) { mutableStateOf("") }

    val filtered = remember(permissions, query) {
        if (query.isBlank()) permissions
        else permissions.filter {
            it.label.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
        }
    }
    val runtimePerms = filtered.filter { it.kind == PermissionKind.RUNTIME }
    val appopsPerms = filtered.filter { it.kind == PermissionKind.APPOPS }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close, fontWeight = FontWeight.Bold) }
        },
        title = {
            Column {
                Text(
                    s.permissionsTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    packageName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LegendDot(GeometricAllow, s.legendChangeable)
                    LegendDot(GeometricLocked, s.legendLocked)
                }
            }
        },
        text = {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                permissions.isEmpty() -> Text(
                    s.permissionsEmpty,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        placeholder = {
                            Text(s.permissionsSearch, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (filtered.isEmpty()) {
                        Text(
                            s.permissionsNoMatch,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                        ) {
                            if (runtimePerms.isNotEmpty()) {
                                item(key = "h-runtime") {
                                    PermissionSectionHeader("${s.permissionsRuntime} (${runtimePerms.size})")
                                }
                                items(runtimePerms, key = { "R:${it.name}" }) { perm ->
                                    PermissionRow(
                                        perm = perm,
                                        isBusy = busyPermission == perm.name,
                                        onToggle = { onToggle(perm) }
                                    )
                                }
                            }
                            if (appopsPerms.isNotEmpty()) {
                                item(key = "h-appops") {
                                    PermissionSectionHeader("${s.permissionsAppOps} (${appopsPerms.size})")
                                }
                                items(appopsPerms, key = { "A:${it.name}" }) { perm ->
                                    PermissionRow(
                                        perm = perm,
                                        isBusy = busyPermission == perm.name,
                                        onToggle = { onToggle(perm) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun PermissionSectionHeader(title: String) {
    Text(
        title.uppercase(),
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
    )
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermissionRow(perm: AppPermission, isBusy: Boolean, onToggle: () -> Unit) {
    val s = LocalStrings.current
    val locked = perm.isProtected
    val stateColor = if (perm.isGranted) GeometricAllow else GeometricDeny
    val stateText = when {
        perm.kind == PermissionKind.APPOPS -> if (perm.isGranted) "ALLOW" else "IGNORE"
        else -> if (perm.isGranted) "TRUE" else "DENY"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(
                perm.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                Text(
                    if (perm.kind == PermissionKind.APPOPS) "APPOPS" else "RUNTIME",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (locked) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = s.locked,
                        tint = GeometricLocked,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        s.locked.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeometricLocked
                    )
                }
            }
        }

        Box(
            modifier = Modifier.width(74.dp).height(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Surface(
                    onClick = onToggle,
                    enabled = !locked,
                    shape = RoundedCornerShape(8.dp),
                    color = stateColor.copy(alpha = if (locked) 0.08f else 0.16f),
                    border = BorderStroke(1.dp, stateColor.copy(alpha = if (locked) 0.3f else 0.6f)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stateText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (locked) stateColor.copy(alpha = 0.6f) else stateColor
                        )
                    }
                }
            }
        }
    }
}

