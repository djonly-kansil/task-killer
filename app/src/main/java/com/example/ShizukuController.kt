package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuController {

    data class BulkState(
        val runningPackages: Set<String> = emptySet(),
        val bootReceiverComponents: Map<String, List<String>> = emptyMap(),
        val bgDataBlockedUids: Set<Int> = emptySet()
    ) {
        fun isRunning(packageName: String): Boolean =
            runningPackages.any { it == packageName || it.startsWith("$packageName:") }
    }

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

    fun getBulkState(): BulkState {
        if (!isReady()) return BulkState()

        val output = executeWithOutput(
            "ps -A -o NAME; " +
                "echo '---BOOT_RECEIVERS---'; " +
                "cmd package query-receivers --components -a android.intent.action.BOOT_COMPLETED; " +
                "echo '---NETPOLICY---'; " +
                "dumpsys netpolicy"
        )

        val psPart = output.substringBefore("---BOOT_RECEIVERS---")
        val bootPart = output.substringAfter("---BOOT_RECEIVERS---").substringBefore("---NETPOLICY---")
        val netPart = output.substringAfter("---NETPOLICY---")

        val runningPackages = psPart.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "NAME" }
            .toSet()

        val bootReceiverComponents = bootPart.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('/') && !it.contains(' ') }
            .groupBy { it.substringBefore('/') }

        val uidPolicyRegex = Regex("""UID=(\d+)\s+policy=(\S+)""")
        val bgDataBlockedUids = uidPolicyRegex.findAll(netPart)
            .filter { it.groupValues[2].contains("REJECT") }
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()

        return BulkState(runningPackages, bootReceiverComponents, bgDataBlockedUids)
    }

    fun setComponentEnabled(component: String, enabled: Boolean): Boolean {
        val command = if (enabled) {
            "pm enable --user 0 $component"
        } else {
            "pm disable-user --user 0 $component"
        }
        return execute(command)
    }

    fun forceStopPackage(packageName: String, uid: Int): Boolean {
        val ok1 = execute("am force-stop --user 0 $packageName")
        val ok2 = if (uid > 0) execute("cmd activity kill-uid --user 0 $uid") else false
        return ok1 || ok2
    }

    fun isPackageRunning(packageName: String): Boolean {
        if (!isReady()) return false
        val output = executeWithOutput("ps -A -o NAME")
        return output.lineSequence()
            .map { it.trim() }
            .any { it == packageName || it.startsWith("$packageName:") }
    }

    fun analyzeWhyAppWontKill(packageName: String): String {
        if (!isReady()) return "Shizuku belum siap."
        val output = executeWithOutput("dumpsys activity services $packageName")
        val bindings = output.lineSequence()
            .filter { it.contains("Client:") || it.contains("binding") }
            .map { it.trim() }
            .take(3)
            .toList()
            
        return if (bindings.isNotEmpty()) {
            "Gagal kill. Ditahan oleh:\n" + bindings.joinToString("\n")
        } else {
            "Gagal kill. Aplikasi secara otomatis di-restart oleh sistem."
        }
    }
}
