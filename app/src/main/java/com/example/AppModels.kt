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

/** Urutan daftar aplikasi. */
enum class SortMode {
    NAME_ASC,
    NAME_DESC,
    INSTALL_NEW,
    INSTALL_OLD,
    RUNNING_FIRST
}

/** Filter daftar aplikasi. */
enum class AppFilter {
    ALL,
    RUNNING,
    NETWORK_BLOCKED
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
    val networkAccessMode: NetworkAccessMode = NetworkAccessMode.ALL,
    val installTime: Long = 0L
)

/** Satu baris pada layar detail RAM. */
data class RunningAppRam(
    val appName: String,
    val packageName: String,
    val uid: Int,
    val icon: Drawable? = null,
    val ramMb: Float = 0f,
    val isSystemApp: Boolean = false
)

data class AppManagerState(
    val userApps: List<AppInfo> = emptyList(),
    val systemApps: List<AppInfo> = emptyList(),
    val usedRamGb: Float = 0f,
    val totalRamGb: Float = 0f,
    // Rincian memori (GB) hasil pembacaan /proc/meminfo
    val ramUserAppsGb: Float = 0f,
    val ramCacheGb: Float = 0f,
    val ramSystemGb: Float = 0f,
    val ramFreeGb: Float = 0f,
    /** Riwayat rasio pemakaian RAM (0f..1f), maksimal 40 titik. */
    val ramHistory: List<Float> = emptyList(),
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
    val permissionBusy: String? = null,
    // Urutkan & filter
    val sortMode: SortMode = SortMode.NAME_ASC,
    val appFilter: AppFilter = AppFilter.ALL,
    val showSortSheet: Boolean = false,
    // Pencarian aplikasi
    val searchQuery: String = "",
    // Jaringan massal
    val showBulkNetworkSheet: Boolean = false,
    val isBulkNetworkBusy: Boolean = false,
    // Pop-up VPN (sekali per sesi aplikasi)
    val showVpnHint: Boolean = false,
    // Kill
    val killingPackages: Set<String> = emptySet(),
    // Layar RAM detail
    val showRamDetail: Boolean = false,
    val ramApps: List<RunningAppRam> = emptyList(),
    val isRamLoading: Boolean = false,
    val ramDetailTarget: RunningAppRam? = null
)
