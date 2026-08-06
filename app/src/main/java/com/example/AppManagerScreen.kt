package com.example

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeometricAllow
import com.example.ui.theme.GeometricDeny
import com.example.ui.theme.GeometricLocked
import com.example.ui.theme.GeometricSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(viewModel: AppManagerViewModel, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val s = LocalStrings.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isTunnelActive by remember { mutableStateOf(false) }

    // Mode kartu minimalis (VPN + RAM berbagi satu baris), tersimpan antar sesi.
    var compactCards by remember { mutableStateOf(SettingsRepository.loadCompactCards(context)) }

    // Konfirmasi keluar: back pertama menampilkan peringatan, back kedua keluar.
    var lastBackPressAt by remember { mutableStateOf(0L) }
    val activity = context as? Activity
    val noOverlayOpen = !state.showRamDetail &&
        state.permissionTargetPackage == null &&
        !state.showSortSheet &&
        !state.showBulkNetworkSheet &&
        !state.showVpnHint
    BackHandler(enabled = noOverlayOpen) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 2000L) {
            activity?.finish()
        } else {
            lastBackPressAt = now
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(s.exitConfirm)
            }
        }
    }

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
            delay(400)
        }
    }

    // Layar detail RAM menyegarkan daftar app lebih sering, tanpa indikator loading.
    LaunchedEffect(state.showRamDetail) {
        while (state.showRamDetail) {
            delay(2000)
            viewModel.loadRamApps(context, silent = true)
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
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
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

                // Tombol hide/show: mengubah kartu VPN & RAM menjadi mode minimalis
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            compactCards = !compactCards
                            SettingsRepository.saveCompactCards(context, compactCards)
                        },
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (compactCards) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                if (compactCards) s.compactShow else s.compactHide,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                val vpnStatusText = when {
                    !state.isVpnActive -> s.vpnOff
                    isTunnelActive -> s.vpnOnActive
                    else -> s.vpnOnPreparing
                }

                if (compactCards) {
                    // Mode minimalis: VPN di kiri, RAM di kanan
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VpnStatusCard(
                            isVpnActive = state.isVpnActive,
                            statusText = vpnStatusText,
                            compact = true,
                            modifier = Modifier.weight(0.85f).fillMaxHeight(),
                            onToggle = { onToggleVpn() }
                        )
                        RamUsageCard(
                            state = state,
                            modifier = Modifier.weight(1.15f).fillMaxHeight(),
                            onClick = { viewModel.openRamDetail(context) },
                            compact = true
                        )
                    }
                } else {
                    VpnStatusCard(
                        isVpnActive = state.isVpnActive,
                        statusText = vpnStatusText,
                        compact = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        onToggle = { onToggleVpn() }
                    )

                    // Kartu RAM informatif (klik -> layar RAM detail)
                    RamUsageCard(
                        state = state,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        onClick = { viewModel.openRamDetail(context) }
                    )
                }

                // Baris aksi cepat
                if (state.currentTab != 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickActionChip(
                            icon = Icons.Default.Public,
                            label = s.networkAccessAll,
                            color = GeometricAllow,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isBulkNetworkBusy,
                            onClick = { viewModel.openBulkNetworkSheet() }
                        )
                        QuickActionChip(
                            icon = Icons.Default.Stop,
                            label = s.killAll,
                            color = GeometricDeny,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.killAllUserApps(context) }
                        )
                        QuickActionChip(
                            icon = Icons.Default.FilterList,
                            label = s.filterAndSort,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.openSortSheet() }
                        )
                    }
                }

                // Kolom pencarian aplikasi
                if (state.currentTab != 2) {
                    AppSearchField(
                        query = state.searchQuery,
                        placeholder = s.searchApps,
                        clearLabel = s.clearSearch,
                        onQueryChange = { viewModel.setSearchQuery(it) }
                    )
                }

                // Judul daftar
                val listCount = when (state.currentTab) {
                    0 -> viewModel.visibleApps(state.userApps).size
                    1 -> viewModel.visibleApps(state.systemApps).size
                    else -> 0
                }
                val titleText = when (state.currentTab) {
                    0 -> "${s.userApps} ($listCount)"
                    1 -> "${s.systemApps} ($listCount)"
                    else -> s.info
                }
                Column(modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(titleText, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .width(36.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }

                val listFocusManager = LocalFocusManager.current
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.loadData(context) },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { listFocusManager.clearFocus(force = true) })
                        }
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

/** Kartu VPN: versi penuh (dengan teks status) dan versi minimalis. */
@Composable
private fun VpnStatusCard(
    isVpnActive: Boolean,
    statusText: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val s = LocalStrings.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.fillMaxHeight() else Modifier)
                .heightIn(min = if (compact) 56.dp else 44.dp)
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = if (isVpnActive) GeometricSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
            if (compact) {
                Text(
                    s.vpnFilter,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    s.vpnFilter,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Switch(
                checked = isVpnActive,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(if (compact) 0.62f else 0.7f),
                colors = SwitchDefaults.colors(checkedTrackColor = GeometricSuccess)
            )
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSearchField(
    query: String,
    placeholder: String,
    clearLabel: String,
    onQueryChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Mode pencarian dianggap aktif sejak kolom mendapat fokus sampai fokus dilepas.
    var searchActive by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) { if (isFocused) searchActive = true }

    fun release() {
        // Satu aksi: tutup keyboard, lepas fokus (kursor hilang), keluar dari mode pencarian.
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        searchActive = false
    }

    // Back dari HP: sekali tekan langsung keluar dari mode pencarian.
    BackHandler(enabled = searchActive || isFocused || query.isNotEmpty()) {
        if (query.isNotEmpty()) onQueryChange("")
        release()
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 2.dp)
            .height(36.dp) // <- sekarang beneran dipatuhi, atur di sini
            .focusRequester(focusRequester),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { release() }, onDone = { release() }),
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = query,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                placeholder = {
                    Text(placeholder, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange(""); release() },
                            modifier = Modifier.size(28.dp) // dikecilin, IconButton default juga minimal 48dp
                        ) {
                            Icon(Icons.Default.Close, contentDescription = clearLabel, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(50),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp) // ini yg atur "gemuk"-nya isi
            )
        }
    )
}

@Composable
private fun QuickActionChip(
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
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
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

    val runtimePerms = permissions.filter { it.kind == PermissionKind.RUNTIME }
    val appopsPerms = permissions.filter { it.kind == PermissionKind.APPOPS }

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

