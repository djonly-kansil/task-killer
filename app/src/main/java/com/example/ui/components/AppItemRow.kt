package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900
import com.example.ui.theme.SystemBadgeBg
import com.example.ui.theme.SystemBadgeColor
import com.example.ui.theme.WhitelistBadgeBg
import com.example.ui.theme.WhitelistBadgeColor

@Composable
fun AppItemRow(
    app: AppItem,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (app.isRunning) Color.White else Color.Transparent
    val shadowModifier = if (app.isRunning) Modifier.background(bgColor) else Modifier.background(bgColor)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(shadowModifier)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Placeholder
        val initial = app.appName.firstOrNull()?.uppercase() ?: "A"
        val iconBgColor = if (app.isRunning) Indigo100 else Slate200
        val iconTextColor = if (app.isRunning) Indigo600 else Slate500

        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = iconTextColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Slate900
                )
                if (app.isRunning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E)) // Green500 for active
                    )
                }
                if (app.isWhitelisted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PROTECTED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = WhitelistBadgeColor,
                        modifier = Modifier
                            .background(WhitelistBadgeBg, RoundedCornerShape(4.dp))
                            .border(1.dp, Slate200, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                } else if (app.isSystemApp && !app.isRunning) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYSTEM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SystemBadgeColor,
                        modifier = Modifier
                            .background(SystemBadgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = app.packageName,
                fontSize = 12.sp,
                color = Slate400
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Checkbox
        if (!app.isWhitelisted) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) Indigo600 else Color.Transparent)
                    .border(
                        width = if (isSelected) 0.dp else 2.dp,
                        color = if (isSelected) Color.Transparent else Slate200,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleSelection() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(2.dp, Slate200.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            )
        }
    }
}
