package com.example.shizuku

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellService : IShellService.Stub() {

    override fun execCommand(cmd: String): String {
        return try {
            Log.d("ShellService", "Executing command: $cmd")
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            Log.e("ShellService", "Error executing command: $cmd", e)
            "ERROR: ${e.localizedMessage}"
        }
    }

    override fun destroy() {
        Log.i("ShellService", "Destroying ShellService")
        try {
            System.exit(0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
