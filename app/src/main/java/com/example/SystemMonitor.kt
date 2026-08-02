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
            
            val cpuUsage = if (currentCpuStats != null) {
                val usage = calculateCpuUsage(lastCpuStats, currentCpuStats)
                lastCpuStats = currentCpuStats
                usage
            } else {
                readCpuUsageFromDumpsys()
            }

            emit(SystemStats(totalRamMb, usedRamMb, cpuUsage))
            delay(2500) // Update every 2.5 seconds
        }
    }

    private fun readCpuUsageFromDumpsys(): Float {
        return try {
            val output = ShizukuManager.executeCommand("dumpsys cpuinfo")
            val totalLine = output.lines().find { it.contains("TOTAL:") } ?: return 0f
            val match = Regex("([0-9.]+)\\s*%\\s*TOTAL").find(totalLine)
            match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    private fun readCpuStats(): CpuStats? {
        return try {
            // First try reading directly (works on older Android)
            var line: String? = null
            try {
                val reader = RandomAccessFile("/proc/stat", "r")
                line = reader.readLine()
                reader.close()
            } catch (e: Exception) {
                // Fallback to Shizuku if permission denied
                val output = ShizukuManager.executeCommand("cat /proc/stat | grep '^cpu '")
                line = output.split("\n").firstOrNull { it.startsWith("cpu ") }
            }
            
            if (line?.startsWith("cpu ") == true) {
                val parts = line.trim().split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
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
