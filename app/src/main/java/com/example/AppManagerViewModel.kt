package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }

    
    private var hasShownVpnInactiveNotice = false

    fun loadData(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            updateRamInfo(context)
            checkShizukuStatus()

            val (user, system) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                val bulk = ShizukuController.getBulkState()

                
                val vpnRules = VpnRulesRepository.getAllRules(context)

                val userApps = mutableListOf<AppInfo>()
                val systemApps = mutableListOf<AppInfo>()

                packages.forEach { pkg ->
                    val appInfo = pkg.applicationInfo ?: return@forEach
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

                    
                    val liveBootComponents = bulk.bootReceiverComponents[pkg.packageName]
                    if (liveBootComponents != null) {
                        AutoBootRepository.rememberComponents(context, pkg.packageName, liveBootComponents)
                    }
                    val hasBootReceiver = liveBootComponents != null ||
                        AutoBootRepository.hasKnownComponents(context, pkg.packageName)

                    val info = AppInfo(
                        appName = name,
                        packageName = pkg.packageName,
                        isSystemApp = isSystem,
                        icon = icon,
                        isRunning = bulk.isRunning(pkg.packageName),
                        isDataOn = !bulk.bgDataBlockedUids.contains(appInfo.uid),
                        hasBootReceiver = hasBootReceiver,
                        isAutoBootEnabled = hasBootReceiver && AutoBootRepository.isAutoBootEnabled(context, pkg.packageName),
                        uid = appInfo.uid,
                        networkAccessMode = vpnRules[appInfo.uid] ?: NetworkAccessMode.ALL
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

    
    fun refreshLiveStatus(context: Context) {
        if (!ShizukuController.isReady()) return
        viewModelScope.launch(Dispatchers.IO) {
            val bulk = ShizukuController.getBulkState()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    userApps = _state.value.userApps.map { it.withLiveStatus(bulk, context) },
                    systemApps = _state.value.systemApps.map { it.withLiveStatus(bulk, context) }
                )
            }
        }
    }

    private fun AppInfo.withLiveStatus(bulk: ShizukuController.BulkState, context: Context): AppInfo {
        val newIsRunning = bulk.isRunning(packageName)
        val newIsDataOn = !bulk.bgDataBlockedUids.contains(uid)

        val liveBootComponents = bulk.bootReceiverComponents[packageName]
        if (liveBootComponents != null) {
            AutoBootRepository.rememberComponents(context, packageName, liveBootComponents)
        }
        val newHasBootReceiver = liveBootComponents != null || AutoBootRepository.hasKnownComponents(context, packageName)
        val newIsAutoBoot = newHasBootReceiver && AutoBootRepository.isAutoBootEnabled(context, packageName)

        return if (newIsRunning == isRunning && newIsDataOn == isDataOn &&
            newIsAutoBoot == isAutoBootEnabled && newHasBootReceiver == hasBootReceiver) {
            this
        } else {
            copy(isRunning = newIsRunning, isDataOn = newIsDataOn, isAutoBootEnabled = newIsAutoBoot, hasBootReceiver = newHasBootReceiver)
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

    
    fun refreshVpnStatus(context: Context) {
        _state.value = _state.value.copy(isVpnActive = VpnController.isRunning(context))
    }

    
    
    fun getVpnPrepareIntent(context: Context): Intent? = VpnController.prepareIntent(context)

      
    fun startVpn(context: Context) {
        VpnController.start(context)
        viewModelScope.launch {
            pollVpnStatusUntilStable(context, expected = true)
        }
    }

    fun stopVpn(context: Context) {
        VpnController.stop(context)
        viewModelScope.launch {
            pollVpnStatusUntilStable(context, expected = false)
        }
    }

    private suspend fun pollVpnStatusUntilStable(context: Context, expected: Boolean) {
        repeat(20) {
            val actual = withContext(Dispatchers.IO) { VpnController.isRunning(context) }
            if (_state.value.isVpnActive != actual) {
                _state.value = _state.value.copy(isVpnActive = actual)
            }
            if (actual == expected) return
            delay(100)
        }
    }

    
    fun setAppNetworkMode(packageName: String, uid: Int, mode: NetworkAccessMode, context: Context) {
        val vpnInactiveNow = !_state.value.isVpnActive
        viewModelScope.launch(Dispatchers.IO) {
            VpnRulesRepository.setMode(context, uid, mode)
            withContext(Dispatchers.Main) {
                updateAppNetworkMode(packageName, mode)
                if (vpnInactiveNow && mode != NetworkAccessMode.ALL && !hasShownVpnInactiveNotice) {
                    hasShownVpnInactiveNotice = true
                    _state.value = _state.value.copy(
                        notice = "VPN belum aktif. Aturan tersimpan & akan otomatis berlaku begitu VPN dinyalakan."
                    )
                }
            }
        }
    }

    
    fun forceStopApp(packageName: String, uid: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ShizukuController.forceStopPackage(packageName, uid)
            delay(400) // beri waktu sebentar sebelum cek ulang status proses
            val stillRunning = ShizukuController.isPackageRunning(packageName)

            withContext(Dispatchers.Main) {
                if (!ok) {
                    _state.value = _state.value.copy(errorMessage = "Gagal menghentikan aplikasi. Pastikan Shizuku aktif & izin diberikan.")
                } else if (stillRunning) {
                    _state.value = _state.value.copy(errorMessage = "Aplikasi aktif kembali secara otomatis. Coba tekan KILL sekali lagi.")
                }
                updateSingleAppRunningState(packageName, stillRunning)
                updateRamInfo(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appsToKill = _state.value.userApps.filter { it.isRunning }
            var anyFailed = false
            appsToKill.forEach { app ->
                if (!ShizukuController.forceStopPackage(app.packageName, app.uid)) anyFailed = true
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
                ShizukuController.execute("cmd netpolicy remove restrict-background-blacklist $uid")
            } else {
                ShizukuController.execute("cmd netpolicy add restrict-background-blacklist $uid")
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

    
    fun toggleAutoBoot(packageName: String, currentStatus: Boolean, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !currentStatus
            val components = AutoBootRepository.getComponents(context, packageName)

            if (components.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(errorMessage = "Tidak ada penerima BOOT_COMPLETED yang terdeteksi untuk aplikasi ini.")
                }
                return@launch
            }

            val ok = components.all { component -> ShizukuController.setComponentEnabled(component, newStatus) }
            if (ok) {
                AutoBootRepository.setAutoBootEnabled(context, packageName, newStatus)
            }

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

    private fun updateAppNetworkMode(packageName: String, mode: NetworkAccessMode) {
        _state.value = _state.value.copy(
            userApps = _state.value.userApps.map { if (it.packageName == packageName) it.copy(networkAccessMode = mode) else it },
            systemApps = _state.value.systemApps.map { if (it.packageName == packageName) it.copy(networkAccessMode = mode) else it }
        )
    }
}
