package com.example.whitelist

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log

object WhitelistManager {
    private const val TAG = "WhitelistManager"

    private val HARDCODED_WHITELIST = setOf(
        "android",
        "com.android.systemui",
        "moe.shizuku.privileged.api",
        "com.google.android.gms",
        "com.android.vending"
    )

    fun getDynamicWhitelist(context: Context): Set<String> {
        val whitelist = mutableSetOf<String>()
        
        whitelist.addAll(HARDCODED_WHITELIST)
        
        whitelist.add(context.packageName)

        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val launcherResolve = context.packageManager.resolveActivity(launcherIntent, 0)
            launcherResolve?.activityInfo?.packageName?.let {
                if (it.isNotEmpty() && it != "android") {
                    whitelist.add(it)
                    Log.d(TAG, "Whitelisted Launcher: $it")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve default launcher", e)
        }

        try {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            if (!defaultIme.isNullOrEmpty()) {
                ComponentName.unflattenFromString(defaultIme)?.packageName?.let {
                    whitelist.add(it)
                    Log.d(TAG, "Whitelisted IME: $it")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve default IME", e)
        }

        try {
            Telephony.Sms.getDefaultSmsPackage(context)?.let {
                if (it.isNotEmpty()) {
                    whitelist.add(it)
                    Log.d(TAG, "Whitelisted SMS App: $it")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve default SMS package", e)
        }

        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage?.let {
                if (it.isNotEmpty()) {
                    whitelist.add(it)
                    Log.d(TAG, "Whitelisted Dialer: $it")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve default dialer", e)
        }

        Log.i(TAG, "Dynamic whitelist loaded with ${whitelist.size} protected packages.")
        return whitelist
    }

    fun isWhitelisted(packageName: String, whitelist: Set<String>): Boolean {
        return whitelist.contains(packageName)
    }
}
