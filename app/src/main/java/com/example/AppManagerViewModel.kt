package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable? = null,
    val isRunning: Boolean = false,
    val isDataOn: Boolean = true,
    val isAutoBootEnabled: Boolean = false,
    val uid: Int = 0
)

data class AppManagerState(
    val userApps: List<AppInfo> = emptyList(),
    val systemApps: List<AppInfo> = emptyList(),
    val usedRamGb: Float = 0f,
    val totalRamGb: Float = 0f,
    val isLoading: Boolean = true,
    val shizukuStatus: String = "Checking Shizuku...",
    val currentTab: Int = 0
)

class AppManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppManagerState())
    val state: StateFlow<AppManagerState> = _state.asStateFlow()

    fun selectTab(tabIndex: Int) {
        _state.value = _state.value.copy(currentTab = tabIndex)
    }

    fun loadData(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            
            updateRamInfo(context)
            checkShizukuStatus()
            
            val (user, system) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
                
                val userApps = mutableListOf<AppInfo>()
                val systemApps = mutableListOf<AppInfo>()
                
                packages.forEach { pkg ->
                    val appInfo = pkg.applicationInfo
                    if (appInfo != null) {
                        val name = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                        
                        // Cek status running asli menggunakan pidof via Shizuku
                        val isRunning = checkIsAppRunningViaShizuku(pkg.packageName)
                        val isDataOn = checkAppNetworkStatus(appInfo.uid, pkg.packageName)

                        val info = AppInfo(
                            appName = name,
                            packageName = pkg.packageName,
                            isSystemApp = isSystem,
                            icon = icon,
                            isRunning = isRunning,
                            isDataOn = isDataOn,
                            isAutoBootEnabled = true,
                            uid = appInfo.uid
                        )
                        
                        if (isSystem) {
                            systemApps.add(info)
                        } else {
                            userApps.add(info)
                        }
                    }
                }
                
                userApps.sortBy { it.appName.lowercase() }
                systemApps.sortBy { it.appName.lowercase() }
                
                Pair(userApps, systemApps)
            }
            
            _state.value = _state.value.copy(
                userApps = user,
                systemApps = system,
                isLoading = false
            )
        }
    }

    fun updateRamInfo(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val total = memInfo.totalMem.toFloat() / (1024 * 1024 * 1024)
        val avail = memInfo.availMem.toFloat() / (1024 * 1024 * 1024)
        val used = total - avail
        _state.value = _state.value.copy(
            usedRamGb = used,
            totalRamGb = total
        )
    }

    fun checkShizukuStatus() {
        try {
            val status = if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku Connected & Granted"
                } else {
                    "Shizuku Running (Permission Denied)"
                }
            } else {
                "Shizuku Not Running"
            }
            _state.value = _state.value.copy(shizukuStatus = status)
        } catch (e: Throwable) {
            _state.value = _state.value.copy(shizukuStatus = "Shizuku Error")
        }
    }

    fun forceStopApp(packageName: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            executeShizukuCommand("am force-stop $packageName")
            executeShizukuCommand("cmd activity kill-uid --user 0 $packageName")
            withContext(Dispatchers.Main) {
                updateSingleAppRunningState(packageName, false)
                updateRamInfo(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appsToKill = _state.value.userApps.filter { it.isRunning }
            appsToKill.forEach { app ->
                executeShizukuCommand("am force-stop ${app.packageName}")
            }
            withContext(Dispatchers.Main) {
                loadData(context)
            }
        }
    }

    fun toggleDataNetwork(packageName: String, uid: Int, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            
            if (newStatus) {
                // Biarkan Internet Aktif
                executeShizukuCommand("cmd netpolicy remove restrict-background-whitelist $uid")
                executeShizukuCommand("cmd appops set --uid $uid RUN_IN_BACKGROUND allow")
            } else {
                // Matikan Akses Jaringan/Internet Aplikasi
                executeShizukuCommand("cmd netpolicy add restrict-background-whitelist $uid")
                executeShizukuCommand("cmd appops set --uid $uid RUN_IN_BACKGROUND deny")
                executeShizukuCommand("cmd netpolicy add firewall-chain-rule $uid deny")
            }
            
            withContext(Dispatchers.Main) {
                updateAppNetworkState(packageName, newStatus)
            }
        }
    }

    fun toggleAutoBoot(packageName: String, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            val mode = if (newStatus) "allow" else "ignore"
            executeShizukuCommand("cmd appops set $packageName BOOT_COMPLETED $mode")
            
            withContext(Dispatchers.Main) {
                updateAppAutoBootState(packageName, newStatus)
            }
        }
    }

    fun uninstallApp(packageName: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            executeShizukuCommand("pm uninstall $packageName")
            withContext(Dispatchers.Main) {
                loadData(context)
            }
        }
    }

    private fun checkIsAppRunningViaShizuku(packageName: String): Boolean {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val output = executeShizukuCommandWithOutput("pidof $packageName")
            output.trim().isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkAppNetworkStatus(uid: Int, packageName: String): Boolean {
        // Default bernilai true kecuali dibatasi
        return true
    }

    private fun updateSingleAppRunningState(packageName: String, isRunning: Boolean) {
        _state.value = _state.value.copy(
            userApps = _state.value.userApps.map { if (it.packageName == packageName) it.copy(isRunning = isRunning) else it },
            systemApps = _state.value.systemApps.map { if (it.packageName == packageName) it.copy(isRunning = isRunning) else it }
        )
    }

    private fun updateAppNetworkState(packageName: String, isDataOn: Boolean) {
        _state.value = _state.value.copy(
            userApps = _state.value.userApps.map { if (it.packageName == packageName) it.copy(isDataOn = isDataOn) else it },
            systemApps = _state.value.systemApps.map { if (it.packageName == packageName) it.copy(isDataOn = isDataOn) else it }
        )
    }

    private fun updateAppAutoBootState(packageName: String, isAutoBootEnabled: Boolean) {
        _state.value = _state.value.copy(
            userApps = _state.value.userApps.map { if (it.packageName == packageName) it.copy(isAutoBootEnabled = isAutoBootEnabled) else it },
            systemApps = _state.value.systemApps.map { if (it.packageName == packageName) it.copy(isAutoBootEnabled = isAutoBootEnabled) else it }
        )
    }

    private fun executeShizukuCommand(command: String) {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun executeShizukuCommandWithOutput(command: String): String {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return ""
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }
}
