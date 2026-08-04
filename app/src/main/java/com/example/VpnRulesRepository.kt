package com.example

import android.content.Context
import android.content.SharedPreferences

/**
 * Penyimpanan persisten untuk mode akses jaringan per aplikasi -- dikunci dengan UID
 * (BUKAN packageName), karena inilah kunci yang dipakai AppInfo.uid & dibaca balik
 * di AppManagerViewModel.loadData() lewat getAllRules(context)[appInfo.uid].
 *
 * Kenapa UID, bukan packageName? Karena LocalVpnService butuh UID untuk menentukan
 * app mana yang perlu dimasukkan ke interface VPN, lalu baru dipetakan ke packageName
 * lewat PackageManager.getPackagesForUid() saat membangun Builder (VpnService.Builder
 * hanya menerima packageName, bukan uid, jadi pemetaan itu dilakukan di sisi service).
 *
 * Mode ALL (default) TIDAK disimpan sebagai entry terpisah -- begitu user memilih
 * balik ke ALL, entry uid tsb dihapus dari SharedPreferences (bukan ditulis string
 * "ALL"), supaya file prefs tidak membengkak untuk app yang tidak pernah dibatasi.
 */
object VpnRulesRepository {
    private const val PREFS_NAME = "vpn_rules_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Dibaca AppManagerViewModel.loadData() sekali di awal, dan oleh LocalVpnService
     * setiap kali interface VPN dibangun ulang (lihat rebuild() di LocalVpnService).
     */
    fun getAllRules(context: Context): Map<Int, NetworkAccessMode> {
        val result = mutableMapOf<Int, NetworkAccessMode>()
        prefs(context).all.forEach { (key, value) ->
            val uid = key.toIntOrNull() ?: return@forEach
            val mode = (value as? String)?.let {
                runCatching { NetworkAccessMode.valueOf(it) }.getOrNull()
            }
            if (mode != null) result[uid] = mode
        }
        return result
    }

    fun getMode(context: Context, uid: Int): NetworkAccessMode {
        val raw = prefs(context).getString(uid.toString(), null) ?: return NetworkAccessMode.ALL
        return runCatching { NetworkAccessMode.valueOf(raw) }.getOrDefault(NetworkAccessMode.ALL)
    }

    /**
     * Simpan mode baru untuk satu uid, lalu langsung minta LocalVpnService membangun
     * ulang interface VPN (kalau sedang aktif) supaya perubahan berlaku seketika --
     * sesuai catatan di AppManagerViewModel.setAppNetworkMode(): "tidak butuh restart
     * VPN". Kalau VPN filter sedang tidak aktif, reloadRules() tidak melakukan apa-apa;
     * rule tetap tersimpan dan baru benar-benar berlaku begitu VPN dinyalakan.
     */
    fun setMode(context: Context, uid: Int, mode: NetworkAccessMode) {
        val editor = prefs(context).edit()
        if (mode == NetworkAccessMode.ALL) {
            editor.remove(uid.toString())
        } else {
            editor.putString(uid.toString(), mode.name)
        }
        editor.apply()

        LocalVpnService.reloadRules(context)
    }
}
