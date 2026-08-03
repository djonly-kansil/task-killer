package com.example.taskwatch.shizuku

import android.util.Log

class UserServiceImpl : IUserService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun listRunningProcesses(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "activity", "processes"))
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("UserServiceImpl", "Error listing processes", e)
            ""
        }
    }

    override fun forceStopPackage(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e("UserServiceImpl", "Error stopping package", e)
            false
        }
    }
}
