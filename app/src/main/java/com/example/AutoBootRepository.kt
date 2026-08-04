package com.example

import android.content.Context
import android.content.SharedPreferences


object AutoBootRepository {
    private const val PREFS_NAME = "auto_boot_prefs"
    private const val KEY_COMPONENTS_PREFIX = "components_"
    private const val KEY_DISABLED_PREFIX = "disabled_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    
    fun rememberComponents(context: Context, packageName: String, components: List<String>) {
        if (components.isEmpty()) return
        prefs(context).edit()
            .putString(KEY_COMPONENTS_PREFIX + packageName, components.joinToString(","))
            .apply()
    }

    fun getComponents(context: Context, packageName: String): List<String> =
        prefs(context).getString(KEY_COMPONENTS_PREFIX + packageName, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun hasKnownComponents(context: Context, packageName: String): Boolean =
        prefs(context).contains(KEY_COMPONENTS_PREFIX + packageName)

    
    fun isAutoBootEnabled(context: Context, packageName: String): Boolean =
        !prefs(context).getBoolean(KEY_DISABLED_PREFIX + packageName, false)

    fun setAutoBootEnabled(context: Context, packageName: String, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_DISABLED_PREFIX + packageName, !enabled)
            .apply()
    }
}
