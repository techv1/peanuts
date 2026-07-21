package com.studiodragon.peanuts.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.studiodragon.peanuts.core.SimConfig
import com.studiodragon.peanuts.core.StopMode
import com.studiodragon.peanuts.data.model.SimLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.abs

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "peanuts_settings")

class RecentLocationsStore(private val context: Context) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, SimLocation::class.java)
    private val jsonAdapter = moshi.adapter<List<SimLocation>>(listType)

    companion object {
        private val RECENT_LOCATIONS_KEY = stringPreferencesKey("recent_locations")
        private val LAST_LAT_KEY = doublePreferencesKey("last_lat")
        private val LAST_LON_KEY = doublePreferencesKey("last_lon")
        private val LAST_ALT_KEY = doublePreferencesKey("last_alt")
        
        private val UPDATE_INTERVAL_KEY = longPreferencesKey("update_interval_ms")
        private val JITTER_AMP_KEY = doublePreferencesKey("jitter_amp_deg")
        private val STOP_MODE_KEY = stringPreferencesKey("stop_mode")
    }

    val recentLocationsFlow: Flow<List<SimLocation>> = context.dataStore.data.map { prefs ->
        val json = prefs[RECENT_LOCATIONS_KEY] ?: return@map emptyList()
        try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveLocation(location: SimLocation) {
        context.dataStore.edit { prefs ->
            val json = prefs[RECENT_LOCATIONS_KEY]
            val currentList = try {
                if (json != null) jsonAdapter.fromJson(json)?.toMutableList() ?: mutableListOf()
                else mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            currentList.removeAll { 
                abs(it.latitude - location.latitude) < 0.0001 && 
                abs(it.longitude - location.longitude) < 0.0001 
            }

            currentList.add(0, location)
            val trimmedList = currentList.take(5)
            prefs[RECENT_LOCATIONS_KEY] = jsonAdapter.toJson(trimmedList)
            prefs[LAST_LAT_KEY] = location.latitude
            prefs[LAST_LON_KEY] = location.longitude
            prefs[LAST_ALT_KEY] = location.altitude
        }
    }

    val lastLocationFlow: Flow<SimLocation?> = context.dataStore.data.map { prefs ->
        val lat = prefs[LAST_LAT_KEY] ?: return@map null
        val lon = prefs[LAST_LON_KEY] ?: return@map null
        val alt = prefs[LAST_ALT_KEY] ?: 0.0
        SimLocation(latitude = lat, longitude = lon, altitude = alt)
    }

    val simConfigFlow: Flow<SimConfig> = context.dataStore.data.map { prefs ->
        val interval = prefs[UPDATE_INTERVAL_KEY] ?: 1000L
        val jitter = prefs[JITTER_AMP_KEY] ?: 0.000008
        val stopModeStr = prefs[STOP_MODE_KEY] ?: StopMode.GRADUAL.name
        val stopMode = try { StopMode.valueOf(stopModeStr) } catch (e: Exception) { StopMode.GRADUAL }

        SimConfig(
            baseUpdateIntervalMs = interval,
            jitterWalkingDeg = jitter,
            stopMode = stopMode
        )
    }

    suspend fun updateSimConfig(config: SimConfig) {
        context.dataStore.edit { prefs ->
            prefs[UPDATE_INTERVAL_KEY] = config.baseUpdateIntervalMs
            prefs[JITTER_AMP_KEY] = config.jitterWalkingDeg
            prefs[STOP_MODE_KEY] = config.stopMode.name
        }
    }
}
