package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shizuku.ShizukuManager
import com.example.ui.components.AppBottomSheet
import com.example.ui.components.AppInfoDialog
import com.example.ui.components.AppItemRow
import com.example.ui.components.BatchConfirmDialog
import com.example.ui.components.NoticeCard
import com.example.ui.theme.BackgroundColor
import com.example.ui.theme.CardBackground
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900
import com.example.viewmodel.AppTab
import com.example.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    viewModel: TaskViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val shizukuStatus by ShizukuManager.statusText.collectAsState()
    val isUserServiceBound by ShizukuManager.isUserServiceBound.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundColor,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground)
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "System Manager",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate900,
                            letterSpacing = (-0.5).sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isUserServiceBound) Color(0xFF10B981) else Color(0xFFEF4444)) // Emerald or Red
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = shizukuStatus,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }
                    }
                }
                
                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedBorderColor = Indigo600,
                        unfocusedBorderColor = Slate200
                    )
                )

                if (!uiState.noticeCardDismissed) {
                    NoticeCard(onDismiss = { viewModel.dismissNoticeCard() })
                }

                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppTab.entries.forEach { tab ->
                        val selected = uiState.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setTab(tab) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Indigo600 else Slate500
                            )
                        }
                    }
                }
                // Indicator line
                Row(modifier = Modifier.fillMaxWidth().height(2.dp).background(Slate200)) {
                    val weight = 1f / AppTab.entries.size
                    Spacer(
                        modifier = Modifier
                            .weight(weight * AppTab.entries.indexOf(uiState.selectedTab))
                    )
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .height(2.dp)
                            .background(Indigo600)
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(weight * (AppTab.entries.size - AppTab.entries.indexOf(uiState.selectedTab) - 1))
                    )
                }
            }
        },
        bottomBar = {
            if (uiState.selectedPackageNames.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackground)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${uiState.selectedPackageNames.size} app selected",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate500
                    )
                    Button(
                        onClick = { viewModel.openBatchConfirmDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = Indigo600),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Kill Task", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Indigo600
                )
            } else if (uiState.filteredApps.isEmpty()) {
                Text(
                    text = "Tidak ada aplikasi yang ditemukan.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Slate500
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredApps) { app ->
                        AppItemRow(
                            app = app,
                            isSelected = uiState.selectedPackageNames.contains(app.packageName),
                            onToggleSelection = { viewModel.toggleSelection(app.packageName) },
                            onClick = { viewModel.openAppModal(app) }
                        )
                    }
                }
            }
        }

        uiState.selectedAppForModal?.let { app ->
            if (uiState.showAppInfoDialog) {
                AppInfoDialog(
                    app = app,
                    onDismiss = { viewModel.closeAppInfoDialog() }
                )
            } else {
                AppBottomSheet(
                    app = app,
                    onDismiss = { viewModel.closeAppModal() },
                    onShowInfo = { viewModel.openAppInfoDialog(app) },
                    onForceStop = { viewModel.forceStopApp(app, context) },
                    onDeepUninstall = { viewModel.deepUninstallApp(app, context) }
                )
            }
        }

        if (uiState.showBatchConfirmDialog) {
            BatchConfirmDialog(
                count = uiState.selectedPackageNames.size,
                onConfirm = { viewModel.executeBatchForceStop(context) },
                onDismiss = { viewModel.closeBatchConfirmDialog() }
            )
        }
    }
}
