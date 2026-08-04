package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Membungkus semua interaksi shell command via Shizuku di satu tempat,
 * supaya ViewModel tidak perlu tahu detail proses shell (sebelumnya
 * dijadikan private method di dalam ViewModel).
 */
object ShizukuController {

    fun isReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun getStatusText(): String {
        return try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku Connected & Granted"
                } else {
                    "Shizuku Running (Permission Denied)"
                }
            } else {
                "Shizuku Not Running"
            }
        } catch (e: Throwable) {
            "Shizuku Error"
        }
    }

    /** Menjalankan command tanpa butuh output. Return true kalau exit code 0. */
    fun execute(command: String): Boolean {
        if (!isReady()) return false
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Menjalankan command dan mengembalikan output stdout-nya. */
    fun executeWithOutput(command: String): String {
        if (!isReady()) return ""
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * REVISI: sebelumnya setiap aplikasi memicu satu proses shell baru
     * ("pidof <package>") saat loadData(), jadi bisa ratusan proses shell
     * berurutan untuk device dengan banyak app terinstall. Sekarang cukup
     * SATU kali panggilan shell untuk ambil semua proses yang berjalan.
     */
    fun getRunningPackages(): Set<String> {
        if (!isReady()) return emptySet()
        val output = executeWithOutput("ps -A -o NAME")
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "NAME" }
            .toSet()
    }

    /**
     * REVISI: sebelumnya checkAppNetworkStatus() selalu return true (hardcoded/stub)
     * dan isAutoBootEnabled selalu di-set true saat load (hardcoded), jadi status
     * yang ditampilkan di UI tidak pernah mencerminkan kondisi asli device.
     * Sekarang keduanya dicek nyata lewat appops, digabung dalam satu proses shell
     * (bukan dua) supaya tidak menambah beban terlalu banyak.
     */
    fun getAppOpsStatus(packageName: String, uid: Int): Pair<Boolean, Boolean> {
        if (!isReady()) return Pair(true, false)
        val output = executeWithOutput(
            "cmd appops get --uid $uid RUN_IN_BACKGROUND; echo '---SPLIT---'; cmd appops get $packageName BOOT_COMPLETED"
        )
        val parts = output.split("---SPLIT---")
        val networkPart = parts.getOrElse(0) { "" }
        val bootPart = parts.getOrElse(1) { "" }
        val isDataOn = !networkPart.contains("deny", ignoreCase = true)
        val isAutoBootEnabled = bootPart.contains("allow", ignoreCase = true)
        return Pair(isDataOn, isAutoBootEnabled)
    }
}
