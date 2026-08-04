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
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer

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

        @Volatile
        var isTunnelActive: Boolean = false
            private set

        @Volatile
        private var activeInstance: LocalVpnService? = null

        fun reloadRules(context: Context) {
            activeInstance?.let { service ->
                Handler(Looper.getMainLooper()).post {
                    service.rebuild()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

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
                }
            }
        }

        val oldFd = tunFd
        val oldThread = readerThread

        if (!addedAny) {
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
        }

        if (newFd == null) {
            oldThread?.interrupt()
            oldFd?.closeQuietly()
            tunFd = null
            readerThread = null
            isTunnelActive = false
            return
        }

        tunFd = newFd
        readerThread = startDropperThread(newFd).also { it.start() }
        isTunnelActive = true

        oldThread?.interrupt()
        oldFd?.closeQuietly()
    }

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
                    if (length < 0) break
                }
            } catch (e: IOException) {
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
