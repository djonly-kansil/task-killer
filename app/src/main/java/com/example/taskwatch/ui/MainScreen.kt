package com.example.taskwatch.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.taskwatch.data.ProcessInfo
import com.example.taskwatch.ui.components.ProcessListItem
import com.example.taskwatch.viewmodel.AccessState
import com.example.taskwatch.viewmodel.ProcessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ProcessViewModel,
    onNavigateToSettings: () -> Unit
) {
    val processes by viewModel.processes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val accessState by viewModel.accessState.collectAsState()

    var processToStop by remember { mutableStateOf<ProcessInfo?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.updateAccessState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TaskWatch") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            StatusBanner(
                accessState = accessState,
                hasUsageStats = viewModel.hasUsageStatsPermission(),
                onRequestShizuku = { viewModel.shizukuManager.requestPermission() },
                onOpenUsageStats = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onOpenShizukuSite = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
                }
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (processes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No running processes found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(processes, key = { it.packageName }) { process ->
                        ProcessListItem(
                            process = process,
                            showStopButton = accessState == AccessState.SHIZUKU_READY,
                            onStopClick = { processToStop = process }
                        )
                    }
                }
            }
        }
    }

    if (processToStop != null) {
        AlertDialog(
            onDismissRequest = { processToStop = null },
            title = { Text("Force Stop App") },
            text = { Text("Force-stop ${processToStop?.appName}? This closes the app and any background activity.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        processToStop?.let { viewModel.forceStopPackage(it.packageName) }
                        processToStop = null
                    }
                ) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { processToStop = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusBanner(
    accessState: AccessState,
    hasUsageStats: Boolean,
    onRequestShizuku: () -> Unit,
    onOpenUsageStats: () -> Unit,
    onOpenShizukuSite: () -> Unit
) {
    Surface(
        color = when (accessState) {
            AccessState.SHIZUKU_READY -> MaterialTheme.colorScheme.primaryContainer
            AccessState.SHIZUKU_UNAUTHORIZED -> MaterialTheme.colorScheme.errorContainer
            AccessState.LIMITED_MODE -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (accessState) {
                AccessState.SHIZUKU_READY -> {
                    Text("Full access enabled via Shizuku.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                AccessState.SHIZUKU_UNAUTHORIZED -> {
                    Text("Shizuku is running but permission is denied.", color = MaterialTheme.colorScheme.onErrorContainer)
                    Button(onClick = onRequestShizuku, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Grant Permission")
                    }
                }
                AccessState.LIMITED_MODE -> {
                    Text(
                        "Limited Mode: Shizuku not available. Cannot force-stop apps.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!hasUsageStats) {
                        Text(
                            "Usage Access is required to list processes in Limited Mode.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(onClick = onOpenUsageStats, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Grant Usage Access")
                        }
                    }
                    TextButton(onClick = onOpenShizukuSite) {
                        Text("Learn about Shizuku")
                    }
                }
            }
        }
    }
}
