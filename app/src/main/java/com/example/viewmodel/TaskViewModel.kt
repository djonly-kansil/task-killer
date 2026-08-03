package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.model.AppItem
import com.example.shizuku.ShizukuManager
import com.example.whitelist.WhitelistManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    USER_APPS("User Apps"),
    SYSTEM_APPS("System Apps"),
    RUNNING_APPS("Running Apps")
}

data class TaskUiState(
    val selectedTab: AppTab = AppTab.USER_APPS,
    val searchQuery: String = "",
    val showRunningOnly: Boolean = false,
    val userApps: List<AppItem> = emptyList(),
    val systemApps: List<AppItem> = emptyList(),
    val isLoading: Boolean = false,
    val whitelist: Set<String> = emptySet(),
    val selectedPackageNames: Set<String> = emptySet(),
    val showBatchConfirmDialog: Boolean = false,
    val selectedAppForModal: AppItem? = null,
    val showAppInfoDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val noticeCardDismissed: Boolean = false
) {
    val filteredApps: List<AppItem>
        get() {
            val baseList = when (selectedTab) {
                AppTab.USER_APPS -> userApps
                AppTab.SYSTEM_APPS -> systemApps
                AppTab.RUNNING_APPS -> (userApps + systemApps).filter { it.isRunning }.distinctBy { it.packageName }
            }

            return baseList.filter { app ->
                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)
                }

                val matchesRunning = if (showRunningOnly && selectedTab != AppTab.RUNNING_APPS) {
                    app.isRunning
                } else {
                    true
                }

                matchesSearch && matchesRunning
            }
        }
}

class TaskViewModel : ViewModel() {
    private const val TAG = "TaskViewModel"

    private val _uiState = MutableStateFlow(TaskUiState())
    val uiState: StateFlow<TaskUiState> = _uiState.asStateFlow()

    fun initialize(context: Context) {
        viewModelScope.launch {
            val whitelist = WhitelistManager.getDynamicWhitelist(context)
            _uiState.update { it.copy(whitelist = whitelist) }
            refreshApps(context)
        }
    }

    fun refreshApps(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val runningPackages = AppRepository.getRunningPackageNames()
                val whitelist = _uiState.value.whitelist
                val userApps = AppRepository.loadUserApps(context, whitelist, runningPackages)
                val systemApps = AppRepository.loadSystemApps(context, whitelist, runningPackages)

                _uiState.update {
                    it.copy(
                        userApps = userApps,
                        systemApps = systemApps,
                        isLoading = false
                    )
                }
                Log.i(TAG, "Refresh completed: ${userApps.size} user apps, ${systemApps.size} system apps.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh applications list", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "Gagal memuat daftar aplikasi: ${e.message}"
                    )
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab, selectedPackageNames = emptySet()) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setShowRunningOnly(show: Boolean) {
        _uiState.update { it.copy(showRunningOnly = show) }
    }

    fun toggleSelection(packageName: String) {
        _uiState.update { state ->
            val current = state.selectedPackageNames.toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            state.copy(selectedPackageNames = current)
        }
    }

    fun selectAllInCurrentTab() {
        _uiState.update { state ->
            val nonWhitelisted = state.filteredApps
                .filter { !it.isWhitelisted }
                .map { it.packageName }
                .toSet()
            state.copy(selectedPackageNames = nonWhitelisted)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackageNames = emptySet()) }
    }

    fun openAppModal(app: AppItem) {
        _uiState.update { it.copy(selectedAppForModal = app) }
    }

    fun closeAppModal() {
        _uiState.update { it.copy(selectedAppForModal = null) }
    }

    fun openAppInfoDialog(app: AppItem) {
        _uiState.update {
            it.copy(
                selectedAppForModal = app,
                showAppInfoDialog = true
            )
        }
    }

    fun closeAppInfoDialog() {
        _uiState.update { it.copy(showAppInfoDialog = false) }
    }

    fun forceStopApp(app: AppItem, context: Context) {
        viewModelScope.launch {
            try {
                val output = ShizukuManager.execCommand("am force-stop ${app.packageName}")
                Log.d(TAG, "Force stop output for ${app.packageName}: $output")
                _uiState.update {
                    it.copy(
                        selectedAppForModal = null,
                        snackbarMessage = "Berhasil force-stop ${app.appName}"
                    )
                }
                refreshApps(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error force stopping ${app.packageName}", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Gagal force-stop ${app.appName}: ${e.message}")
                }
            }
        }
    }

    fun deepUninstallApp(app: AppItem, context: Context) {
        viewModelScope.launch {
            try {
                val clearOutput = ShizukuManager.execCommand("pm clear ${app.packageName}")
                Log.i(TAG, "pm clear output for ${app.packageName}: $clearOutput")

                var uninstallOutput = ShizukuManager.execCommand("pm uninstall ${app.packageName}")
                Log.i(TAG, "pm uninstall output: $uninstallOutput")

                if (uninstallOutput.contains("Failure", ignoreCase = true) ||
                    uninstallOutput.contains("Error", ignoreCase = true)
                ) {
                    uninstallOutput = ShizukuManager.execCommand("pm uninstall --user 0 ${app.packageName}")
                    Log.i(TAG, "pm uninstall --user 0 output: $uninstallOutput")
                }

                _uiState.update {
                    it.copy(
                        selectedAppForModal = null,
                        snackbarMessage = "Selesai deep uninstall ${app.appName}"
                    )
                }
                refreshApps(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error deep uninstalling ${app.packageName}", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Gagal uninstall ${app.appName}: ${e.message}")
                }
            }
        }
    }

    fun openBatchConfirmDialog() {
        if (_uiState.value.selectedPackageNames.isNotEmpty()) {
            _uiState.update { it.copy(showBatchConfirmDialog = true) }
        }
    }

    fun closeBatchConfirmDialog() {
        _uiState.update { it.copy(showBatchConfirmDialog = false) }
    }

    fun executeBatchForceStop(context: Context) {
        val selectedPackages = _uiState.value.selectedPackageNames.toList()
        _uiState.update { it.copy(showBatchConfirmDialog = false, isLoading = true) }

        viewModelScope.launch {
            var successCount = 0
            for (pkg in selectedPackages) {
                try {
                    val res = ShizukuManager.execCommand("am force-stop $pkg")
                    if (!res.startsWith("ERROR:")) {
                        successCount++
                    } else {
                        Log.w(TAG, "Batch force-stop error on $pkg: $res")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception batch force-stopping $pkg", e)
                }
            }

            _uiState.update {
                it.copy(
                    selectedPackageNames = emptySet(),
                    snackbarMessage = "Berhasil menghentikan $successCount dari ${selectedPackages.size} aplikasi terpilih"
                )
            }
            refreshApps(context)
        }
    }

    fun dismissNoticeCard() {
        _uiState.update { it.copy(noticeCardDismissed = true) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
