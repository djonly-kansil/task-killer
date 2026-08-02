package com.example

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?,
    val isSelected: Boolean = false,
    val isSystemApp: Boolean = false
)
