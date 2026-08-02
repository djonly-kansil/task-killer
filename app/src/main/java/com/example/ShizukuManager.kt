package com.example

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {
    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    fun isPermissionGranted(): Boolean {
        return if (isShizukuAvailable()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int) {
        if (isShizukuAvailable() && !isPermissionGranted()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun forceStopPackage(packageName: String): Boolean {
        if (!isPermissionGranted()) return false
        
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            
            val process = newProcessMethod.invoke(
                null, 
                arrayOf("sh", "-c", "am force-stop $packageName"), 
                null, 
                null
            ) as Process
            
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
