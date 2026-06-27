package com.example.displayconnect

import android.app.Application
import com.example.displayconnect.capture.ScreenCaptureManager
import com.example.displayconnect.network.DisplaySocketClient
import com.example.displayconnect.storage.SettingsRepository

/**
 * Application que provê instâncias compartilhadas entre Activity, ViewModels e Service.
 */
class DisplayConnectApp : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var socketClient: DisplaySocketClient
        private set

    lateinit var captureManager: ScreenCaptureManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        socketClient = DisplaySocketClient()
        captureManager = ScreenCaptureManager(this, socketClient)
    }

    override fun onTerminate() {
        captureManager.stopCapture()
        socketClient.release()
        super.onTerminate()
    }
}
