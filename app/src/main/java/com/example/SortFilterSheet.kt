package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sheet "Filter & Urutkan". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortFilterSheet(
    sortMode: SortMode,
    filter: AppFilter,
    onSort: (SortMode) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            SheetHeader(icon = Icons.Default.FilterList, title = "Filter & Urutkan")

            Text(
                "FILTER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            SheetOption("Semua aplikasi", filter == AppFilter.ALL) { onFilter(AppFilter.ALL) }
            SheetOption("Hanya yang sedang aktif", filter == AppFilter.RUNNING) { onFilter(AppFilter.RUNNING) }
            SheetOption("Jaringan dibatasi / diblokir", filter == AppFilter.NETWORK_BLOCKED) {
                onFilter(AppFilter.NETWORK_BLOCKED)
            }

            Text(
                "URUTKAN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            SheetOption("Nama A → Z", sortMode == SortMode.NAME_ASC) { onSort(SortMode.NAME_ASC) }
            SheetOption("Nama Z → A", sortMode == SortMode.NAME_DESC) { onSort(SortMode.NAME_DESC) }
            SheetOption("Terbaru diinstal", sortMode == SortMode.INSTALL_NEW) { onSort(SortMode.INSTALL_NEW) }
            SheetOption("Terlama diinstal", sortMode == SortMode.INSTALL_OLD) { onSort(SortMode.INSTALL_OLD) }
            SheetOption("Yang aktif di atas", sortMode == SortMode.RUNNING_FIRST) { onSort(SortMode.RUNNING_FIRST) }
        }
    }
}

/** Sheet "Akses Jaringan All" — atur semua aplikasi sekaligus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkNetworkSheet(
    onSelect: (NetworkAccessMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            SheetHeader(icon = Icons.Default.Public, title = "Akses Jaringan Semua App")
            Text(
                "Aturan diterapkan ke seluruh aplikasi pada tab yang sedang dibuka.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            NetworkAccessMode.values().forEach { mode ->
                SheetOption(networkModeLabel(mode), selected = false) { onSelect(mode) }
            }
        }
    }
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SheetOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
    }
}
