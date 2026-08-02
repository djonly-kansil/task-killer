package com.example.taskwatch.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.taskwatch.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShizukuProcessRepository(
    private val context: Context,
    private val shizukuManager: ShizukuManager
) : ProcessRepository {

    override suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val rawOutput = shizukuManager.listRunningProcesses()
        val packages = mutableSetOf<String>()
        val packageMemoryMap = mutableMapOf<String, Long>()

        // Very simplified parsing of 'dumpsys activity processes'.
        // Dumpsys output format varies; looking for "package=com.example" lines or similar identifiers.
        // A robust implementation would use ActivityManager via binder, but for this constraint we parse or fallback.
        // Alternatively, use Android's ActivityManager directly here via Shizuku binder calls if possible,
        // but dumpsys is requested. We'll parse package names from 'package=' or standard output.
        val lines = rawOutput.split("\n")
        val packageRegex = Regex("package=([a-zA-Z0-9_\\.]+)")
        for (line in lines) {
            val match = packageRegex.find(line)
            if (match != null) {
                packages.add(match.groupValues[1])
            }
        }

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
                    memoryKb = packageMemoryMap[packageName] ?: 0L,
                    isSystemApp = isSystem
                )
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedBy { it.appName }
    }

    override suspend fun forceStopPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shizukuManager.forceStopPackage(packageName)
    }

    override fun isReadOnly(): Boolean = false
}
