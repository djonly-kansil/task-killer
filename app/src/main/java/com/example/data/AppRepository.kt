package com.example.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.model.AppItem
import com.example.shizuku.ShizukuManager
import com.example.whitelist.WhitelistManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppRepository {
    private const val TAG = "AppRepository"

    suspend fun loadUserApps(
        context: Context,
        whitelist: Set<String>,
        runningPackages: Set<String>
    ): List<AppItem> = withContext(Dispatchers.IO) {
        val output = ShizukuManager.execCommand("pm list packages -3")
        val packages = parsePackageList(output)
        Log.i(TAG, "User apps count from pm list packages -3: ${packages.size}")
        buildAppList(context, packages, isSystemTab = false, whitelist, runningPackages)
    }

    suspend fun loadSystemApps(
        context: Context,
        whitelist: Set<String>,
        runningPackages: Set<String>
    ): List<AppItem> = withContext(Dispatchers.IO) {
        val output = ShizukuManager.execCommand("pm list packages -s")
        val packages = parsePackageList(output)
        Log.i(TAG, "System apps count from pm list packages -s: ${packages.size}")
        buildAppList(context, packages, isSystemTab = true, whitelist, runningPackages)
    }

    suspend fun getRunningPackageNames(): Set<String> = withContext(Dispatchers.IO) {
        val running = mutableSetOf<String>()
        try {
            val psOutput = ShizukuManager.execCommand("ps -A -o NAME")
            psOutput.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && line.contains(".")) {
                    val pkg = line.substringBefore(":").trim()
                    if (isValidPackageName(pkg)) {
                        running.add(pkg)
                    }
                }
            }

            val dumpsysOutput = ShizukuManager.execCommand("dumpsys activity processes")
            dumpsysOutput.lines().forEach { rawLine ->
                val line = rawLine.trim()
                if (line.contains("package=") || line.contains("pkg=")) {
                    val match = Regex("package=([a-zA-Z0-9_.]+)").find(line)
                        ?: Regex("pkg=([a-zA-Z0-9_.]+)").find(line)
                    match?.groupValues?.getOrNull(1)?.let {
                        if (isValidPackageName(it)) {
                            running.add(it)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching running package names via Shizuku", e)
        }
        Log.i(TAG, "Running packages count via Shizuku: ${running.size}")
        running
    }

    private fun isValidPackageName(pkg: String): Boolean {
        return pkg.contains(".") && !pkg.contains(" ") && !pkg.startsWith("/") && pkg.length > 3
    }

    private fun parsePackageList(output: String): List<String> {
        return output.lines()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun buildAppList(
        context: Context,
        packages: List<String>,
        isSystemTab: Boolean,
        whitelist: Set<String>,
        runningPackages: Set<String>
    ): List<AppItem> {
        val pm = context.packageManager
        val list = mutableListOf<AppItem>()

        for (pkg in packages) {
            try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }

                val appInfo = packageInfo.applicationInfo ?: continue
                val label = pm.getApplicationLabel(appInfo).toString()
                val isRunning = runningPackages.contains(pkg)
                val isWhitelisted = WhitelistManager.isWhitelisted(pkg, whitelist)

                val grantedPermissions = mutableListOf<String>()
                val reqPermissions = packageInfo.requestedPermissions
                val flags = packageInfo.requestedPermissionsFlags
                if (reqPermissions != null && flags != null) {
                    for (i in reqPermissions.indices) {
                        if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                            grantedPermissions.add(reqPermissions[i])
                        }
                    }
                }

                val sizeMB = try {
                    val file = File(appInfo.sourceDir)
                    if (file.exists()) {
                        val mb = file.length() / (1024.0 * 1024.0)
                        Math.round(mb * 100.0) / 100.0
                    } else {
                        0.0
                    }
                } catch (e: Exception) {
                    0.0
                }

                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                list.add(
                    AppItem(
                        packageName = pkg,
                        appName = if (label.isNotBlank()) label else pkg,
                        versionName = packageInfo.versionName ?: "N/A",
                        versionCode = versionCode,
                        isSystemApp = isSystemTab || (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0),
                        isRunning = isRunning,
                        isWhitelisted = isWhitelisted,
                        targetSdk = appInfo.targetSdkVersion,
                        sourceDir = appInfo.sourceDir ?: "N/A",
                        apkSizeMB = sizeMB,
                        grantedPermissions = grantedPermissions
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Application may have been uninstalled or hidden
            } catch (e: Exception) {
                Log.w(TAG, "Error loading app info for package: $pkg", e)
            }
        }

        return list.sortedBy { it.appName.lowercase() }
    }
}
