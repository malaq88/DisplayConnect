package com.example.displayconnect.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.displayconnect.models.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "display_connect_settings")

/**
 * Persistência das configurações via DataStore Preferences.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            espIp = prefs[Keys.ESP_IP] ?: AppSettings().espIp,
            espPort = prefs[Keys.ESP_PORT] ?: AppSettings().espPort,
            targetFps = prefs[Keys.TARGET_FPS] ?: AppSettings().targetFps,
            jpegQuality = prefs[Keys.JPEG_QUALITY] ?: AppSettings().jpegQuality,
            targetWidth = prefs[Keys.TARGET_WIDTH] ?: AppSettings().targetWidth,
            targetHeight = prefs[Keys.TARGET_HEIGHT] ?: AppSettings().targetHeight,
            rotationDegrees = prefs[Keys.ROTATION] ?: AppSettings().rotationDegrees,
            batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: AppSettings().batterySaverMode,
            transmitOnlyOnChanges = prefs[Keys.TRANSMIT_ON_CHANGES] ?: AppSettings().transmitOnlyOnChanges,
            cropTopPercent = prefs[Keys.CROP_TOP] ?: AppSettings().cropTopPercent,
            cropBottomPercent = prefs[Keys.CROP_BOTTOM] ?: AppSettings().cropBottomPercent,
            cropLeftPercent = prefs[Keys.CROP_LEFT] ?: AppSettings().cropLeftPercent,
            cropRightPercent = prefs[Keys.CROP_RIGHT] ?: AppSettings().cropRightPercent,
            frameDiffThreshold = prefs[Keys.FRAME_DIFF_THRESHOLD] ?: AppSettings().frameDiffThreshold
        )
    }

    suspend fun updateEspConnection(ip: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ESP_IP] = ip
            prefs[Keys.ESP_PORT] = port
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                espIp = prefs[Keys.ESP_IP] ?: AppSettings().espIp,
                espPort = prefs[Keys.ESP_PORT] ?: AppSettings().espPort,
                targetFps = prefs[Keys.TARGET_FPS] ?: AppSettings().targetFps,
                jpegQuality = prefs[Keys.JPEG_QUALITY] ?: AppSettings().jpegQuality,
                targetWidth = prefs[Keys.TARGET_WIDTH] ?: AppSettings().targetWidth,
                targetHeight = prefs[Keys.TARGET_HEIGHT] ?: AppSettings().targetHeight,
                rotationDegrees = prefs[Keys.ROTATION] ?: AppSettings().rotationDegrees,
                batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: AppSettings().batterySaverMode,
                transmitOnlyOnChanges = prefs[Keys.TRANSMIT_ON_CHANGES] ?: AppSettings().transmitOnlyOnChanges,
                cropTopPercent = prefs[Keys.CROP_TOP] ?: AppSettings().cropTopPercent,
                cropBottomPercent = prefs[Keys.CROP_BOTTOM] ?: AppSettings().cropBottomPercent,
                cropLeftPercent = prefs[Keys.CROP_LEFT] ?: AppSettings().cropLeftPercent,
                cropRightPercent = prefs[Keys.CROP_RIGHT] ?: AppSettings().cropRightPercent,
                frameDiffThreshold = prefs[Keys.FRAME_DIFF_THRESHOLD] ?: AppSettings().frameDiffThreshold
            )
            val updated = transform(current)
            prefs[Keys.ESP_IP] = updated.espIp
            prefs[Keys.ESP_PORT] = updated.espPort
            prefs[Keys.TARGET_FPS] = updated.targetFps
            prefs[Keys.JPEG_QUALITY] = updated.jpegQuality
            prefs[Keys.TARGET_WIDTH] = updated.targetWidth
            prefs[Keys.TARGET_HEIGHT] = updated.targetHeight
            prefs[Keys.ROTATION] = updated.rotationDegrees
            prefs[Keys.BATTERY_SAVER] = updated.batterySaverMode
            prefs[Keys.TRANSMIT_ON_CHANGES] = updated.transmitOnlyOnChanges
            prefs[Keys.CROP_TOP] = updated.cropTopPercent
            prefs[Keys.CROP_BOTTOM] = updated.cropBottomPercent
            prefs[Keys.CROP_LEFT] = updated.cropLeftPercent
            prefs[Keys.CROP_RIGHT] = updated.cropRightPercent
            prefs[Keys.FRAME_DIFF_THRESHOLD] = updated.frameDiffThreshold
        }
    }

    private object Keys {
        val ESP_IP = stringPreferencesKey("esp_ip")
        val ESP_PORT = intPreferencesKey("esp_port")
        val TARGET_FPS = intPreferencesKey("target_fps")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val TARGET_WIDTH = intPreferencesKey("target_width")
        val TARGET_HEIGHT = intPreferencesKey("target_height")
        val ROTATION = intPreferencesKey("rotation_degrees")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver_mode")
        val TRANSMIT_ON_CHANGES = booleanPreferencesKey("transmit_only_on_changes")
        val CROP_TOP = floatPreferencesKey("crop_top_percent")
        val CROP_BOTTOM = floatPreferencesKey("crop_bottom_percent")
        val CROP_LEFT = floatPreferencesKey("crop_left_percent")
        val CROP_RIGHT = floatPreferencesKey("crop_right_percent")
        val FRAME_DIFF_THRESHOLD = floatPreferencesKey("frame_diff_threshold")
    }
}
