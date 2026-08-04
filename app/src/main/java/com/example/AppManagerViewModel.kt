package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppManagerViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppManagerState())
    val state: StateFlow<AppManagerState> = _state.asStateFlow()

    fun selectTab(tabIndex: Int) {
        _state.value = _state.value.copy(currentTab = tabIndex)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun loadData(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            updateRamInfo(context)
            checkShizukuStatus()

            val (user, system) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                // REVISI: ambil daftar proses berjalan sekali saja di awal,
                // bukan spawn shell baru untuk tiap aplikasi di dalam loop.
                val runningPackages = ShizukuController.getRunningPackages()

                val userApps = mutableListOf<AppInfo>()
                val systemApps = mutableListOf<AppInfo>()

                packages.forEach { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@forEach
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

                    // REVISI: status data & auto-boot sekarang dicek nyata,
                    // bukan hardcoded true seperti sebelumnya.
                    val (isDataOn, isAutoBootEnabled) = ShizukuController.getAppOpsStatus(pkg.packageName, appInfo.uid)

                    val info = AppInfo(
                        appName = name,
                        packageName = pkg.packageName,
                        isSystemApp = isSystem,
                        icon = icon,
                        isRunning = runningPackages.contains(pkg.packageName),
                        isDataOn = isDataOn,
                        isAutoBootEnabled = isAutoBootEnabled,
                        uid = appInfo.uid
                    )

                    if (isSystem) systemApps.add(info) else userApps.add(info)
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
        _state.value = _state.value.copy(shizukuStatus = ShizukuController.getStatusText())
    }

    fun forceStopApp(packageName: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok1 = ShizukuController.execute("am force-stop $packageName")
            val ok2 = ShizukuController.execute("cmd activity kill-uid --user 0 $packageName")
            withContext(Dispatchers.Main) {
                if (!ok1 && !ok2) {
                    _state.value = _state.value.copy(errorMessage = "Gagal menghentikan aplikasi. Pastikan Shizuku aktif & izin diberikan.")
                } else {
                    updateSingleAppRunningState(packageName, false)
                }
                updateRamInfo(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appsToKill = _state.value.userApps.filter { it.isRunning }
            var anyFailed = false
            appsToKill.forEach { app ->
                if (!ShizukuController.execute("am force-stop ${app.packageName}")) anyFailed = true
            }
            withContext(Dispatchers.Main) {
                if (anyFailed) {
                    _state.value = _state.value.copy(errorMessage = "Sebagian aplikasi gagal dihentikan.")
                }
                loadData(context)
            }
        }
    }

    fun toggleDataNetwork(packageName: String, uid: Int, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            val ok = if (newStatus) {
                // Biarkan Internet Aktif
                ShizukuController.execute("cmd netpolicy remove restrict-background-whitelist $uid") and
                    ShizukuController.execute("cmd appops set --uid $uid RUN_IN_BACKGROUND allow")
            } else {
                // Matikan Akses Jaringan/Internet Aplikasi
                ShizukuController.execute("cmd netpolicy add restrict-background-whitelist $uid") and
                    ShizukuController.execute("cmd appops set --uid $uid RUN_IN_BACKGROUND deny") and
                    ShizukuController.execute("cmd netpolicy add firewall-chain-rule $uid deny")
            }

            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal mengubah status jaringan aplikasi.")
                } else {
                    updateAppNetworkState(packageName, newStatus)
                }
            }
        }
    }

    fun toggleAutoBoot(packageName: String, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            val mode = if (newStatus) "allow" else "ignore"
            val ok = ShizukuController.execute("cmd appops set $packageName BOOT_COMPLETED $mode")

            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal mengubah pengaturan auto-boot.")
                } else {
                    updateAppAutoBootState(packageName, newStatus)
                }
            }
        }
    }

    fun uninstallApp(packageName: String, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ShizukuController.execute("pm uninstall $packageName")
            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal menghapus aplikasi.")
                }
                loadData(context)
            }
        }
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
}
