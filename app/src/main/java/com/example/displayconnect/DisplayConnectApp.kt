package com.example.displayconnect

import android.app.Application
import com.example.displayconnect.network.BleNavClient
import com.example.displayconnect.storage.SettingsRepository

class DisplayConnectApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var navClient: BleNavClient
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        navClient = BleNavClient(this)
    }

    override fun onTerminate() {
        navClient.release()
        super.onTerminate()
    }
}
