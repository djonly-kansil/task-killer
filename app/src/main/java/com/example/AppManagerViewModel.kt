package com.example

import android.app.ActivityManager
import android.content.Context
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

            val (user, system) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)

                // REVISI (masalah 1): satu kali panggilan shell untuk SEMUA data,
                // bukan satu panggilan per aplikasi seperti sebelumnya.
                val bulk = ShizukuController.getBulkState()

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
                        isRunning = bulk.runningPackages.contains(pkg.packageName),
                        isDataOn = !bulk.bgDataBlockedUids.contains(appInfo.uid),
                        isAutoBootEnabled = !bulk.bootIgnoredPackages.contains(pkg.packageName),
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

    /**
     * REVISI (masalah 3 - tidak auto-refresh):
     * Refresh RINGAN yang hanya memperbarui status yang bisa berubah dari luar
     * (proses berjalan, status background-data, auto-boot), tanpa query ulang
     * PackageManager (icon/label) yang lebih berat. Dipanggil dari onResume()
     * MainActivity, jadi begitu Anda kembali ke app ini -- misalnya setelah
     * mengubah data seluler dari Pengaturan HP atau menutup aplikasi lain lewat
     * recent apps -- datanya sudah ikut menyesuaikan tanpa perlu tutup-buka app
     * ini secara manual.
     *
     * Item yang statusnya TIDAK berubah dikembalikan sebagai objek AppInfo yang
     * SAMA (bukan copy baru), supaya LazyColumn di AppListContent (yang pakai
     * key = packageName) hanya me-recompose baris yang benar-benar berubah --
     * bukan reload seluruh daftar.
     */
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
        val newIsRunning = bulk.runningPackages.contains(packageName)
        val newIsDataOn = !bulk.bgDataBlockedUids.contains(uid)
        val newIsAutoBoot = !bulk.bootIgnoredPackages.contains(packageName)
        return if (newIsRunning == isRunning && newIsDataOn == isDataOn && newIsAutoBoot == isAutoBootEnabled) {
            this
        } else {
            copy(isRunning = newIsRunning, isDataOn = newIsDataOn, isAutoBootEnabled = newIsAutoBoot)
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

    /**
     * REVISI (masalah 4 - kill belum powerfull):
     * uid sekarang dikirim (dipakai untuk kill-uid yang benar). Setelah perintah
     * kill dikirim, app CEK ULANG status proses yang SEBENARNYA (bukan langsung
     * asumsi berhasil) -- jadi kalau aplikasi itu aktif lagi sendiri, tombol KILL
     * otomatis kembali aktif (merah) tanpa Anda harus reload manual, dan Anda
     * langsung tahu lewat pesan bahwa aplikasi itu hidup lagi sendiri.
     */
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

    /**
     * REVISI (masalah 2 - tombol data salah/tidak konsisten):
     * Sebelumnya status "isDataOn" dibaca dari appop RUN_IN_BACKGROUND, yang
     * sebenarnya kontrol untuk boleh-tidaknya proses berjalan di LATAR BELAKANG
     * secara umum -- BUKAN kontrol akses jaringan. Perintah mematikannya juga
     * memakai "cmd netpolicy add firewall-chain-rule ..." yang bukan subcommand
     * netpolicy yang valid, sehingga selalu gagal saat mematikan (makanya tombol
     * terasa "tidak bisa ditekan" saat hijau/ON -- sebenarnya bisa ditekan, tapi
     * errornya diam-diam), sementara saat dihidupkan lagi perintahnya "berhasil"
     * tapi memang dari awal tidak pernah benar-benar memblokir apa pun (makanya
     * status merah terasa tidak berpengaruh nyata ke jaringan).
     *
     * Sekarang dipakai mekanisme resmi Android untuk membatasi DATA SELULER LATAR
     * BELAKANG per aplikasi (cmd netpolicy add/remove restrict-background-blacklist),
     * yang statusnya dibaca ulang secara konsisten lewat dumpsys netpolicy di
     * getBulkState() -- jadi apa yang di-set dan yang dibaca sekarang sinkron.
     *
     * CATATAN PENTING: Android stock (tanpa root) tidak menyediakan command publik
     * untuk memblokir total Wi-Fi & data seluler sekaligus termasuk saat aplikasi
     * dibuka di foreground. Toggle terpisah "Wi-Fi" / "Data seluler" yang kadang
     * muncul di beberapa HP (mis. sebagian custom ROM) adalah fitur eksklusif OEM
     * yang commandnya tidak didokumentasikan dan berbeda-beda per merek, sehingga
     * tidak bisa ditiru 1:1 lewat Shizuku di semua device. Kalau Anda perlu blokir
     * total (foreground+background, Wi-Fi vs seluler terpisah) yang konsisten di
     * semua device, jalur yang reliable adalah VpnService lokal (seperti NetGuard),
     * bukan lewat netpolicy/appops -- ini perubahan arsitektur yang lebih besar,
     * beri tahu saya kalau ingin saya bantu rancang.
     */
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
