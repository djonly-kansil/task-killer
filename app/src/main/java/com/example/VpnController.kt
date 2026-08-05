package com.example

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Kontrol VPN.
 *
 * PENTING: tombol switch VPN adalah MASTER SWITCH.
 * - ON  -> service langsung dinyalakan & tunnel langsung dibangun, tanpa menunggu
 *          logika/aturan per-aplikasi apa pun.
 * - OFF -> tunnel langsung ditutup & service dimatikan, tanpa menunggu logika lain.
 */
object VpnController {

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context.applicationContext)

    /** Niat pengguna (master switch) yang disimpan permanen. */
    fun isMasterEnabled(context: Context): Boolean =
        VpnRulesRepository.isMasterEnabled(context)

    fun start(context: Context) {
        // Set master state DULU supaya UI & service sepakat sejak detik pertama.
        VpnRulesRepository.setMasterEnabled(context, true)
        val intent = Intent(context, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        VpnRulesRepository.setMasterEnabled(context, false)
        // Matikan tunnel secara langsung (tidak menunggu service menerima intent).
        LocalVpnService.shutdownNow()
        val intent = Intent(context, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_STOP)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // service mungkin sudah mati; abaikan.
        }
    }

    /** Status yang dipakai UI = master switch (bukan hasil evaluasi aturan). */
    fun isRunning(context: Context): Boolean =
        VpnRulesRepository.isMasterEnabled(context) || LocalVpnService.isRunning

    fun isTunnelActive(context: Context): Boolean = LocalVpnService.isTunnelActive

    /**
     * Jeda sementara untuk operasi massal: tunnel ditutup & service dihentikan,
     * TAPI flag master (niat pengguna) tidak diubah sehingga VPN bisa dinyalakan
     * kembali setelah operasi selesai.
     */
    fun pauseForBulk(context: Context) {
        LocalVpnService.shutdownNow()
        val intent = Intent(context, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_STOP)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // service mungkin sudah mati; abaikan.
        }
    }

    /** Nyalakan kembali setelah operasi massal, hanya bila master masih ON. */
    fun resumeAfterBulk(context: Context) {
        if (!VpnRulesRepository.isMasterEnabled(context)) return
        val intent = Intent(context, LocalVpnService::class.java)
            .setAction(LocalVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }
}
