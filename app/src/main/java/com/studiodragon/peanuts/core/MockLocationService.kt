package com.studiodragon.peanuts.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.studiodragon.peanuts.R
import com.studiodragon.peanuts.ui.main.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MockLocationService : Service() {

    private lateinit var locationProvider: LocationProvider
    private var simulationEngine: SimulationEngine? = null

    companion object {
        const val ACTION_START = "com.studiodragon.peanuts.ACTION_START"
        const val ACTION_STOP = "com.studiodragon.peanuts.ACTION_STOP"
        
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_ALT = "extra_alt"

        const val CHANNEL_ID = "peanuts_location_channel"
        const val NOTIF_ID = 1001

        private val _isSimulating = MutableStateFlow(false)
        val isSimulating: StateFlow<Boolean> = _isSimulating

        private val _currentSimLocation = MutableStateFlow<Triple<Double, Double, Double>?>(null)
        val currentSimLocation: StateFlow<Triple<Double, Double, Double>?> = _currentSimLocation

        fun startService(context: Context, lat: Double, lon: Double, alt: Double = 0.0) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LON, lon)
                putExtra(EXTRA_ALT, alt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationProvider = LocationProvider(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lon = intent.getDoubleExtra(EXTRA_LON, 0.0)
                val alt = intent.getDoubleExtra(EXTRA_ALT, 0.0)
                startSimulation(lat, lon, alt)
            }
            ACTION_STOP -> {
                stopSimulation()
            }
        }
        return START_STICKY
    }

    private fun startSimulation(lat: Double, lon: Double, alt: Double) {
        val notification = createNotification(lat, lon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val success = locationProvider.setupTestProvider()
        if (success) {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            simulationEngine?.stop()
            simulationEngine = SimulationEngine(
                locationManager = locationProvider.getLocationManager(),
                sensorManager = sensorManager,
                baseLat = lat,
                baseLon = lon,
                baseAlt = alt
            )
            
            simulationEngine?.onTickListener = { loc, jitter, stateName ->
                val sats = loc.extras?.getInt("satellites") ?: 8
                updateNotification(loc.latitude, loc.longitude, loc.altitude, loc.speed, jitter, sats, stateName)
                _currentSimLocation.value = Triple(loc.latitude, loc.longitude, loc.altitude)
            }

            simulationEngine?.start()
            _isSimulating.value = true
            _currentSimLocation.value = Triple(lat, lon, alt)
        }
    }

    private fun stopSimulation() {
        simulationEngine?.stop()
        simulationEngine = null
        locationProvider.removeTestProvider()
        _isSimulating.value = false
        _currentSimLocation.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Peanuts Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active GPS simulation status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(lat: Double, lon: Double): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🥜 peanuts GPS Simulation Active")
            .setContentText(String.format("Lat: %.5f | Lon: %.5f", lat, lon))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(lat: Double, lon: Double, alt: Double, speed: Float, jitter: Double, sats: Int, state: String) {
        val title = "🥜 peanuts: $state ($sats Sats)"
        val text = "Lat: %.5f | Lon: %.5f | Alt: %.1fm | Speed: %.2fm/s | Jitter: %.2fm".format(lat, lon, alt, speed, jitter)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager?.notify(NOTIF_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopSimulation()
        super.onDestroy()
    }
}
