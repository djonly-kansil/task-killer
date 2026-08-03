package com.example.taskwatch.data

import android.graphics.drawable.Drawable

data class ProcessInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val memoryKb: Long?,
    val isSystemApp: Boolean
)
