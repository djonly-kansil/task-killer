package com.example

import android.content.Context

/** Mode tema aplikasi. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Bahasa antarmuka. */
enum class AppLanguage { INDONESIAN, ENGLISH }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.INDONESIAN
)

/** Penyimpanan preferensi sederhana lewat SharedPreferences. */
object SettingsRepository {
    private const val PREFS = "app_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANG = "language"
    private const val KEY_COMPACT = "compact_cards"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppSettings {
        val p = prefs(context)
        val theme = runCatching {
            ThemeMode.valueOf(p.getString(KEY_THEME, ThemeMode.DARK.name)!!)
        }.getOrDefault(ThemeMode.DARK)
        val lang = runCatching {
            AppLanguage.valueOf(p.getString(KEY_LANG, AppLanguage.INDONESIAN.name)!!)
        }.getOrDefault(AppLanguage.INDONESIAN)
        return AppSettings(themeMode = theme, language = lang)
    }

    fun saveTheme(context: Context, mode: ThemeMode) {
        prefs(context).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANG, language.name).apply()
    }

    /** Mode kartu minimalis (VPN + RAM berbagi satu baris). */
    fun loadCompactCards(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPACT, false)

    fun saveCompactCards(context: Context, compact: Boolean) {
        prefs(context).edit().putBoolean(KEY_COMPACT, compact).apply()
    }
}
