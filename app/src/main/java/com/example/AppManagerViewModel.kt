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

    /** Pop-up "VPN belum aktif" hanya sekali selama proses aplikasi hidup. */
    private var vpnHintShownThisSession = false

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
                        networkAccessMode = vpnRules[appInfo.uid] ?: NetworkAccessMode.ALL,
                        installTime = pkg.firstInstallTime
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

    /**
     * Membaca rincian memori di thread IO (Dispatchers.IO) lalu memperbarui state,
     * termasuk menambah satu titik pada riwayat pemakaian RAM (maks 40 titik).
     */
    fun updateRamInfo(context: Context) {
        viewModelScope.launch { refreshRamInfo(context) }
    }

    suspend fun refreshRamInfo(context: Context) {
        val snapshot = withContext(Dispatchers.IO) { readMemorySnapshot(context) }
        val prev = _state.value
        val ratio = if (snapshot.totalGb > 0f) (snapshot.usedGb / snapshot.totalGb).coerceIn(0f, 1f) else 0f
        val history = (prev.ramHistory + ratio).let { if (it.size > 40) it.drop(it.size - 40) else it }
        _state.value = prev.copy(
            usedRamGb = snapshot.usedGb,
            totalRamGb = snapshot.totalGb,
            ramUserAppsGb = snapshot.userAppsGb,
            ramCacheGb = snapshot.cacheGb,
            ramSystemGb = snapshot.systemGb,
            ramFreeGb = snapshot.freeGb,
            ramHistory = history
        )
    }

    private data class MemorySnapshot(
        val totalGb: Float,
        val usedGb: Float,
        val userAppsGb: Float,
        val cacheGb: Float,
        val systemGb: Float,
        val freeGb: Float
    )

    /** Rincian dari /proc/meminfo; jatuh kembali ke ActivityManager bila gagal dibaca. */
    private fun readMemorySnapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val gb = 1024f * 1024f * 1024f
        val fallbackTotal = memInfo.totalMem.toFloat() / gb
        val fallbackAvail = memInfo.availMem.toFloat() / gb

        val values = try {
            java.io.File("/proc/meminfo").readLines().mapNotNull { line ->
                val parts = line.split(":")
                if (parts.size < 2) return@mapNotNull null
                val kb = parts[1].trim().removeSuffix(" kB").trim().toLongOrNull() ?: return@mapNotNull null
                parts[0].trim() to kb
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }

        fun v(key: String): Float = (values[key] ?: 0L).toFloat() / (1024f * 1024f)

        val total = if (v("MemTotal") > 0f) v("MemTotal") else fallbackTotal
        if (total <= 0f) return MemorySnapshot(0f, 0f, 0f, 0f, 0f, 0f)

        val free = when {
            v("MemAvailable") > 0f -> v("MemAvailable")
            else -> fallbackAvail
        }.coerceIn(0f, total)

        var cache = (v("Cached") + v("Buffers") + v("SReclaimable")).coerceAtLeast(0f)
        var system = (v("Slab") - v("SReclaimable") + v("KernelStack") + v("PageTables")).coerceAtLeast(0f)

        val used = (total - free).coerceIn(0f, total)
        // Cache yang belum dibebaskan sudah ikut dihitung di MemAvailable; batasi agar tidak melebihi "used".
        cache = cache.coerceAtMost((used * 0.6f))
        system = system.coerceAtMost((used - cache).coerceAtLeast(0f))
        val userApps = (used - cache - system).coerceAtLeast(0f)

        return MemorySnapshot(
            totalGb = total,
            usedGb = used,
            userAppsGb = userApps,
            cacheGb = cache,
            systemGb = system,
            freeGb = free
        )
    }

    fun checkShizukuStatus() {
        _state.value = _state.value.copy(shizukuStatus = ShizukuController.getStatusText())
    }

    // ---------------------------------------------------------------------
    // URUTKAN & FILTER
    // ---------------------------------------------------------------------

    fun openSortSheet() { _state.value = _state.value.copy(showSortSheet = true) }
    fun closeSortSheet() { _state.value = _state.value.copy(showSortSheet = false) }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
    }

    fun setAppFilter(filter: AppFilter) {
        _state.value = _state.value.copy(appFilter = filter)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun visibleApps(source: List<AppInfo>): List<AppInfo> {
        val s = _state.value
        val filtered = when (s.appFilter) {
            AppFilter.ALL -> source
            AppFilter.RUNNING -> source.filter { it.isRunning }
            AppFilter.NETWORK_BLOCKED -> source.filter { it.networkAccessMode != NetworkAccessMode.ALL }
        }
        val query = s.searchQuery.trim().lowercase()
        val searched = if (query.isEmpty()) filtered else filtered.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
        return when (s.sortMode) {
            SortMode.NAME_ASC -> searched.sortedBy { it.appName.lowercase() }
            SortMode.NAME_DESC -> searched.sortedByDescending { it.appName.lowercase() }
            SortMode.INSTALL_NEW -> searched.sortedByDescending { it.installTime }
            SortMode.INSTALL_OLD -> searched.sortedBy { it.installTime }
            SortMode.RUNNING_FIRST -> searched.sortedWith(
                compareByDescending<AppInfo> { it.isRunning }.thenBy { it.appName.lowercase() }
            )
        }
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
        _state.value = _state.value.copy(isVpnActive = true, showVpnHint = false)
        VpnController.start(context)
    }

    /** OFF: langsung mati, tanpa menunggu logika lain. */
    fun stopVpn(context: Context) {
        _state.value = _state.value.copy(isVpnActive = false)
        VpnController.stop(context)
    }

    fun dismissVpnHint() {
        _state.value = _state.value.copy(showVpnHint = false)
    }

    /** Tampilkan pop-up VPN maksimal sekali per sesi aplikasi. */
    private fun maybeShowVpnHint(mode: NetworkAccessMode) {
        if (mode == NetworkAccessMode.ALL) return
        if (_state.value.isVpnActive) return
        if (vpnHintShownThisSession) return
        vpnHintShownThisSession = true
        _state.value = _state.value.copy(showVpnHint = true)
    }

    fun setAppNetworkMode(packageName: String, uid: Int, mode: NetworkAccessMode, context: Context) {
        maybeShowVpnHint(mode)
        viewModelScope.launch(Dispatchers.IO) {
            VpnRulesRepository.setMode(context, uid, mode)
            withContext(Dispatchers.Main) {
                updateAppNetworkMode(packageName, mode)
            }
        }
    }

    // ---------------------------------------------------------------------
    // JARINGAN MASSAL
    // ---------------------------------------------------------------------

    fun openBulkNetworkSheet() { _state.value = _state.value.copy(showBulkNetworkSheet = true) }
    fun closeBulkNetworkSheet() { _state.value = _state.value.copy(showBulkNetworkSheet = false) }

    /** Terapkan satu mode jaringan ke semua aplikasi pada tab yang sedang aktif. */
    fun setNetworkModeForAll(mode: NetworkAccessMode, context: Context) {
        maybeShowVpnHint(mode)
        val isSystemTab = _state.value.currentTab == 1
        val targets = if (isSystemTab) _state.value.systemApps else _state.value.userApps
        _state.value = _state.value.copy(isBulkNetworkBusy = true, showBulkNetworkSheet = false)

        viewModelScope.launch(Dispatchers.IO) {
            // VPN dijeda dulu bila aktif: menulis aturan satu-per-satu sambil tunnel
            // hidup memicu ratusan rebuild dan membuat app crash.
            val wasVpnOn = VpnController.isMasterEnabled(context)
            if (wasVpnOn) {
                VpnController.pauseForBulk(context)
                delay(400)
            }

            // Satu operasi batch untuk semua uid (tanpa rebuild per aplikasi).
            VpnRulesRepository.setModeForUids(context, targets.map { it.uid }.distinct(), mode)

            // Jika VPN mati sejak awal, biarkan tetap mati.
            if (wasVpnOn) {
                delay(200)
                VpnController.resumeAfterBulk(context)
            }

            withContext(Dispatchers.Main) {
                _state.value = if (isSystemTab) {
                    _state.value.copy(
                        systemApps = _state.value.systemApps.map { it.copy(networkAccessMode = mode) },
                        isBulkNetworkBusy = false,
                        isVpnActive = VpnController.isMasterEnabled(context)
                    )
                } else {
                    _state.value.copy(
                        userApps = _state.value.userApps.map { it.copy(networkAccessMode = mode) },
                        isBulkNetworkBusy = false,
                        isVpnActive = VpnController.isMasterEnabled(context)
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // KILL
    // ---------------------------------------------------------------------

    /**
     * Hentikan aplikasi memakai rantai berlapis, lalu pantau ~9 detik.
     * Jika aplikasi langsung hidup lagi, otomatis dihentikan ulang (maks 3x)
     * supaya pengguna tidak perlu menekan tombol berkali-kali.
     */
    fun forceStopApp(packageName: String, uid: Int, context: Context) {
        if (_state.value.killingPackages.contains(packageName)) return
        _state.value = _state.value.copy(
            killingPackages = _state.value.killingPackages + packageName
        )

        viewModelScope.launch(Dispatchers.IO) {
            var result = ShizukuController.forceStopPackageDetailed(packageName, uid)
            var attempts = 1
            var stillRunning = result.stillRunning

            // Pantau respawn cepat.
            while (attempts < 3) {
                delay(3000)
                if (!ShizukuController.isPackageRunning(packageName)) {
                    stillRunning = false
                    break
                }
                result = ShizukuController.forceStopPackageDetailed(packageName, uid)
                stillRunning = result.stillRunning
                attempts++
            }

            val holders = if (stillRunning && result.keptAliveBy.isEmpty()) {
                ShizukuController.findKeepAliveHolders(packageName)
            } else result.keptAliveBy

            withContext(Dispatchers.Main) {
                val message = when {
                    !stillRunning -> null
                    holders.isNotEmpty() ->
                        "Masih hidup, ditahan oleh: ${holders.take(3).joinToString(", ")}. Hentikan aplikasi itu dulu, lalu coba lagi."
                    !result.commandSucceeded ->
                        "Gagal menghentikan. Pastikan Shizuku aktif & izin diberikan."
                    else ->
                        "Aplikasi dijadwalkan sistem dan langsung hidup lagi. Tekan Hentikan sekali lagi bila perlu."
                }
                if (message != null) {
                    _state.value = _state.value.copy(errorMessage = message)
                }
                _state.value = _state.value.copy(
                    killingPackages = _state.value.killingPackages - packageName
                )
                updateSingleAppRunningState(packageName, stillRunning)
                updateRamInfo(context)
                if (_state.value.showRamDetail) loadRamApps(context)
            }
        }
    }

    fun killAllUserApps(context: Context) {
        val isSystemTab = _state.value.currentTab == 1
        val source = if (isSystemTab) _state.value.systemApps else _state.value.userApps
        val appsToKill = source.filter { it.isRunning && it.packageName != context.packageName }
        if (appsToKill.isEmpty()) return

        _state.value = _state.value.copy(
            killingPackages = _state.value.killingPackages + appsToKill.map { it.packageName }
        )

        viewModelScope.launch(Dispatchers.IO) {
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
                _state.value = _state.value.copy(
                    killingPackages = emptySet(),
                    errorMessage = if (survivors.isEmpty()) {
                        "${appsToKill.size} aplikasi dihentikan."
                    } else {
                        val holderText = if (holdersAll.isNotEmpty()) {
                            " Ditahan oleh: ${holdersAll.take(3).joinToString(", ")}."
                        } else ""
                        "Masih aktif: ${survivors.take(4).joinToString(", ")}.$holderText"
                    }
                )
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
    // LAYAR RAM DETAIL
    // ---------------------------------------------------------------------

    fun openRamDetail(context: Context) {
        _state.value = _state.value.copy(showRamDetail = true, isRamLoading = true)
        loadRamApps(context)
    }

    fun closeRamDetail() {
        _state.value = _state.value.copy(showRamDetail = false, ramDetailTarget = null)
    }

    fun selectRamApp(app: RunningAppRam?) {
        _state.value = _state.value.copy(ramDetailTarget = app)
    }

    fun loadRamApps(context: Context, silent: Boolean = false) {
        viewModelScope.launch {
            refreshRamInfo(context)
            val list = withContext(Dispatchers.IO) {
                val known = (_state.value.userApps + _state.value.systemApps)
                    .associateBy { it.packageName }
                val shizukuMem = ShizukuController.getRunningPackageMemoryMb()

                val entries = if (shizukuMem.isNotEmpty()) {
                    shizukuMem.mapNotNull { (pkg, mb) ->
                        val info = known[pkg] ?: return@mapNotNull null
                        RunningAppRam(
                            appName = info.appName,
                            packageName = pkg,
                            uid = info.uid,
                            icon = info.icon,
                            ramMb = mb,
                            isSystemApp = info.isSystemApp
                        )
                    }
                } else {
                    // Fallback tanpa Shizuku: hanya proses milik sendiri yang terbaca.
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    (am.runningAppProcesses ?: emptyList()).mapNotNull { proc ->
                        val pkg = proc.processName.substringBefore(':')
                        val info = known[pkg] ?: return@mapNotNull null
                        val mem = runCatching {
                            am.getProcessMemoryInfo(intArrayOf(proc.pid)).firstOrNull()?.totalPss ?: 0
                        }.getOrDefault(0)
                        RunningAppRam(
                            appName = info.appName,
                            packageName = pkg,
                            uid = info.uid,
                            icon = info.icon,
                            ramMb = mem / 1024f,
                            isSystemApp = info.isSystemApp
                        )
                    }
                }

                entries.distinctBy { it.packageName }.sortedByDescending { it.ramMb }
            }

            _state.value = _state.value.copy(
                ramApps = list,
                isRamLoading = if (silent) _state.value.isRamLoading else false
            )
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
                errorMessage = "Izin terkunci: ${permission.label} tidak bisa diubah."
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
                    // kembalikan ke posisi semula + tandai terkunci
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
