package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shizuku.ShizukuManager
import com.example.ui.screens.TaskScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inisialisasi Shizuku dengan pengaman try-catch
        try {
            ShizukuManager.initialize(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal inisialisasi ShizukuManager: ${e.message}")
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TaskViewModel = viewModel()
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        viewModel.initialize(applicationContext)
                    }
                    TaskScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            ShizukuManager.checkPermissionAndBind()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal mengecek permission Shizuku: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ShizukuManager.unbind()
        } catch (e: Exception) {
            Log.e("MainActivity", "Gagal unbind Shizuku: ${e.message}")
        }
    }
}
