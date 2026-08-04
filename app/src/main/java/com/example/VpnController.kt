package com.example

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat


object VpnController {

    
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context.applicationContext)

    fun start(context: Context) {
        val intent = Intent(context, LocalVpnService::class.java).setAction(LocalVpnService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, LocalVpnService::class.java).setAction(LocalVpnService.ACTION_STOP)
        context.startService(intent)
    }

    fun isRunning(context: Context): Boolean = LocalVpnService.isRunning

    
    fun isTunnelActive(context: Context): Boolean = LocalVpnService.isTunnelActive
}
