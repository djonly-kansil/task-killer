package com.example.model

data class AppItem(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isRunning: Boolean,
    val isWhitelisted: Boolean,
    val targetSdk: Int,
    val sourceDir: String,
    val apkSizeMB: Double,
    val grantedPermissions: List<String>
)
