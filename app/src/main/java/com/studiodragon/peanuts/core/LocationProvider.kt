package com.studiodragon.peanuts.core

import android.content.Context
import android.location.Criteria
import android.location.LocationManager
import android.os.Build

class LocationProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    fun setupTestProvider(): Boolean {
        return try {
            // Remove previous test provider if present
            try {
                locationManager.removeTestProvider(providerName)
            } catch (e: Exception) {
                // Ignore if not previously registered
            }

            locationManager.addTestProvider(
                providerName,
                false, // requiresNetwork
                false, // requiresSatellite
                false, // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(providerName, true)
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun removeTestProvider() {
        try {
            locationManager.setTestProviderEnabled(providerName, false)
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLocationManager(): LocationManager = locationManager
}
