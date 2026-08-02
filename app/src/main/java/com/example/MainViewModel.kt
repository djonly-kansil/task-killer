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
        _shizukuAvailable.value = ShizukuManager.isShizukuAvailable()
        _shizukuPermissionGranted.value = ShizukuManager.isPermissionGranted()
    }

    fun requestShizukuPermission() {
        ShizukuManager.requestPermission(100)
    }

    fun loadUserApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                val myPackageName = getApplication<Application>().packageName
                val launcherPackages = getLauncherPackages(pm)

                val vendorPrefixes = listOf(
                    "com.android.", "android.process.", "com.coloros.", 
                    "com.oplus.", "com.miui.", "com.samsung.", "moe.shizuku.privileged.api"
                )

                packages.mapNotNull { appInfo ->
                    val packageName = appInfo.packageName
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || 
                                   (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    
                    val isVendorOrSystem = vendorPrefixes.any { packageName.startsWith(it) }
                    val isLauncher = launcherPackages.contains(packageName)
                    val isSelf = packageName == myPackageName

                    if (isSystem || isVendorOrSystem || isLauncher || isSelf) {
                        null
                    } else {
                        AppInfo(
                            packageName = packageName,
                            name = pm.getApplicationLabel(appInfo).toString(),
                            icon = pm.getApplicationIcon(appInfo)
                        )
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
            _userApps.update { apps -> apps.map { it.copy(isSelected = false) } }
        }
    }

    fun killAllApps() {
         viewModelScope.launch(Dispatchers.IO) {
            val allApps = _userApps.value
            allApps.forEach { app ->
                ShizukuManager.forceStopPackage(app.packageName)
            }
            _userApps.update { apps -> apps.map { it.copy(isSelected = false) } }
        }
    }
}
