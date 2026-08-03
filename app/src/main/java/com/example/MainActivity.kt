package com.example

import android.os.Bundle
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
        
        ShizukuManager.initialize(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: TaskViewModel = viewModel()
                    // Initialize viewmodel exactly once
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
        ShizukuManager.checkPermissionAndBind()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShizukuManager.unbind()
    }
}
