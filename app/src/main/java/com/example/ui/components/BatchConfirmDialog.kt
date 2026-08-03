package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.Slate900

@Composable
fun BatchConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfirmasi Force-Stop", color = Slate900, fontWeight = FontWeight.Bold) },
        text = { Text("Anda akan melakukan force-stop pada $count aplikasi yang dipilih. Apakah Anda yakin?") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Ya, Force-Stop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
