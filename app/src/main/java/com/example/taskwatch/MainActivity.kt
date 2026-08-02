package com.example.taskwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.taskwatch.shizuku.ShizukuManager
import com.example.taskwatch.ui.MainScreen
import com.example.taskwatch.ui.SettingsScreen
import com.example.taskwatch.ui.theme.MyApplicationTheme
import com.example.taskwatch.viewmodel.ProcessViewModel
import com.example.taskwatch.viewmodel.ProcessViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var shizukuManager: ShizukuManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        shizukuManager = ShizukuManager(this)
        
        val viewModel = ViewModelProvider(
            this,
            ProcessViewModelFactory(this, shizukuManager)
        )[ProcessViewModel::class.java]

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                
                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shizukuManager.destroy()
    }
}
