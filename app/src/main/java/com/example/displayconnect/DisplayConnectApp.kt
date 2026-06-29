package com.example.displayconnect

import android.app.Application
import com.example.displayconnect.network.DisplaySocketClient
import com.example.displayconnect.storage.SettingsRepository

class DisplayConnectApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var socketClient: DisplaySocketClient
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        socketClient = DisplaySocketClient()
    }

    override fun onTerminate() {
        socketClient.release()
        super.onTerminate()
    }
}
