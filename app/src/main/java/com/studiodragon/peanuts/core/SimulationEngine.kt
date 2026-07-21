package com.studiodragon.peanuts.core

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Configuration parameters for the Sensor Fusion Simulation Engine.
 */
data class SimConfig(
    // Core Timing
    val baseUpdateIntervalMs: Long = 1000,
    val cameraInterruptDelayMs: Long = 250,      // The "Rapid Tick" when camera initializes

    // Spatial Jitter (Micro-Drift)
    val jitterWalkingDeg: Double = 0.000008,     // ~0.9m baseline jitter
    val jitterStationaryDeg: Double = 0.00000009,// ~9.8mm absolute flatline lock
    
    // Kalman Filter (Velocity lag)
    val kalmanAlpha: Double = 0.25,              // Speed adapts 25% toward actual distance per tick (Lag)
    val stationarySpeedHum: Float = 0.155f,      // The baseline Doppler noise when standing still

    // Accuracy Metrics
    val accuracyMin: Float = 3.2f,
    val accuracyMax: Float = 5.0f,
    val verticalAccBaseMultiplier: Float = 1.5f,

    // Altitude Physics
    val altitudeNoiseM: Double = 0.2,            // GPS altitude static noise
    
    // Lifecycle
    val stopMode: StopMode = StopMode.GRADUAL,
    val gradualFadeDurationMs: Long = 12000
)

enum class StopMode { INSTANT, GRADUAL, FREEZE }

enum class MotionState { 
    IDLE, 
    DROP_TO_DOC, 
    SCANNING_DOC, 
    LIFT_TO_SELFIE 
}

/**
 * Advanced Multi-Sensor GPS Simulation Engine.
 */
