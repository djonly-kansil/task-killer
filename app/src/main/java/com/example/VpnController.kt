package com.example

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Lapisan tipis di atas VpnService -- membungkus prepare()/start()/stop() supaya
 * ViewModel & UI (AppManagerViewModel, AppManagerScreen) tidak perlu tahu detail
 * Intent/nama komponen LocalVpnService secara langsung.
 *
 * Status "isRunning" dibaca dari flag statis di LocalVpnService, BUKAN dari
 * ActivityManager.getRunningServices() -- sejak API 26 API itu dibatasi hanya
 * melihat service milik app sendiri dengan cara yang tidak selalu reliable untuk
 * VpnService, jadi flag statis in-process jauh lebih sederhana & instan dibaca.
 *
 * CATATAN: karena start VPN service berjalan async (lihat startForegroundService di
 * bawah), ada jeda singkat antara tombol ditekan & flag isRunning benar-benar jadi
 * true di proses sistem. AppManagerViewModel.startVpn() sudah memanggil
 * refreshVpnStatus() tepat sesudah start() -- pada kondisi sangat jarang, jeda itu
 * membuat kartu status baru ikut menyesuaikan di refresh berikutnya (mis. saat
 * MainActivity.onResume). Ini bukan bug fatal, cuma nuansa timing bawaan Android
 * service; beri tahu saya kalau mau dibuatkan lebih instan (perlu observable/Flow
 * tambahan di ViewModel).
 */
object VpnController {

    /**
     * Return null kalau izin VPN sudah pernah di-approve user sebelumnya (pemanggil
     * bisa langsung start()). Kalau non-null, Intent ini WAJIB dijalankan lewat
     * ActivityResultLauncher (dialog consent sistem "app ingin menyiapkan koneksi VPN"),
     * TIDAK BOLEH lewat startActivity biasa.
     */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context.applicationContext)

    fun start(context: Context) {
        val intent = Intent(context, LocalVpnService::class.java).setAction(LocalVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LocalVpnService::class.java).setAction(LocalVpnService.ACTION_STOP)
        context.startService(intent)
    }

    fun isRunning(context: Context): Boolean = LocalVpnService.isRunning
}
