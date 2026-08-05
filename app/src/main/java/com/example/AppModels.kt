package com.example

import android.graphics.drawable.Drawable


enum class NetworkAccessMode {
    ALL,
    WIFI_ONLY,
    CELLULAR_ONLY,
    BLOCKED
}

/** Jenis izin: runtime permission (pm grant/revoke) atau appops (appops set). */
enum class PermissionKind {
    RUNTIME,
    APPOPS
}

data class AppPermission(
    val name: String,
    val label: String,
    val isGranted: Boolean,
    val kind: PermissionKind,
    val isProtected: Boolean = false
)

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isRunning: Boolean = false,
    val isDataOn: Boolean = true,
    val uid: Int = 0,
    val networkAccessMode: NetworkAccessMode = NetworkAccessMode.ALL
)

data class AppManagerState(
    val userApps: List<AppInfo> = emptyList(),
    val systemApps: List<AppInfo> = emptyList(),
    val usedRamGb: Float = 0f,
    val totalRamGb: Float = 0f,
    val isLoading: Boolean = true,
    val shizukuStatus: String = "Checking Shizuku...",
    val currentTab: Int = 0,
    val errorMessage: String? = null,
    val isVpnActive: Boolean = false,
    // Dialog izin
    val permissionTargetPackage: String? = null,
    val permissionTargetName: String? = null,
    val permissions: List<AppPermission> = emptyList(),
    val isPermissionsLoading: Boolean = false,
    val permissionBusy: String? = null
)
