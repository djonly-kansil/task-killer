package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppItem
import com.example.ui.theme.Indigo100
import com.example.ui.theme.Indigo600
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    app: AppItem,
    onDismiss: () -> Unit,
    onShowInfo: () -> Unit,
    onForceStop: () -> Unit,
    onDeepUninstall: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                val initial = app.appName.firstOrNull()?.uppercase() ?: "A"
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Indigo100),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = Indigo600,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = app.appName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "v${app.versionName} • ${if (app.isSystemApp) "System App" else "User App"}",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                }
            }

            // Options
            BottomSheetOption(
                icon = Icons.Default.Info,
                title = "App Info",
                iconColor = Slate600,
                bgColor = Slate50,
                textColor = Slate900,
                onClick = onShowInfo
            )
            Spacer(modifier = Modifier.height(8.dp))
            BottomSheetOption(
                icon = Icons.Default.Close,
                title = "Kill Process (Force Stop)",
                iconColor = Color(0xFFD97706), // amber-600
                bgColor = Slate50,
                textColor = Slate900,
                onClick = onForceStop,
                enabled = !app.isWhitelisted
            )
            Spacer(modifier = Modifier.height(8.dp))
            BottomSheetOption(
                icon = Icons.Default.Delete,
                title = "Deep Uninstall (Shell)",
                iconColor = Color(0xFFDC2626), // red-600
                bgColor = Color(0xFFFEF2F2), // red-50
                textColor = Color(0xFFDC2626), // red-600
                onClick = onDeepUninstall,
                enabled = !app.isWhitelisted && !app.isSystemApp
            )
        }
    }
}

@Composable
fun BottomSheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    iconColor: Color,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) iconColor else iconColor.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) textColor else textColor.copy(alpha = 0.5f)
        )
    }
}
