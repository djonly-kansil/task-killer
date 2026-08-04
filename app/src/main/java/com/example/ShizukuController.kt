package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku


object ShizukuController {

    data class BulkState(
        val runningPackages: Set<String> = emptySet(),
        val bootIgnoredPackages: Set<String> = emptySet(),
        val bgDataBlockedUids: Set<Int> = emptySet()
    ) {
        fun isRunning(packageName: String): Boolean =
            runningPackages.any { it == packageName || it.startsWith("$packageName:") }
    }

    /** Hasil eksekusi perintah shell lewat Shizuku. */
    data class CmdResult(val exitCode: Int, val output: String) {
        val ok: Boolean
            get() = exitCode == 0 && !looksLikeError

        val looksLikeError: Boolean
            get() {
                val lower = output.lowercase()
                return lower.contains("exception") ||
                    lower.contains("error:") ||
                    lower.contains("permission denial") ||
                    lower.contains("unknown command") ||
                    lower.contains("not found") ||
                    lower.contains("failure")
            }
    }

    /** Hasil percobaan kill sebuah aplikasi. */
    data class ForceStopResult(
        val commandSucceeded: Boolean,
        val stillRunning: Boolean,
        /** Aplikasi/komponen lain yang menahan app ini tetap hidup. */
        val keptAliveBy: List<String> = emptyList(),
        val diagnostic: String? = null
    )

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

