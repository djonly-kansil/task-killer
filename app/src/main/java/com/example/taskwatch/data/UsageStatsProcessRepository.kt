package com.example.taskwatch.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageStatsProcessRepository(private val context: Context) : ProcessRepository {

    override suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60, time)

        if (stats == null || stats.isEmpty()) {
            return@withContext emptyList()
        }

        val packages = stats.map { it.packageName }.distinct()

        packages.mapNotNull { packageName ->
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                ProcessInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    memoryKb = null,
                    isSystemApp = isSystem
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.appName }
    }

    override suspend fun forceStopPackage(packageName: String): Boolean {
        return false // Unsupported in read-only mode
    }

    override fun isReadOnly(): Boolean = true
}
