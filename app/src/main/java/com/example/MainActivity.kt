package com.example

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            var settings by remember { mutableStateOf(SettingsRepository.load(this)) }
            var showSettings by remember { mutableStateOf(false) }

            val darkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalStrings provides stringsFor(settings.language)) {
                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            onThemeChange = { mode ->
                                settings = settings.copy(themeMode = mode)
                                SettingsRepository.saveTheme(this, mode)
                            },
                            onLanguageChange = { lang ->
                                settings = settings.copy(language = lang)
                                SettingsRepository.saveLanguage(this, lang)
                            },
                            onBack = { showSettings = false }
                        )
                    } else {
                        AppManagerScreen(viewModel, onOpenSettings = { showSettings = true })
                    }
                }
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
        
        viewModel.refreshLiveStatus()

        
        viewModel.refreshVpnStatus(this)
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