    fun run(command: String): CmdResult {
        if (!isReady()) return CmdResult(-1, "Shizuku tidak siap")
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "$command 2>&1"), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            CmdResult(code, output)
        } catch (e: Exception) {
            CmdResult(-1, e.message ?: "exception")
        }
    }

    fun execute(command: String): Boolean = run(command).ok

    fun executeWithOutput(command: String): String = run(command).output

    fun getBulkState(): BulkState {
        if (!isReady()) return BulkState()

        val output = executeWithOutput(
            "ps -A -o NAME; " +
                "echo '---BOOT_IGNORE---'; " +
                "cmd appops query-op BOOT_COMPLETED ignore; " +
                "echo '---NETPOLICY---'; " +
                "dumpsys netpolicy"
        )

        val psPart = output.substringBefore("---BOOT_IGNORE---")
        val bootPart = output.substringAfter("---BOOT_IGNORE---").substringBefore("---NETPOLICY---")
        val netPart = output.substringAfter("---NETPOLICY---")

        val runningPackages = psPart.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "NAME" }
            .toSet()

        val bootIgnoredPackages = bootPart.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(" ") }
            .toSet()

        val uidPolicyRegex = Regex("""UID=(\d+)\s+policy=(\S+)""")
        val bgDataBlockedUids = uidPolicyRegex.findAll(netPart)
            .filter { it.groupValues[2].contains("REJECT") }
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()

        return BulkState(runningPackages, bootIgnoredPackages, bgDataBlockedUids)
    }

    // ---------------------------------------------------------------------
    // AUTO-BOOT
    // ---------------------------------------------------------------------

    /**
     * Mengatur auto-boot dengan rantai paksa:
     * 1. `cmd appops set --user 0 <pkg> BOOT_COMPLETED allow|ignore`
     * 2. `appops set` (tanpa --user, ROM lama)
     * 3. `pm disable-user/enable --user 0 <pkg>/<BootReceiver>` untuk setiap receiver
     *    yang mendengarkan BOOT_COMPLETED / QUICKBOOT / LOCKED_BOOT_COMPLETED.
     *
     * @return Pair(berhasil, detail langkah yang dipakai / alasan gagal)
     */
    fun setAutoBootEnabled(packageName: String, enable: Boolean): Pair<Boolean, String> {
        if (!isReady()) return false to "Shizuku belum aktif atau izin belum diberikan."

        val mode = if (enable) "allow" else "ignore"

        val a = run("cmd appops set --user 0 $packageName BOOT_COMPLETED $mode")
        if (a.ok) return true to "appops BOOT_COMPLETED=$mode"

        val b = run("appops set $packageName BOOT_COMPLETED $mode")
        if (b.ok) return true to "appops (legacy) BOOT_COMPLETED=$mode"

        // Fallback paksa: enable/disable komponen BootReceiver-nya langsung.
        val receivers = findBootReceivers(packageName)
        if (receivers.isEmpty()) {
            return false to "appops ditolak & tidak ada boot receiver yang bisa dimatikan. ${a.output.trim().take(160)}"
        }

        var changed = 0
        val failed = mutableListOf<String>()
        receivers.forEach { component ->
            val cmd = if (enable) {
                "pm enable --user 0 $component"
            } else {
                "pm disable-user --user 0 $component"
            }
            val r = run(cmd)
            if (r.ok) changed++ else failed.add(component.substringAfterLast('/'))
        }

        return if (changed > 0) {
            true to "Paksa via pm ${if (enable) "enable" else "disable-user"} pada $changed receiver."
        } else {
            false to "Gagal: appops ditolak dan pm disable-user gagal (${failed.joinToString()})."
        }
    }

    /** Mencari komponen receiver boot milik sebuah paket. */
    private fun findBootReceivers(packageName: String): List<String> {
        val actions = listOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.LOCKED_BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON"
        )
        val result = linkedSetOf<String>()
        actions.forEach { action ->
            val out = executeWithOutput("pm query-receivers --user 0 --components -a $action | grep $packageName")
            out.lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("$packageName/") }
                .forEach { result.add(it) }

            if (result.isEmpty()) {
                // ROM tanpa --components: parse format "name=pkg/.Receiver"
                val out2 = executeWithOutput("pm query-receivers --user 0 -a $action | grep -i $packageName")
                Regex("""$packageName/[A-Za-z0-9_.$]+""").findAll(out2).forEach { result.add(it.value) }
            }
        }
        return result.toList()
    }

    // ---------------------------------------------------------------------
    // FORCE STOP / KILL
    // ---------------------------------------------------------------------

    /**
     * Rantai paksa untuk mematikan aplikasi:
     * 1. `am force-stop --user 0 <pkg>`
     * 2. `am force-stop <pkg>` (ROM lama)
     * 3. `cmd activity kill-uid --user 0 <uid>` / `am kill --user 0 <pkg>`
     * 4. `killall -9` pada nama proses
     * Jika masih hidup -> diagnosa lewat `dumpsys activity services/providers` untuk
     * mengetahui aplikasi lain yang menahannya tetap aktif.
     */
    fun forceStopPackageDetailed(packageName: String, uid: Int): ForceStopResult {
        if (!isReady()) {
            return ForceStopResult(false, true, emptyList(), "Shizuku belum aktif atau izin belum diberikan.")
        }

        var anyOk = false
        val steps = listOf(
            "am force-stop --user 0 $packageName",
            "am force-stop $packageName",
            if (uid > 0) "cmd activity kill-uid --user 0 $uid" else null,
            "am kill --user 0 $packageName",
            "killall -9 $packageName"
        ).filterNotNull()

        for (cmd in steps) {
            if (run(cmd).ok) anyOk = true
            if (!isPackageRunning(packageName)) {
                return ForceStopResult(true, false)
            }
        }

        // Masih hidup -> cari siapa yang menahannya.
        val holders = findKeepAliveHolders(packageName)
        return ForceStopResult(anyOk, true, holders)
    }

    /** Kompatibilitas lama. */
    fun forceStopPackage(packageName: String, uid: Int): Boolean =
        forceStopPackageDetailed(packageName, uid).let { it.commandSucceeded && !it.stillRunning }

    /**
     * Menganalisa `dumpsys activity services/providers` untuk menemukan paket lain
     * yang mem-bind service atau memakai provider dari aplikasi ini sehingga proses
     * langsung hidup kembali.
     */
    fun findKeepAliveHolders(packageName: String): List<String> {
        val out = executeWithOutput(
            "dumpsys activity services $packageName; " +
                "echo '---PROVIDERS---'; dumpsys activity providers $packageName"
        )
        if (out.isBlank()) return emptyList()

        val holders = linkedSetOf<String>()

        // ConnectionRecord baris: "* ConnectionRecord{... u0 CR ...} binder=... (pid=123 com.other.app)"
        Regex("""ConnectionRecord\{[^}]*\}[^\n]*""").findAll(out).forEach { m ->
            Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+){2,}""").findAll(m.value).forEach { p ->
                val pkg = p.value
                if (!pkg.startsWith(packageName) && !pkg.startsWith("android.") &&
                    !pkg.startsWith("com.android.internal")
                ) holders.add(pkg)
            }
        }

        // Baris "Client ... com.other.app" pada providers / "Bindings:" section.
        out.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("Client") || trimmed.contains("client=") ||
                trimmed.startsWith("-> ") || trimmed.contains("proc=")
            ) {
                Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+){2,}""").findAll(trimmed).forEach { p ->
                    val pkg = p.value.substringBefore('/')
                    if (!pkg.startsWith(packageName) && !pkg.startsWith("android.") &&
                        !pkg.startsWith("com.android.internal")
                    ) holders.add(pkg)
                }
            }
        }

        return holders.take(6).toList()
    }

    fun isPackageRunning(packageName: String): Boolean {
        if (!isReady()) return false
        val output = executeWithOutput("ps -A -o NAME")
        return output.lineSequence()
            .map { it.trim() }
            .any { it == packageName || it.startsWith("$packageName:") }
    }
}
