package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.example.BuildConfig
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 10001

    private val _isBinderAlive = MutableStateFlow(false)
    val isBinderAlive: StateFlow<Boolean> = _isBinderAlive.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(false)
    val isPermissionGranted: StateFlow<Boolean> = _isPermissionGranted.asStateFlow()

    private val _isUserServiceBound = MutableStateFlow(false)
    val isUserServiceBound: StateFlow<Boolean> = _isUserServiceBound.asStateFlow()

    private val _statusText = MutableStateFlow("Shizuku Tidak Terhubung")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private var shellService: IShellService? = null
    private var appContext: Context? = null
    private var isInitialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received.")
        _isBinderAlive.value = true
        checkPermissionAndBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead.")
        _isBinderAlive.value = false
        _isPermissionGranted.value = false
        _isUserServiceBound.value = false
        shellService = null
        _statusText.value = "Shizuku Tidak Terhubung"
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                _isPermissionGranted.value = granted
                if (granted) {
                    _statusText.value = "Menghubungkan ke Shizuku Service..."
                    bindUserService()
                } else {
                    _statusText.value = "Izin Shizuku Ditolak"
                }
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "ShellService connected.")
            try {
                shellService = IShellService.Stub.asInterface(service)
                _isUserServiceBound.value = true
                _statusText.value = "Shizuku Terhubung & Aktif"
            } catch (e: Exception) {
                Log.e(TAG, "Gagal mengonversi interface ShellService", e)
                _statusText.value = "Gagal Menghubungkan Service"
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "ShellService disconnected.")
            shellService = null
            _isUserServiceBound.value = false
            if (_isBinderAlive.value && _isPermissionGranted.value) {
                _statusText.value = "Koneksi Service Terputus, Mencoba Ulang..."
                bindUserService()
            } else {
                _statusText.value = "Shizuku Tidak Terhubung"
            }
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            isInitialized = true
            checkPermissionAndBind()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Shizuku listeners", e)
            _statusText.value = "Shizuku Tidak Tersedia di Perangkat Ini"
        }
    }

    fun checkPermissionAndBind() {
        try {
            val alive = Shizuku.pingBinder()
            _isBinderAlive.value = alive
            if (!alive) {
                _statusText.value = "Shizuku Tidak Terhubung (Pastikan aplikasi Shizuku berjalan)"
                return
            }

            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                _statusText.value = "Versi Shizuku Terlalu Lama (Minimal V11)"
                return
            }

            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            _isPermissionGranted.value = granted

            if (granted) {
                _statusText.value = "Menghubungkan ke Shizuku Service..."
                bindUserService()
            } else {
                _statusText.value = "Izin Shizuku Belum Diberikan"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            _isBinderAlive.value = false
            _statusText.value = "Shizuku Tidak Terhubung"
        }
    }

    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                _statusText.value = "Shizuku Tidak Terhubung"
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                _isPermissionGranted.value = true
                bindUserService()
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
            _statusText.value = "Gagal Meminta Izin Shizuku"
        }
    }

    private fun bindUserService() {
        val context = appContext ?: return
        if (_isUserServiceBound.value) return
        
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shell_service")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            Shizuku.bindUserService(args, serviceConnection)
            Log.i(TAG, "Binding Shizuku UserService...")
        } catch (e: Exception) {
            Log.e(TAG, "Error binding Shizuku UserService", e)
            _statusText.value = "Gagal Menghubungkan ke Service"
        }
    }

    fun unbind() {
        try {
            val context = appContext ?: return
            if (_isUserServiceBound.value) {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, ShellService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("shell_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE)

                Shizuku.unbindUserService(args, serviceConnection, true)
                _isUserServiceBound.value = false
            }
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding Shizuku UserService", e)
        }
    }

    fun execCommand(cmd: String): String {
        val service = shellService
        if (service != null && _isUserServiceBound.value) {
            return try {
                service.execCommand(cmd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to exec command via UserService: $cmd", e)
                "ERROR: ${e.message}"
            }
        }
        return "ERROR: Shizuku UserService belum terhubung. Pastikan Shizuku berjalan dan izin diberikan."
    }
}
