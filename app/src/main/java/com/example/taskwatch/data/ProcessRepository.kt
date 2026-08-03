package com.example.taskwatch.data

interface ProcessRepository {
    suspend fun getRunningProcesses(): List<ProcessInfo>
    suspend fun forceStopPackage(packageName: String): Boolean
    fun isReadOnly(): Boolean
}
