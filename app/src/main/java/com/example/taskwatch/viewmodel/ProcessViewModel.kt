package com.example.taskwatch.viewmodel

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.taskwatch.data.ProcessInfo
import com.example.taskwatch.data.ProcessRepository
import com.example.taskwatch.data.ShizukuProcessRepository
import com.example.taskwatch.data.UsageStatsProcessRepository
import com.example.taskwatch.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AccessState {
    SHIZUKU_READY,
    SHIZUKU_UNAUTHORIZED,
    LIMITED_MODE
}

class ProcessViewModel(
    private val context: Context,
    val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes: StateFlow<List<ProcessInfo>> = _processes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _accessState = MutableStateFlow(AccessState.LIMITED_MODE)
    val accessState: StateFlow<AccessState> = _accessState.asStateFlow()

    private var repository: ProcessRepository = UsageStatsProcessRepository(context)

    init {
        viewModelScope.launch {
            shizukuManager.isReady.collect { isReady ->
                updateAccessState()
            }
        }
        viewModelScope.launch {
            shizukuManager.permissionGranted.collect { granted ->
                updateAccessState()
            }
        }
    }

    fun updateAccessState() {
        val isShizukuReady = shizukuManager.isReady.value
        val hasPermission = shizukuManager.permissionGranted.value
        val shizukuInstalled = rikka.shizuku.Shizuku.pingBinder()
        
        val newState = when {
            isShizukuReady -> AccessState.SHIZUKU_READY
            shizukuInstalled && !hasPermission -> AccessState.SHIZUKU_UNAUTHORIZED
            else -> AccessState.LIMITED_MODE
        }
        
        _accessState.value = newState
        repository = if (newState == AccessState.SHIZUKU_READY) {
            ShizukuProcessRepository(context, shizukuManager)
        } else {
            UsageStatsProcessRepository(context)
        }
        
        loadProcesses()
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadProcesses() {
        viewModelScope.launch {
            _isLoading.value = true
            _processes.value = repository.getRunningProcesses()
            _isLoading.value = false
        }
    }

    fun forceStopPackage(packageName: String) {
        viewModelScope.launch {
            val success = repository.forceStopPackage(packageName)
            if (success) {
                loadProcesses() // Refresh list
            }
        }
    }
}

class ProcessViewModelFactory(
    private val context: Context,
    private val shizukuManager: ShizukuManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProcessViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProcessViewModel(context, shizukuManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
