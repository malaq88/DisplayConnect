package com.example.displayconnect.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.displayconnect.models.AppSettings
import com.example.displayconnect.routing.RouteProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "display_connect_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            espIp = prefs[Keys.ESP_IP] ?: AppSettings().espIp,
            espPort = prefs[Keys.ESP_PORT] ?: AppSettings().espPort,
            navUpdateHz = prefs[Keys.NAV_UPDATE_HZ] ?: AppSettings().navUpdateHz,
            mapScaleMeters = prefs[Keys.MAP_SCALE_METERS] ?: AppSettings().mapScaleMeters,
            routeProfile = RouteProfile.fromStorage(prefs[Keys.ROUTE_PROFILE]),
            destQuery = prefs[Keys.DEST_QUERY] ?: "",
            destLabel = prefs[Keys.DEST_LABEL] ?: "",
            destLat = prefs[Keys.DEST_LAT] ?: "",
            destLon = prefs[Keys.DEST_LON] ?: ""
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
                navUpdateHz = prefs[Keys.NAV_UPDATE_HZ] ?: AppSettings().navUpdateHz,
                mapScaleMeters = prefs[Keys.MAP_SCALE_METERS] ?: AppSettings().mapScaleMeters,
                routeProfile = RouteProfile.fromStorage(prefs[Keys.ROUTE_PROFILE]),
                destQuery = prefs[Keys.DEST_QUERY] ?: "",
                destLabel = prefs[Keys.DEST_LABEL] ?: "",
                destLat = prefs[Keys.DEST_LAT] ?: "",
                destLon = prefs[Keys.DEST_LON] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.ESP_IP] = updated.espIp
            prefs[Keys.ESP_PORT] = updated.espPort
            prefs[Keys.NAV_UPDATE_HZ] = updated.navUpdateHz
            prefs[Keys.MAP_SCALE_METERS] = updated.mapScaleMeters
            prefs[Keys.ROUTE_PROFILE] = updated.routeProfile.storageKey
            prefs[Keys.DEST_QUERY] = updated.destQuery
            prefs[Keys.DEST_LABEL] = updated.destLabel
            prefs[Keys.DEST_LAT] = updated.destLat
            prefs[Keys.DEST_LON] = updated.destLon
        }
    }

    private object Keys {
        val ESP_IP = stringPreferencesKey("esp_ip")
        val ESP_PORT = intPreferencesKey("esp_port")
        val NAV_UPDATE_HZ = intPreferencesKey("nav_update_hz")
        val MAP_SCALE_METERS = doublePreferencesKey("map_scale_meters")
        val ROUTE_PROFILE = stringPreferencesKey("route_profile")
        val DEST_QUERY = stringPreferencesKey("dest_query")
        val DEST_LABEL = stringPreferencesKey("dest_label")
        val DEST_LAT = stringPreferencesKey("dest_lat")
        val DEST_LON = stringPreferencesKey("dest_lon")
    }
}
