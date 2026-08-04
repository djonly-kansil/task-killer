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
 * CATATAN: karena start/stop VPN service berjalan async (lihat startForegroundService
 * di bawah, dan Intent ACTION_STOP yang dikirim lewat startService()), ada jeda
 * singkat antara tombol ditekan & flag isRunning benar-benar berubah di proses
 * sistem. AppManagerViewModel.startVpn()/stopVpn() menangani ini dengan polling
 * status berkala (bukan cek sekali) sampai nilainya stabil, supaya switch di UI
 * tidak pernah "nyangkut" di status lama.
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

    /**
     * REVISI (masalah: switch VPN terlihat ON tapi trafik belum benar-benar
     * difilter selama belum ada app dengan mode non-ALL): isRunning() di atas
     * cuma berarti "service-nya hidup", BUKAN berarti ada tunnel yang benar-benar
     * terbentuk -- LocalVpnService sengaja tidak establish() interface kalau
     * tidak ada satupun app yang perlu dibatasi saat ini. Fungsi ini mengekspos
     * status tunnel yang SEBENARNYA secara terpisah, dipakai AppManagerScreen
     * untuk menampilkan teks status yang jujur (mis. "siap, menunggu ada app
     * dibatasi" vs "aktif memfilter").
     */
    fun isTunnelActive(context: Context): Boolean = LocalVpnService.isTunnelActive
}
