package com.example

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.RandomAccessFile

data class SystemStats(
    val totalRamMb: Long,
    val usedRamMb: Long,
    val cpuUsagePercent: Float
)

class SystemMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun getStatsFlow(): Flow<SystemStats> = flow {
        var lastCpuStats = readCpuStats()
        
        while (true) {
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
            val availRamMb = memoryInfo.availMem / (1024 * 1024)
            val usedRamMb = totalRamMb - availRamMb

            val currentCpuStats = readCpuStats()
            val cpuUsage = calculateCpuUsage(lastCpuStats, currentCpuStats)
            lastCpuStats = currentCpuStats

            emit(SystemStats(totalRamMb, usedRamMb, cpuUsage))
            delay(2500) // Update every 2.5 seconds
        }
    }

    private fun readCpuStats(): CpuStats? {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            
            if (line?.startsWith("cpu ") == true) {
                val parts = line.split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                if (parts.size >= 4) {
                    val idle = parts[3]
                    val total = parts.sum()
                    return CpuStats(idle, total)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateCpuUsage(last: CpuStats?, current: CpuStats?): Float {
        if (last == null || current == null) return 0f
        
        val idleDiff = current.idle - last.idle
        val totalDiff = current.total - last.total
        
        if (totalDiff == 0L) return 0f
        
        return ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
    }

    private data class CpuStats(val idle: Long, val total: Long)
}
