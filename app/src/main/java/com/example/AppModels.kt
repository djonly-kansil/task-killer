package com.example

import android.graphics.drawable.Drawable


enum class NetworkAccessMode {
    ALL,            
    WIFI_ONLY,     
    CELLULAR_ONLY,  
    BLOCKED        
}

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isRunning: Boolean = false,
    val isDataOn: Boolean = true,
    val hasBootReceiver: Boolean = false,
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
    val notice: String? = null,
    val isVpnActive: Boolean = false
)
