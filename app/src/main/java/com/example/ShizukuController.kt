package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

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

    // Mengambil SEMUA proses running dalam 1x panggil Shell (Sangat Cepat)
    fun getRunningPackages(): Set<String> {
        if (!isReady()) return emptySet()
        val output = executeWithOutput("ps -A -o NAME")
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "NAME" }
            .toSet()
    }

    // Force stop tingkat tinggi (termasuk --user 0 dan kill-uid)
    fun forceStopPowerful(packageName: String, uid: Int): Boolean {
        if (!isReady()) return false
        val cmd1 = "am force-stop --user 0 $packageName"
        val cmd2 = "cmd activity kill-uid --user 0 $packageName"
        val cmd3 = "am kill --user 0 $packageName"
        return execute("$cmd1; $cmd2; $cmd3")
    }
}
