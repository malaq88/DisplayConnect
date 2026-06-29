package com.example.displayconnect.navigation

import android.content.Context
import android.os.PowerManager

object NavigationWakeLock {

    private const val TAG = "DisplayConnect::Navigation"
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        if (wakeLock?.isHeld == true) return
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    @Synchronized
    fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
