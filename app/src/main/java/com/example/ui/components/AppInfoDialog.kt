package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppItem
import com.example.ui.theme.Slate900

@Composable
fun AppInfoDialog(
    app: AppItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Information", color = Slate900, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                InfoRow("App Name", app.appName)
                InfoRow("Package Name", app.packageName)
                InfoRow("Version Name", app.versionName)
                InfoRow("Version Code", app.versionCode.toString())
                InfoRow("System App", app.isSystemApp.toString())
                InfoRow("Running", app.isRunning.toString())
                InfoRow("Whitelisted", app.isWhitelisted.toString())
                InfoRow("Target SDK", app.targetSdk.toString())
                InfoRow("APK Size", "${app.apkSizeMB} MB")
                InfoRow("Source Dir", app.sourceDir)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Granted Permissions:", fontWeight = FontWeight.Bold)
                if (app.grantedPermissions.isEmpty()) {
                    Text("None", style = MaterialTheme.typography.bodySmall)
                } else {
                    app.grantedPermissions.forEach {
                        Text("- $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(4.dp))
    }
}
