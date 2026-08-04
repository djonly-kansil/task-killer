package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    // Mengecek apakah Jaringan secara keseluruhan ON (Wifi ATAU Seluler)
    fun isDeviceNetworkActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun loadData(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            updateRamInfo(context)
            checkShizukuStatus()

            val (user, system) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                // 1. Ambil seluruh proses running dalam SATU pemanggilan tunggal (Cepat)
                val runningPackages = ShizukuController.getRunningPackages()

                val userApps = mutableListOf<AppInfo>()
                val systemApps = mutableListOf<AppInfo>()

                packages.forEach { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@forEach
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

                    val info = AppInfo(
                        appName = name,
                        packageName = pkg.packageName,
                        isSystemApp = isSystem,
                        icon = icon,
                        isRunning = runningPackages.contains(pkg.packageName),
                        isDataOn = true, // Status default, dikontrol oleh Network Toggle
                        isAutoBootEnabled = false,
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

    // Memperbarui aplikasi tunggal tanpa harus mereload seluruh list
    fun refreshSingleAppStatus(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val runningPackages = ShizukuController.getRunningPackages()
            val isStillRunning = runningPackages.contains(packageName)
            
            withContext(Dispatchers.Main) {
                updateSingleAppRunningState(packageName, isStillRunning)
            }
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

    // Force Stop Powerful (Masalah 4)
    fun forceStopApp(packageName: String, uid: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ShizukuController.forceStopPowerful(packageName, uid)
            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal mematikan $packageName. Cek izin Shizuku.")
                }
                // Langsung update status running secara lokal agar tombol abu-abu
                updateSingleAppRunningState(packageName, false)
                updateRamInfo(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appsToKill = _state.value.userApps.filter { it.isRunning }
            appsToKill.forEach { app ->
                ShizukuController.forceStopPowerful(app.packageName, app.uid)
            }
            withContext(Dispatchers.Main) {
                loadData(context)
            }
        }
    }

    // Toggle Data Network (Masalah 2)
    fun toggleDataNetwork(packageName: String, uid: Int, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            val ok = if (newStatus) {
                // Izinkan Akses Data
                ShizukuController.execute("cmd netpolicy remove reject-admin $uid") and
                ShizukuController.execute("cmd appops set --uid $uid RUN_IN_BACKGROUND allow")
            } else {
                // Blokir Total Akses Data Aplikasi
                ShizukuController.execute("cmd netpolicy add reject-admin $uid") and
                ShizukuController.execute("cmd appops set --uid $uid RUN_IN_BACKGROUND deny")
            }

            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal mengubah aturan jaringan aplikasi.")
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
