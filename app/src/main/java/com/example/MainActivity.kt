package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private val viewModel: AppManagerViewModel by viewModels()

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 100
    }

    // BroadcastReceiver untuk mendeteksi perubahan status jaringan & aplikasi HP secara Real-Time
    private val autoReloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.let {
                viewModel.loadData(it)
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkShizukuPermission()
            viewModel.checkShizukuStatus()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { viewModel.checkShizukuStatus() }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            runOnUiThread {
                viewModel.checkShizukuStatus()
                viewModel.loadData(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // Register Receiver untuk perubahan Jaringan dan Install/Uninstall/Change App
        val filter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        
        // Register receiver khusus connectivity
        registerReceiver(autoReloadReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        viewModel.loadData(this)

        setContent {
            MyApplicationTheme {
                AppManagerScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(autoReloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateRamInfo(this)
        viewModel.checkShizukuStatus()
        viewModel.loadData(this) // Refresh otomatis saat aplikasi dibuka kembali dari background
    }

    private fun checkShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