class SimulationEngine(
    private val locationManager: LocationManager,
    private val sensorManager: SensorManager,
    private val baseLat: Double,
    private val baseLon: Double,
    private val baseAlt: Double,
    private val config: SimConfig = SimConfig()
) : SensorEventListener {

    private var simulationJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val random = Random()
    var onTickListener: ((Location, Double, String) -> Unit)? = null

    // Position State
    private var currentLat = baseLat
    private var currentLon = baseLon
    private var currentAccuracy = (config.accuracyMin + config.accuracyMax) / 2f
    
    // Kalman Filter State
    private var reportedSpeed = 0.0
    private var lastTickNanos = SystemClock.elapsedRealtimeNanos()

    // Hardware Sensor Injection State
    private var physicalBearing: Float? = null
    private var initialPressure: Float? = null
    private var relativeAltitudeOffset: Double = 0.0

    // Sequence State Machine
    private var motionState = MotionState.IDLE
    private var stateTicksLeft = 0

    fun start() {
        if (simulationJob?.isActive == true) return

        // 1. Hijack physical hardware sensors for undeniable data matching
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)

        lastTickNanos = SystemClock.elapsedRealtimeNanos()

        simulationJob = coroutineScope.launch {
            try {
                while (isActive) {
                    processTick()
                }
            } catch (e: CancellationException) {
                // Handled
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        when (config.stopMode) {
            StopMode.INSTANT, StopMode.FREEZE -> {
                simulationJob?.cancel()
                simulationJob = null
            }
            StopMode.GRADUAL -> coroutineScope.launch { fadeAndStop() }
        }
    }

    /**
     * Triggers the physical sequence: Selfie -> Fast Drop -> Flatline Scan -> Lift
     */
    fun triggerDocumentScanSequence() {
        if (motionState == MotionState.IDLE) {
            motionState = MotionState.DROP_TO_DOC
            stateTicksLeft = 1 // 1 tick to drop
        }
    }

    private suspend fun processTick(fadeMultiplier: Double = 1.0) {
        val currentNanos = SystemClock.elapsedRealtimeNanos()
        val dtNanos = currentNanos - lastTickNanos
        lastTickNanos = currentNanos
        val dtSeconds = dtNanos / 1_000_000_000.0

        // 1. State Machine & Hardware Interrupt Timing
        var targetDelayMs = config.baseUpdateIntervalMs
        var activeJitterBase = config.jitterWalkingDeg
        var positionalJump = 0.0

        when (motionState) {
            MotionState.IDLE -> {
                activeJitterBase = config.jitterWalkingDeg
            }
            MotionState.DROP_TO_DOC -> {
                // Rapid movement down and forward
                positionalJump = 0.000005 // Approx 0.5 meters
                targetDelayMs = config.cameraInterruptDelayMs // RAPID TICK for camera OS interrupt
                stateTicksLeft--
                if (stateTicksLeft <= 0) {
                    motionState = MotionState.SCANNING_DOC
                    stateTicksLeft = 5 // Stay in doc scan for 5 ticks
                }
            }
            MotionState.SCANNING_DOC -> {
                // Sub-centimeter lock (9.8mm)
                activeJitterBase = config.jitterStationaryDeg 
                stateTicksLeft--
                if (stateTicksLeft <= 0) {
                    motionState = MotionState.LIFT_TO_SELFIE
                    stateTicksLeft = 1
                }
            }
            MotionState.LIFT_TO_SELFIE -> {
                positionalJump = -0.000005 // Revert position
                targetDelayMs = config.cameraInterruptDelayMs
                stateTicksLeft--
                if (stateTicksLeft <= 0) motionState = MotionState.IDLE
            }
        }

        // Apply CPU Thread Dispatching Jitter to the delay (simulate OS lag)
        val osJitter = random.nextGaussian() * 40
        delay((targetDelayMs + osJitter).toLong().coerceAtLeast(100))

        // 2. Spatial Math (Calculate raw movement)
        val oldLat = currentLat
        val oldLon = currentLon
        
        currentLat += (random.nextGaussian() * activeJitterBase) + positionalJump
        currentLon += (random.nextGaussian() * activeJitterBase) + positionalJump

        // Rough Haversine approx for velocity calculation (in meters)
        val dLatMeters = (currentLat - oldLat) * 111320
        val dLonMeters = (currentLon - oldLon) * 111320 * cos(Math.toRadians(baseLat))
        val distanceTraveledMeters = sqrt((dLatMeters * dLatMeters) + (dLonMeters * dLonMeters))

        // 3. KALMAN VELOCITY FILTER (Decouple speed from distance jump)
        val rawCalculatedSpeed = distanceTraveledMeters / dtSeconds
        
        if (motionState == MotionState.SCANNING_DOC || motionState == MotionState.IDLE) {
            // Apply baseline Doppler hum when still (e.g., 0.155m/s)
            val dopplerNoise = config.stationarySpeedHum + (random.nextGaussian() * 0.02)
            reportedSpeed = (config.kalmanAlpha * dopplerNoise) + ((1 - config.kalmanAlpha) * reportedSpeed)
        } else {
            // Exponential Moving Average to lag the speed spike behind the coordinate jump
            reportedSpeed = (config.kalmanAlpha * rawCalculatedSpeed) + ((1 - config.kalmanAlpha) * reportedSpeed)
        }
        val finalSpeed = reportedSpeed.toFloat().coerceAtLeast(0f) * fadeMultiplier.toFloat()

        // 4. Barometric Altitude Injection
        // We use base altitude + simulated GPS noise + real physical barometric drops
        val currentAlt = baseAlt + (random.nextGaussian() * config.altitudeNoiseM) + relativeAltitudeOffset

        // 5. Payload Assembly
        val location = Location("gps").apply {
            latitude = currentLat
            longitude = currentLon
            altitude = currentAlt
            accuracy = currentAccuracy
            speed = finalSpeed

            // Inject the real physical bearing of the device instead of a fake random walk
            physicalBearing?.let {
                bearing = it
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = currentAccuracy * config.verticalAccBaseMultiplier
                speedAccuracyMetersPerSecond = 0.1f + random.nextFloat() * 0.2f
                if (physicalBearing != null) bearingAccuracyDegrees = 5.0f + random.nextFloat() * 2f
            }

            // Undeniable System Clock Signatures
            time = System.currentTimeMillis()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = currentNanos
            }
            
            extras = Bundle().apply {
                putInt("satellites", if (motionState == MotionState.SCANNING_DOC) 12 else randomInt(8, 11))
                putFloat("hdop", if (motionState == MotionState.SCANNING_DOC) 0.8f else randomFloat(0.9f, 1.2f))
            }
        }

        try {
            locationManager.setTestProviderLocation("gps", location)
            onTickListener?.invoke(location, distanceTraveledMeters, motionState.name)
        } catch (e: Exception) {
            e.printStackTrace() // Provider not initialized
        }
    }

    private suspend fun fadeAndStop() {
        val oldJob = simulationJob
        simulationJob = null
        oldJob?.cancelAndJoin()

        val steps = (config.gradualFadeDurationMs / config.baseUpdateIntervalMs).toInt()
        for (i in steps downTo 1) {
            val fadeMultiplier = i.toDouble() / steps.toDouble()
            processTick(fadeMultiplier = fadeMultiplier)
        }
    }

    // --- HARDWARE SENSOR FUSION IMPLEMENTATION ---
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Translate rotation matrix into a physical compass bearing
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientationAngles = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                
                // Extract Azimuth (Bearing) and Pitch (Tilt)
                val azimuthDeg = (Math.toDegrees(orientationAngles[0].toDouble()) + 360) % 360
                val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()) // Range: -90 to 90
                
                physicalBearing = azimuthDeg.toFloat()

                // AUTO-TRIGGER LOGIC: 
                // If the phone drops below 25 degrees (flat) and we are currently IDLE
                if (Math.abs(pitchDeg) < 25.0 && motionState == MotionState.IDLE) {
                    // The bot just tilted the phone to scan! Trigger the GPS anomaly.
                    triggerDocumentScanSequence()
                } 
                // Reset state if lifted back up to browsing angle (> 60 degrees)
                else if (Math.abs(pitchDeg) > 60.0 && motionState == MotionState.IDLE) {
                    // Ready for the next scan cycle
                }
            }
            Sensor.TYPE_PRESSURE -> {
                // Barometric formula approximation: ~8.3 meters of altitude change per 1 hPa/mbar
                val currentPressure = event.values[0]
                if (initialPressure == null) {
                    initialPressure = currentPressure
                } else {
                    relativeAltitudeOffset = ((initialPressure!! - currentPressure) * 8.3).toDouble()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Ignored for simulation purposes
    }

    // --- Utility Math Functions ---
    private fun randomInt(min: Int, max: Int): Int = min + random.nextInt(max - min + 1)
    private fun randomFloat(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)
}
