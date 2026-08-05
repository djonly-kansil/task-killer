package com.example

import android.content.Context
import android.content.SharedPreferences


object VpnRulesRepository {
    private const val PREFS_NAME = "vpn_rules_prefs"
    private const val KEY_MASTER = "__vpn_master_enabled__"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Master switch VPN (niat pengguna dari tombol on/off). */
    fun isMasterEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MASTER, false)

    fun setMasterEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MASTER, enabled).apply()
    }

    fun getAllRules(context: Context): Map<Int, NetworkAccessMode> {
        val result = mutableMapOf<Int, NetworkAccessMode>()
        prefs(context).all.forEach { (key, value) ->
            if (key == KEY_MASTER) return@forEach
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

    fun setMode(context: Context, uid: Int, mode: NetworkAccessMode) {
        val editor = prefs(context).edit()
        if (mode == NetworkAccessMode.ALL) {
            editor.remove(uid.toString())
        } else {
            editor.putString(uid.toString(), mode.name)
        }
        editor.apply()

        // Aturan hanya me-refresh tunnel yang sudah hidup; tidak pernah menyalakan
        // atau mematikan VPN (itu hak eksklusif master switch).
        LocalVpnService.reloadRules(context)
    }

    /**
     * Menulis satu mode untuk BANYAK uid dalam satu operasi.
     * Sengaja TIDAK memanggil reloadRules: pemanggil (pengaturan semua app)
     * yang bertanggung jawab membangun tunnel satu kali setelah selesai.
     */
    fun setModeForUids(context: Context, uids: Collection<Int>, mode: NetworkAccessMode) {
        val editor = prefs(context).edit()
        uids.forEach { uid ->
            if (mode == NetworkAccessMode.ALL) {
                editor.remove(uid.toString())
            } else {
                editor.putString(uid.toString(), mode.name)
            }
        }
        editor.commit()
    }
}
