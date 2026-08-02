package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {

    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    fun isPermissionGranted(): Boolean {
        return if (isShizukuAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (isShizukuAvailable() && !isPermissionGranted()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    // --- FUNGSI MENGAMBIL DAFTAR APLIKASI (PERBAIKAN UTAMA) ---
    fun getRunningApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val runningApps = mutableListOf<AppInfo>()

        try {
            // Ambil semua aplikasi yang diinstall oleh pengguna
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            // Ambil list package aktif dari shell perintah 'ps -A'
            val shellOutput = executeCommand("ps -A")
            val runningPackages = mutableSetOf<String>()

            if (shellOutput.isNotEmpty()) {
                shellOutput.lines().forEach { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.isNotEmpty()) {
                        val pkg = parts.last()
                        if (pkg.contains(".")) {
                            runningPackages.add(pkg)
                        }
                    }
                }
            }

            for (app in installedApps) {
                // Filter: Hanya ambil aplikasi pihak ke-3 (bukan sistem/bloatware)
                val isUserApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                val isSelf = app.packageName == context.packageName
                val isShizuku = app.packageName.contains("shizuku")

                if (isUserApp && !isSelf && !isShizuku) {
                    val appName = packageManager.getApplicationLabel(app).toString()

                    // Fallback: Jika 'ps -A' diblokir, tampilkan semua User Apps
                    if (runningPackages.isEmpty() || runningPackages.contains(app.packageName)) {
                        runningApps.add(AppInfo(appName, app.packageName))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return runningApps
    }

    fun forceStopPackage(packageName: String): Boolean {
        if (!isPermissionGranted()) return false
        val output = executeCommand("am force-stop $packageName")
        return true
    }

    fun executeCommand(command: String): String {
        if (!isPermissionGranted()) return ""
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            
            val process = newProcessMethod.invoke(
                null, 
                arrayOf("sh", "-c", command), 
                null, 
                null
            ) as Process
            
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
