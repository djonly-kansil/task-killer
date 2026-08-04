package com.example

import android.content.Context
import android.content.SharedPreferences


object VpnRulesRepository {
    private const val PREFS_NAME = "vpn_rules_prefs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    
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
