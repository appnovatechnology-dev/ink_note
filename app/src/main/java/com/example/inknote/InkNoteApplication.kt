package com.example.inknote

import android.app.Application
import android.content.ComponentCallbacks2

class InkNoteApplication : Application(), ComponentCallbacks2 {

    override fun onCreate() {
        super.onCreate()
        com.example.inknote.data.preferences.ThemeStateHolder.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // Perform aggressive memory trim
                System.gc()
            }
            else -> {
                // Standard memory trim
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        System.gc()
    }
}
