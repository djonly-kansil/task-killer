package com.example

import android.content.pm.PackageManager
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

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            checkShizukuPermission()
            viewModel.checkShizukuStatus()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { viewModel.checkShizukuStatus() }
    }

    // REVISI: sebelumnya requestPermission() dipanggil tapi tidak ada listener
    // hasilnya sama sekali, jadi UI tidak refresh otomatis setelah user
    // menyetujui/menolak dialog izin Shizuku.
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

        viewModel.loadData(this)

        setContent {
            MyApplicationTheme {
                AppManagerScreen(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateRamInfo(this)
        viewModel.checkShizukuStatus()
        // REVISI (masalah 3): begitu kembali ke app ini -- misalnya setelah
        // mengubah pengaturan data seluler dari Pengaturan HP, atau menutup
        // aplikasi lain lewat recent apps -- status yang bisa berubah dari luar
        // langsung disegarkan otomatis, tanpa perlu tutup-buka app ini manual.
        viewModel.refreshLiveStatus()
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
