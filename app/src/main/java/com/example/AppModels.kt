package com.example

import android.graphics.drawable.Drawable

/**
 * Mode akses jaringan per aplikasi, dikontrol lewat VPN filter lokal (LocalVpnService).
 * Ini TERPISAH dari `isDataOn` di bawah (toggle background-data lewat Shizuku/netpolicy
 * yang sudah ada sebelumnya) -- BLOCKED/WIFI_ONLY/CELLULAR_ONLY di sini baru benar-benar
 * berlaku (termasuk saat aplikasi dibuka di foreground) kalau VPN filter sedang aktif
 * (lihat AppManagerState.isVpnActive).
 */
enum class NetworkAccessMode {
    ALL,            // Wi-Fi + data seluler diizinkan (default)
    WIFI_ONLY,      // hanya boleh lewat Wi-Fi
    CELLULAR_ONLY,  // hanya boleh lewat data seluler
    BLOCKED         // diblokir total, foreground maupun background
}

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isRunning: Boolean = false,
    val isDataOn: Boolean = true,
    val isAutoBootEnabled: Boolean = false,
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
    val isVpnActive: Boolean = false
)
