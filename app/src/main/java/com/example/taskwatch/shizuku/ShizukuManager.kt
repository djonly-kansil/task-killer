package com.example.taskwatch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import com.example.taskwatch.BuildConfig

class ShizukuManager(private val context: Context) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private var userService: IUserService? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkState()
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isReady.value = false
        userService = null
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU && grantResult == PackageManager.PERMISSION_GRANTED) {
            _permissionGranted.value = true
            bindService()
        } else {
            _permissionGranted.value = false
        }
    }
    
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: android.os.IBinder?) {
            if (service != null && service.pingBinder()) {
                userService = IUserService.Stub.asInterface(service)
                _isReady.value = true
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            _isReady.value = false
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        checkState()
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        if (userService != null) {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
            userService = null
        }
    }

    fun requestPermission() {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return
        Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
    }

    private fun checkState() {
        if (Shizuku.pingBinder()) {
            val hasPermission = if (Shizuku.isPreV11()) false else Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            _permissionGranted.value = hasPermission
            if (hasPermission) {
                bindService()
            }
        } else {
            _isReady.value = false
            _permissionGranted.value = false
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, UserServiceImpl::class.java.name))
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
    }

    private fun bindService() {
        if (userService == null) {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }
    }

    fun listRunningProcesses(): String {
        return try {
            userService?.listRunningProcesses() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun forceStopPackage(packageName: String): Boolean {
        return try {
            userService?.forceStopPackage(packageName) ?: false
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val REQUEST_CODE_SHIZUKU = 1001
    }
}
