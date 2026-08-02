package com.example

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val systemMonitor = SystemMonitor(application)

    private val _systemStats = MutableStateFlow(SystemStats(0, 0, 0f))
    val systemStats: StateFlow<SystemStats> = _systemStats.asStateFlow()

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps: StateFlow<List<AppInfo>> = _userApps.asStateFlow()

    private val _shizukuAvailable = MutableStateFlow(false)
    val shizukuAvailable: StateFlow<Boolean> = _shizukuAvailable.asStateFlow()

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted: StateFlow<Boolean> = _shizukuPermissionGranted.asStateFlow()

    init {
        viewModelScope.launch {
            systemMonitor.getStatsFlow().collect { stats ->
                _systemStats.value = stats
            }
        }
        checkShizukuStatus()
        loadUserApps()
    }

    fun checkShizukuStatus() {
        val available = ShizukuManager.isShizukuAvailable()
        val granted = ShizukuManager.isPermissionGranted()
        _shizukuAvailable.value = available
        _shizukuPermissionGranted.value = granted
        if (granted) {
            loadUserApps()
        }
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission(100)
    }

    fun loadUserApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                if (!ShizukuManager.isPermissionGranted()) return@withContext emptyList<AppInfo>()

                // 1. Get third-party packages
                val pmOutput = ShizukuManager.executeCommand("pm list packages -3")
                val thirdPartyPackages = pmOutput.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .toSet()

                // 2. Get running processes
                val psOutput = ShizukuManager.executeCommand("ps -A")
                val runningProcessNames = psOutput.lines()
                    .drop(1) // Drop header
                    .mapNotNull { line ->
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.isNotEmpty()) parts.last() else null
                    }
                    .filter { it.isNotEmpty() }
                    .toSet()

                // 3. Filter running apps that are in third-party packages
                val runningThirdPartyApps = runningProcessNames
                    .filter { processName ->
                        // The process name might be package.name:process, so we take substring before ':'
                        val packageName = processName.substringBefore(":")
                        thirdPartyPackages.contains(packageName)
                    }.map { it.substringBefore(":") }.toSet()

                val pm = getApplication<Application>().packageManager
                val myPackageName = getApplication<Application>().packageName
                val launcherPackages = getLauncherPackages(pm)
                
                val vendorPrefixes = listOf(
                    "com.android.", "android.process.", "com.coloros.", "com.google.android.", 
                    "com.oplus.", "com.miui.", "com.samsung.", "moe.shizuku.privileged.api"
                )

                runningThirdPartyApps.mapNotNull { packageName ->
                    val isVendorOrSystem = vendorPrefixes.any { packageName.startsWith(it) }
                    val isLauncher = launcherPackages.contains(packageName)
                    val isSelf = packageName == myPackageName

                    if (isVendorOrSystem || isLauncher || isSelf) {
                        null
                    } else {
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            AppInfo(
                                packageName = packageName,
                                name = pm.getApplicationLabel(appInfo).toString(),
                                icon = pm.getApplicationIcon(appInfo)
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.sortedBy { it.name.lowercase() }
            }
            _userApps.value = apps
        }
    }

    private fun getLauncherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos.map { it.activityInfo.packageName }.toSet()
    }

    fun toggleAppSelection(packageName: String) {
        _userApps.update { apps ->
            apps.map { 
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it 
            }
        }
    }

    fun killSelectedApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedApps = _userApps.value.filter { it.isSelected }
            selectedApps.forEach { app ->
                ShizukuManager.forceStopPackage(app.packageName)
            }
            loadUserApps() // Reload running apps list
        }
    }

    fun killAllApps() {
         viewModelScope.launch(Dispatchers.IO) {
            val allApps = _userApps.value
            allApps.forEach { app ->
                ShizukuManager.forceStopPackage(app.packageName)
            }
            loadUserApps() // Reload running apps list
        }
    }
}
