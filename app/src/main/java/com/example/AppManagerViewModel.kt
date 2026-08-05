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

    fun loadData(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            updateRamInfo(context)
            checkShizukuStatus()
            refreshVpnStatus(context)

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

                    val info = AppInfo(
                        appName = name,
                        packageName = pkg.packageName,
                        isSystemApp = isSystem,
                        icon = icon,
                        isRunning = bulk.isRunning(pkg.packageName),
                        isDataOn = !bulk.bgDataBlockedUids.contains(appInfo.uid),
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

    fun refreshLiveStatus() {
        if (!ShizukuController.isReady()) return
        viewModelScope.launch(Dispatchers.IO) {
            val bulk = ShizukuController.getBulkState()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    userApps = _state.value.userApps.map { it.withLiveStatus(bulk) },
                    systemApps = _state.value.systemApps.map { it.withLiveStatus(bulk) }
                )
            }
        }
    }

    private fun AppInfo.withLiveStatus(bulk: ShizukuController.BulkState): AppInfo {
        val newIsRunning = bulk.isRunning(packageName)
        val newIsDataOn = !bulk.bgDataBlockedUids.contains(uid)
        return if (newIsRunning == isRunning && newIsDataOn == isDataOn) {
            this
        } else {
            copy(isRunning = newIsRunning, isDataOn = newIsDataOn)
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

    // ---------------------------------------------------------------------
    // VPN — MASTER SWITCH
    // ---------------------------------------------------------------------

    /** Status VPN = niat master switch, bukan hasil evaluasi aturan per-app. */
    fun refreshVpnStatus(context: Context) {
        val master = VpnController.isMasterEnabled(context)
        if (_state.value.isVpnActive != master) {
            _state.value = _state.value.copy(isVpnActive = master)
        }
    }

    fun getVpnPrepareIntent(context: Context): Intent? = VpnController.prepareIntent(context)

    /** ON: langsung nyala, tanpa menunggu logika lain. */
    fun startVpn(context: Context) {
        _state.value = _state.value.copy(isVpnActive = true)
        VpnController.start(context)
    }

    /** OFF: langsung mati, tanpa menunggu logika lain. */
    fun stopVpn(context: Context) {
        _state.value = _state.value.copy(isVpnActive = false)
        VpnController.stop(context)
    }

    fun setAppNetworkMode(packageName: String, uid: Int, mode: NetworkAccessMode, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            VpnRulesRepository.setMode(context, uid, mode)
            withContext(Dispatchers.Main) {
                updateAppNetworkMode(packageName, mode)
            }
        }
    }

    // ---------------------------------------------------------------------
    // KILL
    // ---------------------------------------------------------------------

    fun forceStopApp(packageName: String, uid: Int, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ShizukuController.forceStopPackageDetailed(packageName, uid)
            delay(300)
            val stillRunning = ShizukuController.isPackageRunning(packageName)

            val holders = if (stillRunning && result.keptAliveBy.isEmpty()) {
                ShizukuController.findKeepAliveHolders(packageName)
            } else result.keptAliveBy

            withContext(Dispatchers.Main) {
                val message = when {
                    !stillRunning -> null
                    holders.isNotEmpty() ->
                        "Tetap aktif karena ditahan oleh: ${holders.joinToString(", ")}. Hentikan/batasi app tersebut dulu."
                    !result.commandSucceeded ->
                        "Gagal menghentikan aplikasi. Pastikan Shizuku aktif & izin diberikan."
                    else ->
                        "Aplikasi langsung hidup kembali dan tidak terdeteksi penahannya (kemungkinan dijadwalkan sistem/alarm)."
                }
                if (message != null) {
                    _state.value = _state.value.copy(errorMessage = message)
                }
                updateSingleAppRunningState(packageName, stillRunning)
                updateRamInfo(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val appsToKill = _state.value.userApps.filter { it.isRunning }
            val survivors = mutableListOf<String>()
            val holdersAll = linkedSetOf<String>()

            appsToKill.forEach { app ->
                val result = ShizukuController.forceStopPackageDetailed(app.packageName, app.uid)
                if (result.stillRunning) {
                    survivors.add(app.appName)
                    holdersAll.addAll(result.keptAliveBy)
                }
            }

            withContext(Dispatchers.Main) {
                if (survivors.isNotEmpty()) {
                    val holderText = if (holdersAll.isNotEmpty()) {
                        " Ditahan oleh: ${holdersAll.take(5).joinToString(", ")}."
                    } else ""
                    _state.value = _state.value.copy(
                        errorMessage = "Masih aktif: ${survivors.take(5).joinToString(", ")}.$holderText"
                    )
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

    // ---------------------------------------------------------------------
    // PERMISSIONS
    // ---------------------------------------------------------------------

    fun openPermissions(packageName: String, appName: String) {
        _state.value = _state.value.copy(
            permissionTargetPackage = packageName,
            permissionTargetName = appName,
            permissions = emptyList(),
            isPermissionsLoading = true,
            permissionBusy = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            if (!ShizukuController.isReady()) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isPermissionsLoading = false,
                        errorMessage = "Shizuku belum aktif atau izin belum diberikan."
                    )
                }
                return@launch
            }
            val perms = ShizukuController.readPermissions(packageName)
            withContext(Dispatchers.Main) {
                if (_state.value.permissionTargetPackage == packageName) {
                    _state.value = _state.value.copy(
                        permissions = perms,
                        isPermissionsLoading = false
                    )
                }
            }
        }
    }

    fun closePermissions() {
        _state.value = _state.value.copy(
            permissionTargetPackage = null,
            permissionTargetName = null,
            permissions = emptyList(),
            isPermissionsLoading = false,
            permissionBusy = null
        )
    }

    fun togglePermission(permission: AppPermission) {
        val pkg = _state.value.permissionTargetPackage ?: return
        if (permission.isProtected) {
            _state.value = _state.value.copy(
                errorMessage = "Izin dilindungi: ${permission.label} tidak bisa diubah."
            )
            return
        }
        val target = !permission.isGranted
        _state.value = _state.value.copy(permissionBusy = permission.name)

        viewModelScope.launch(Dispatchers.IO) {
            val (ok, detail) = ShizukuController.setPermission(
                pkg, permission.name, permission.kind, target
            )
            withContext(Dispatchers.Main) {
                if (_state.value.permissionTargetPackage != pkg) return@withContext
                _state.value = if (ok) {
                    _state.value.copy(
                        permissionBusy = null,
                        permissions = _state.value.permissions.map {
                            if (it.name == permission.name && it.kind == permission.kind) {
                                it.copy(isGranted = target)
                            } else it
                        }
                    )
                } else {
                    // biarkan switch kembali ke posisi semula + notif
                    _state.value.copy(
                        permissionBusy = null,
                        permissions = _state.value.permissions.map {
                            if (it.name == permission.name && it.kind == permission.kind) {
                                it.copy(isGranted = permission.isGranted, isProtected = true)
                            } else it
                        },
                        errorMessage = detail
                    )
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

    private fun updateAppNetworkMode(packageName: String, mode: NetworkAccessMode) {
        _state.value = _state.value.copy(
            userApps = _state.value.userApps.map { if (it.packageName == packageName) it.copy(networkAccessMode = mode) else it },
            systemApps = _state.value.systemApps.map { if (it.packageName == packageName) it.copy(networkAccessMode = mode) else it }
        )
    }
}
