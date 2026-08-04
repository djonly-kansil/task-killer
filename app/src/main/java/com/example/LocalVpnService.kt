package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * VpnService lokal murni untuk FILTER per-aplikasi -- BUKAN proxy/VPN sungguhan.
 * Tidak ada server jarak jauh, tidak ada enkripsi tambahan, dan tidak ada satu pun
 * paket yang benar-benar diteruskan ke mana pun. Prinsip kerjanya:
 *
 * 1) Hanya aplikasi yang SEDANG HARUS DIBLOKIR (lihat computeCurrentlyBlockedUids())
 *    yang dimasukkan ke interface VPN lewat Builder.addAllowedApplication(). Semua
 *    aplikasi lain TIDAK disentuh sama sekali -- trafiknya tetap lewat jalur normal
 *    sistem, tidak ada overhead dan tidak lewat tunnel ini.
 * 2) Begitu sebuah app "masuk" ke tunnel ini, paketnya dibaca dari file descriptor
 *    TUN lalu SENGAJA DIBUANG (tidak pernah ditulis balik / diteruskan kemana pun).
 *    Efeknya persis seperti firewall DROP: koneksi macet/timeout dari sudut pandang
 *    app tsb -- tanpa perlu parsing header IP/TCP/UDP atau NAT sama sekali.
 * 3) Mode WIFI_ONLY / CELLULAR_ONLY dievaluasi ulang setiap kali transport jaringan
 *    aktif berubah (lewat ConnectivityManager.NetworkCallback). Contoh: app bermode
 *    WIFI_ONLY otomatis masuk daftar blokir begitu HP pindah ke data seluler, dan
 *    otomatis lepas begitu balik ke Wi-Fi -- tanpa user perlu apa-apa.
 * 4) Daftar allowed-application pada Builder tidak bisa diubah di interface yang
 *    sudah berjalan, jadi setiap perubahan (rule baru dari UI lewat reloadRules(),
 *    atau transport berubah) memicu establish() ULANG dengan fd baru; fd lama
 *    ditutup setelah fd baru siap.
 *
 * Kalau tidak ada satu pun app yang perlu diblokir saat ini, interface TIDAK
 * di-establish sama sekali (service tetap hidup di foreground, siap membangun
 * interface kapan pun ada rule baru) -- sesuai catatan di VpnRulesRepository &
 * AppManagerViewModel bahwa VPN filter "aktif" berarti siap menerapkan rule,
 * bukan berarti selalu ada tunnel yang benar-benar terbentuk.
 */
class LocalVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    @Volatile private var running = false

    private lateinit var connectivityManager: ConnectivityManager
    private var currentTransportIsWifi: Boolean = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_START = "com.example.vpn.action.START"
        const val ACTION_STOP = "com.example.vpn.action.STOP"

        private const val NOTIFICATION_CHANNEL_ID = "vpn_filter_channel"
        private const val NOTIFICATION_ID = 4201

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * REVISI (masalah: switch VPN terlihat ON tapi trafik belum benar-benar
         * difilter): isRunning di atas cuma berarti "service-nya hidup", BUKAN
         * berarti ada tunnel yang benar-benar terbentuk -- rebuild() sengaja tidak
         * memanggil builder.establish() kalau tidak ada satupun app non-ALL (lihat
         * catatan kelas). Dua konsep ini sebelumnya dicampur jadi satu boolean di
         * AppManagerViewModel/AppManagerScreen, makanya switch bisa terlihat ON
         * padahal belum ada yang benar-benar difilter. isTunnelActive di sini
         * mencerminkan status tunnel yang SEBENARNYA, terpisah dari isRunning,
         * supaya UI (lewat VpnController, perlu ditambahkan wrapper serupa
         * isRunning()) bisa menampilkan dua status ini secara jujur/berbeda kalau
         * diperlukan.
         */
        @Volatile
        var isTunnelActive: Boolean = false
            private set

        // Referensi service yang sedang berjalan supaya VpnRulesRepository bisa
        // memicu rebuild tanpa bind/IPC -- aman karena selalu dipakai di proses yang sama.
        @Volatile
        private var activeInstance: LocalVpnService? = null

        /**
         * Dipanggil VpnRulesRepository.setMode() setiap kali user mengganti mode
         * jaringan satu app. Kalau VPN filter sedang tidak aktif, tidak melakukan
         * apa-apa (rule tetap tersimpan, baru berlaku begitu VPN dinyalakan).
         */
        fun reloadRules(context: Context) {
            activeInstance?.rebuild()
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * REVISI (masalah: switch VPN nyangkut ON walau sudah di-OFF):
     * isRunning sekarang di-set false SESEGERA MUNGKIN begitu Intent ACTION_STOP
     * benar-benar diterima & diproses di sini -- bukan menunggu onDestroy() (yang
     * baru dipanggil sistem belakangan lewat siklus Service async, bisa telat
     * dibaca ViewModel). stopSelf() tetap dipanggil untuk memicu teardown asli
     * (tunFd, network callback, dll) lewat onDestroy() seperti biasa.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            running = false
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        running = true
        isRunning = true
        activeInstance = this

        startForeground(NOTIFICATION_ID, buildNotification())

        currentTransportIsWifi = detectCurrentTransport()
        registerNetworkCallback()
        rebuild()
    }

    /**
     * Bangun ulang interface VPN dari nol berdasarkan rule + transport TERKINI.
     * Dipanggil saat: VPN baru dinyalakan, rule app berubah (reloadRules), atau
     * transport jaringan aktif berganti Wi-Fi <-> seluler.
     */
    @Synchronized
    private fun rebuild() {
        if (!running) return

        val blockedUids = computeCurrentlyBlockedUids()
        val builder = Builder()
            .setSession("AppController Filter")
            .setMtu(1500)
            .addAddress("10.10.10.2", 32)
            .addRoute("0.0.0.0", 0)

        var addedAny = false
        blockedUids.forEach { uid ->
            packageManager.getPackagesForUid(uid)?.forEach { pkg ->
                try {
                    builder.addAllowedApplication(pkg)
                    addedAny = true
                } catch (e: PackageManager.NameNotFoundException) {
                    // package sempat di-uninstall di antara pembacaan rule & rebuild -- abaikan
                }
            }
        }

        val oldFd = tunFd
        val oldThread = readerThread

        if (!addedAny) {
            // Tidak ada satu app pun yang perlu diblokir saat ini -- tutup tunnel
            // sepenuhnya. Service tetap hidup, hanya belum ada interface aktif.
            oldThread?.interrupt()
            oldFd?.closeQuietly()
            tunFd = null
            readerThread = null
            isTunnelActive = false
            return
        }

        val newFd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        } ?: return

        tunFd = newFd
        readerThread = startDropperThread(newFd).also { it.start() }
        isTunnelActive = true

        oldThread?.interrupt()
        oldFd?.closeQuietly()
    }

    /**
     * Kumpulkan uid yang HARUS diblokir saat ini, gabungan dari:
     * - mode BLOCKED (selalu)
     * - mode WIFI_ONLY tapi transport aktif sekarang BUKAN Wi-Fi
     * - mode CELLULAR_ONLY tapi transport aktif sekarang Wi-Fi (bukan seluler)
     */
    private fun computeCurrentlyBlockedUids(): Set<Int> {
        val rules = VpnRulesRepository.getAllRules(applicationContext)
        val blocked = mutableSetOf<Int>()
        rules.forEach { (uid, mode) ->
            val shouldBlock = when (mode) {
                NetworkAccessMode.BLOCKED -> true
                NetworkAccessMode.WIFI_ONLY -> !currentTransportIsWifi
                NetworkAccessMode.CELLULAR_ONLY -> currentTransportIsWifi
                NetworkAccessMode.ALL -> false
            }
            if (shouldBlock) blocked.add(uid)
        }
        return blocked
    }

    private fun startDropperThread(fd: ParcelFileDescriptor): Thread {
        return Thread {
            val input = FileInputStream(fd.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val length = input.read(buffer)
                    // Sengaja tidak ditulis balik ke output -- paket dibuang di sini,
                    // ini INTI dari mekanisme block (lihat catatan kelas di atas).
                    if (length < 0) break
                }
            } catch (e: IOException) {
                // fd ditutup saat rebuild/stop -- normal, bukan error
            }
        }
    }

    private fun detectCurrentTransport(): Boolean {
        val network = connectivityManager.activeNetwork ?: return true
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun registerNetworkCallback() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                if (isWifi != currentTransportIsWifi) {
                    currentTransportIsWifi = isWifi
                    rebuild()
                }
            }
        }
        networkCallback = callback
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    override fun onRevoke() {
        // Dipanggil sistem kalau user mencabut izin VPN lewat menu Settings > VPN
        // (bukan lewat switch di app ini) -- pastikan status app ikut menyesuaikan
        // (kartu status akan sinkron sendiri lewat refreshVpnStatus() di onResume).
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        isTunnelActive = false
        activeInstance = null

        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: IllegalArgumentException) {
                // sudah ter-unregister -- abaikan
            }
        }
        networkCallback = null

        readerThread?.interrupt()
        readerThread = null
        tunFd?.closeQuietly()
        tunFd = null

        super.onDestroy()
    }

    private fun ParcelFileDescriptor.closeQuietly() {
        try {
            close()
        } catch (e: IOException) {
            // abaikan
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "VPN Filter",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("VPN Filter aktif")
            .setContentText("Memfilter akses jaringan sesuai aturan per-aplikasi")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
