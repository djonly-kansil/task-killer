package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Membungkus semua interaksi shell command via Shizuku di satu tempat,
 * supaya ViewModel tidak perlu tahu detail proses shell.
 */
object ShizukuController {

    /** Hasil bulk query: status SEMUA aplikasi sekaligus, dari SATU proses shell. */
    data class BulkState(
        val runningPackages: Set<String> = emptySet(),
        val bootIgnoredPackages: Set<String> = emptySet(),
        val bgDataBlockedUids: Set<Int> = emptySet()
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
     * REVISI (masalah 1 - lambat setelah Shizuku connect):
     * Sebelumnya getAppOpsStatus() dipanggil sekali per aplikasi di dalam loop di
     * ViewModel, artinya ratusan Shizuku.newProcess() berturut-turut dibuat saat
     * loadData() (satu app terinstall = satu proses shell baru). Setiap newProcess()
     * melewati IPC ke Shizuku service + fork/exec "sh" baru, jadi sangat mahal kalau
     * diulang untuk semua app terinstall. Sebelum Shizuku connect, isReady() langsung
     * false dan loop ini di-skip cepat -- itu sebabnya load pertama (belum connect)
     * terasa cepat, tapi begitu connect, cabang query nyata inilah yang berjalan.
     *
     * Sekarang SEMUA data (proses berjalan, status auto-boot, status background-data)
     * diambil dalam SATU kali panggilan shell untuk seluruh aplikasi sekaligus.
     */
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
            .filter { it.isNotEmpty() }
            .toSet()

        // Cari baris seperti "UID=10123 policy=REJECT_METERED_BACKGROUND" di dumpsys
        // netpolicy. Baris ini muncul untuk uid yang data seluler latar belakangnya
        // sudah dibatasi lewat "cmd netpolicy add restrict-background-blacklist <uid>".
        val uidPolicyRegex = Regex("""UID=(\d+)\s+policy=(\S+)""")
        val bgDataBlockedUids = uidPolicyRegex.findAll(netPart)
            .filter { it.groupValues[2].contains("REJECT") }
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .toSet()

        return BulkState(runningPackages, bootIgnoredPackages, bgDataBlockedUids)
    }

    /**
     * REVISI (masalah 4 - kill belum "powerfull"):
     * Sebelumnya perintah kedua ("cmd activity kill-uid --user 0 $packageName")
     * mengirim NAMA PAKET ke parameter yang seharusnya UID numerik, jadi command
     * ini pasti selalu gagal diam-diam (kegagalannya tersembunyi karena error hanya
     * ditampilkan kalau KEDUA command gagal). Sekarang uid asli dikirim, dan
     * ditambahkan --user 0 di force-stop juga sesuai masukan Anda, supaya konsisten
     * untuk device dengan banyak profil/user.
     */
    fun forceStopPackage(packageName: String, uid: Int): Boolean {
        val ok1 = execute("am force-stop --user 0 $packageName")
        val ok2 = if (uid > 0) execute("cmd activity kill-uid --user 0 $uid") else false
        return ok1 || ok2
    }

    /** Cek status proses SATU aplikasi secara real, dipakai untuk verifikasi setelah kill. */
    fun isPackageRunning(packageName: String): Boolean {
        if (!isReady()) return false
        val output = executeWithOutput("ps -A -o NAME | grep -x \"$packageName\"")
        return output.trim().isNotEmpty()
    }
}
