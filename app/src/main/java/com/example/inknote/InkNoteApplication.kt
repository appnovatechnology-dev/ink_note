package com.example.inknote

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log
import com.google.firebase.FirebaseApp

class InkNoteApplication : Application(), ComponentCallbacks2 {

    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            Log.d("InkNoteApplication", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.w("InkNoteApplication", "Firebase auto-initialization skipped: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
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
