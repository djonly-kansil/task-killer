package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku


object ShizukuController {

    data class BulkState(
        val runningPackages: Set<String> = emptySet(),
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
                "echo '---NETPOLICY---'; " +
                "dumpsys netpolicy"
        )

        val psPart = output.substringBefore("---NETPOLICY---")
        val netPart = output.substringAfter("---NETPOLICY---")

        val runningPackages = psPart.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "NAME" }
            .toSet()

        val uidPolicyRegex = Regex("""UID=(\d+)\s+policy=(\S+)""")
        val bgDataBlockedUids = uidPolicyRegex.findAll(netPart)
            .filter { it.groupValues[2].contains("REJECT") }
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()

        return BulkState(runningPackages, bgDataBlockedUids)
    }

    // ---------------------------------------------------------------------
    // PERMISSIONS
    // ---------------------------------------------------------------------

    /** Appops penting yang tidak muncul sebagai runtime permission biasa. */
    private val EXTRA_APPOPS = listOf(
        "MANAGE_EXTERNAL_STORAGE",
        "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS",
        "REQUEST_INSTALL_PACKAGES",
        "RUN_IN_BACKGROUND",
        "RUN_ANY_IN_BACKGROUND",
        "START_FOREGROUND",
        "GET_USAGE_STATS",
        "SCHEDULE_EXACT_ALARM",
        "PICTURE_IN_PICTURE"
    )

    /**
     * Membaca daftar izin sebuah paket:
     * - runtime permission lewat `dumpsys package <pkg>` (baris `...: granted=true/false`)
     * - install permission (tidak bisa diubah) ditandai protected
     * - appops lewat `cmd appops get <pkg>`
     */
    fun readPermissions(packageName: String): List<AppPermission> {
        if (!isReady()) return emptyList()

        val out = executeWithOutput(
            "dumpsys package $packageName; " +
                "echo '---APPOPS---'; cmd appops get $packageName"
        )
        val dumpPart = out.substringBefore("---APPOPS---")
        val appopsPart = out.substringAfter("---APPOPS---")

        val result = linkedMapOf<String, AppPermission>()

        // 1. runtime permissions: "android.permission.CAMERA: granted=true, flags=[ ... ]"
        val grantedRegex = Regex("""^\s*([A-Za-z0-9_.]+\.permission\.[A-Za-z0-9_.]+):\s*granted=(true|false)(.*)$""")
        dumpPart.lineSequence().forEach { line ->
            val m = grantedRegex.find(line) ?: return@forEach
            val name = m.groupValues[1]
            val granted = m.groupValues[2] == "true"
            val flags = m.groupValues[3]
            val protectedByFlag = flags.contains("SYSTEM_FIXED", true) || flags.contains("POLICY_FIXED", true)
            result[name] = AppPermission(
                name = name,
                label = shortPermissionLabel(name),
                isGranted = granted,
                kind = PermissionKind.RUNTIME,
                isProtected = protectedByFlag
            )
        }

        // 2. install permissions (declared tapi tidak runtime) -> protected
        val installSection = dumpPart.substringAfter("install permissions:", "").substringBefore("runtime permissions:")
        Regex("""([A-Za-z0-9_.]+\.permission\.[A-Za-z0-9_.]+):\s*granted=(true|false)""")
            .findAll(installSection)
            .forEach { m ->
                val name = m.groupValues[1]
                if (result.containsKey(name)) return@forEach
                result[name] = AppPermission(
                    name = name,
                    label = shortPermissionLabel(name),
                    isGranted = m.groupValues[2] == "true",
                    kind = PermissionKind.RUNTIME,
                    isProtected = true
                )
            }

        // 3. appops: "      MANAGE_EXTERNAL_STORAGE: allow; time=..."
        val opRegex = Regex("""^\s*([A-Z_0-9]{3,}):\s*(allow|ignore|deny|default|foreground)""")
        appopsPart.lineSequence().forEach { line ->
            val m = opRegex.find(line) ?: return@forEach
            val op = m.groupValues[1]
            val mode = m.groupValues[2]
            val key = "appop:$op"
            result[key] = AppPermission(
                name = op,
                label = prettyLabel(op),
                isGranted = mode == "allow" || mode == "foreground",
                kind = PermissionKind.APPOPS,
                isProtected = false
            )
        }

        // 4. appops penting yang belum tampil -> tampilkan sebagai default/off
        EXTRA_APPOPS.forEach { op ->
            val key = "appop:$op"
            if (!result.containsKey(key)) {
                result[key] = AppPermission(
                    name = op,
                    label = prettyLabel(op),
                    isGranted = false,
                    kind = PermissionKind.APPOPS,
                    isProtected = false
                )
            }
        }

        return result.values.sortedWith(
            compareBy({ it.kind.ordinal }, { it.label.lowercase() })
        )
    }

    private fun shortPermissionLabel(name: String): String =
        prettyLabel(name.substringAfterLast('.'))

    /** "READ_EXTERNAL_STORAGE" -> "Read External Storage" */
    fun prettyLabel(raw: String): String =
        raw.split('_', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
            .ifBlank { raw }

    /** Cek apakah sebuah izin/appop sekarang aktif. */
    private fun isPermissionGranted(packageName: String, permission: String, kind: PermissionKind): Boolean? {
        return when (kind) {
            PermissionKind.RUNTIME -> {
                val out = executeWithOutput("dumpsys package $packageName | grep -i \"$permission\"")
                when {
                    out.contains("granted=true") -> true
                    out.contains("granted=false") -> false
                    else -> null
                }
            }
            PermissionKind.APPOPS -> {
                val out = executeWithOutput("cmd appops get $packageName $permission")
                when {
                    out.contains("allow", true) || out.contains("foreground", true) -> true
                    out.contains("ignore", true) || out.contains("deny", true) ||
                        out.contains("default", true) -> false
                    else -> null
                }
            }
        }
    }

    private fun looksProtected(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("not a changeable permission") ||
            lower.contains("is not a runtime permission") ||
            lower.contains("securityexception") ||
            lower.contains("operation not allowed") ||
            lower.contains("system fixed") ||
            lower.contains("policy fixed") ||
            lower.contains("permission denial") ||
            lower.contains("not allowed to change")
    }

    /**
     * Mengubah satu izin. Mengembalikan Pair(berhasil, detail).
     * Jika perintah ditolak atau status tidak berubah -> berhasil=false dengan
     * detail "Izin dilindungi".
     */
    fun setPermission(
        packageName: String,
        permission: String,
        kind: PermissionKind,
        grant: Boolean
    ): Pair<Boolean, String> {
        if (!isReady()) return false to "Shizuku belum aktif atau izin belum diberikan."

        val commands = when (kind) {
            PermissionKind.RUNTIME -> if (grant) {
                listOf(
                    "pm grant --user 0 $packageName $permission",
                    "pm grant $packageName $permission"
                )
            } else {
                listOf(
                    "pm revoke --user 0 $packageName $permission",
                    "pm revoke $packageName $permission"
                )
            }
            PermissionKind.APPOPS -> {
                val mode = if (grant) "allow" else "ignore"
                listOf(
                    "cmd appops set --user 0 $packageName $permission $mode",
                    "appops set $packageName $permission $mode"
                )
            }
        }

        var lastOutput = ""
        var protectedHit = false
        for (cmd in commands) {
            val r = run(cmd)
            lastOutput = r.output
            if (looksProtected(r.output)) protectedHit = true
            if (r.ok) {
                // verifikasi ulang: status harus benar-benar berubah
                val now = isPermissionGranted(packageName, permission, kind)
                if (now == null || now == grant) {
                    return true to "OK"
                }
            }
        }

        // masih mungkin berubah walau output aneh
        val now = isPermissionGranted(packageName, permission, kind)
        if (now == grant) return true to "OK"

        val reason = when {
            protectedHit -> "dilindungi sistem (signature/system-fixed/policy)"
            lastOutput.isBlank() -> "perintah tidak menghasilkan perubahan"
            else -> lastOutput.trim().lines().firstOrNull()?.take(120) ?: "tidak diketahui"
        }
        return false to "Izin dilindungi: $permission ($reason)"
    }



    // ---------------------------------------------------------------------
    // FORCE STOP / KILL
    // ---------------------------------------------------------------------

    /** User id multi-user (biasanya 0, tapi bisa 10/11 di work profile / clone app). */
    private fun userIdOf(uid: Int): Int = if (uid > 0) uid / 100000 else 0

    /**
     * Rantai berlapis untuk benar-benar menghentikan aplikasi, termasuk yang
     * terikat ke proses sistem:
     *
     * 1. `am force-stop --user <u> <pkg>` (+ varian ROM lama)
     * 2. `am kill` / `cmd activity kill-uid` / `killall -9`
     * 3. Putus penahan: hentikan service yang di-bind, kunci standby bucket ke
     *    `restricted`, batalkan job & alarm terjadwal, lalu force-stop ulang.
     * 4. (opsional, aggressive) `pm disable-user` sesaat lalu `pm enable` —
     *    memutus semua binding sistem dan memaksa proses mati.
     */
    fun forceStopPackageDetailed(
        packageName: String,
        uid: Int,
        aggressive: Boolean = true
    ): ForceStopResult {
        if (!isReady()) {
            return ForceStopResult(false, true, emptyList(), "Shizuku belum aktif atau izin belum diberikan.")
        }

        val u = userIdOf(uid)
        var anyOk = false

        fun tryAll(cmds: List<String>): Boolean {
            for (cmd in cmds) {
                if (run(cmd).ok) anyOk = true
                if (!isPackageRunning(packageName)) return true
            }
            return !isPackageRunning(packageName)
        }

        // Tahap 1 — force-stop standar.
        if (tryAll(
                listOf(
                    "am force-stop --user $u $packageName",
                    "am force-stop $packageName"
                )
            )
        ) return ForceStopResult(true, false, emptyList(), "force-stop")

        // Tahap 2 — kill proses & uid.
        if (tryAll(
                listOfNotNull(
                    "am kill --user $u $packageName",
                    if (uid > 0) "cmd activity kill-uid --user $u $uid" else null,
                    "killall -9 $packageName"
                )
            )
        ) return ForceStopResult(true, false, emptyList(), "kill proses")

        // Tahap 3 — putus penahan sistem lalu force-stop ulang.
        stopBoundServices(packageName, u)
        run("am set-standby-bucket $packageName restricted")
        run("cmd jobscheduler cancel-all $packageName")
        run("cmd jobscheduler cancel $packageName")
        run("cmd deviceidle whitelist -$packageName")
        run("cmd appops set $packageName RUN_ANY_IN_BACKGROUND ignore")
        run("cmd appops set $packageName RUN_IN_BACKGROUND ignore")
        run("cmd appops set $packageName START_FOREGROUND ignore")

        if (tryAll(
                listOf(
                    "am force-stop --user $u $packageName",
                    "am kill --user $u $packageName"
                )
            )
        ) return ForceStopResult(true, false, emptyList(), "putus penahan sistem")

        // Tahap 4 — cabut aplikasi sesaat (memutus semua binding sistem).
        if (aggressive) {
            val disabled = run("pm disable-user --user $u $packageName").ok ||
                run("pm disable-user $packageName").ok
            if (disabled) {
                run("am force-stop --user $u $packageName")
                Thread.sleep(400)
                val dead = !isPackageRunning(packageName)
                // Selalu nyalakan kembali supaya aplikasi tidak hilang dari launcher.
                run("pm enable --user $u $packageName")
                run("pm enable $packageName")
                if (dead) {
                    return ForceStopResult(true, false, emptyList(), "disable sesaat")
                }
            }
        }

        // Masih hidup -> cari siapa yang menahannya.
        val holders = findKeepAliveHolders(packageName)
        return ForceStopResult(anyOk, true, holders, "masih hidup setelah semua tahap")
    }

    /** Hentikan seluruh service milik paket yang sedang di-bind proses lain. */
    private fun stopBoundServices(packageName: String, userId: Int) {
        val out = executeWithOutput("dumpsys activity services $packageName")
        if (out.isBlank()) return
        Regex("""ServiceRecord\{[^}]*\s($packageName/[A-Za-z0-9_.\$]+)""")
            .findAll(out)
            .map { it.groupValues[1] }
            .distinct()
            .take(12)
            .forEach { component ->
                run("am stopservice --user $userId $component")
            }
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

    // ---------------------------------------------------------------------
    // RAM PER APLIKASI
    // ---------------------------------------------------------------------

    /**
     * Pemakaian RAM (RSS, dalam MB) per paket yang sedang berjalan.
     * Proses anak (`pkg:remote`) dijumlahkan ke paket induknya.
     */
    fun getRunningPackageMemoryMb(): Map<String, Float> {
        if (!isReady()) return emptyMap()
        val out = executeWithOutput("ps -A -o RSS,NAME")
        if (out.isBlank()) return emptyMap()

        val result = mutableMapOf<String, Float>()
        out.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("RSS")) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 2) return@forEach
            val rssKb = parts[0].toLongOrNull() ?: return@forEach
            val procName = parts[1]
            // hanya proses aplikasi (punya titik pada nama paket)
            if (!procName.contains('.')) return@forEach
            val pkg = procName.substringBefore(':')
            result[pkg] = (result[pkg] ?: 0f) + rssKb / 1024f
        }
        return result
    }
}

