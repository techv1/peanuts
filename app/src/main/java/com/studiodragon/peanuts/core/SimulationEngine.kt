package com.studiodragon.peanuts.core

// TODO: Jitter, noise, altitude, bearing math
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import kotlinx.coroutines.*
import java.util.Random
import kotlin.math.sin

/**
 * Configuration parameters for the Simulation Engine.
 * All magic numbers are extracted here for easy remote-config or tuning.
 */
data class SimConfig(
    // Core Timing & Jitter
    val updateIntervalMs: Long = 1000,
    val updateJitterMs: Long = 80,               // ±80ms interval randomization
    val jitterAmplitudeDeg: Double = 0.000008,   // ±0.9m default baseline jitter

    // Occasional large signal gaps (Dropouts)
    val dropoutProbability: Float = 0.015f,      // ~1.5% chance per tick to cause a gap
    val dropoutDelayMinMs: Long = 1500,
    val dropoutDelayMaxMs: Long = 2500,

    // Environmental Interference (Burst Simulation)
    val burstIntervalMinTicks: Int = 15,
    val burstIntervalMaxTicks: Int = 30,
    val burstDurationTicks: Int = 3,
    val burstJitterMultiplier: Double = 4.0,     // 4x normal jitter during burst

    // Accuracy Metrics
    val accuracyMin: Float = 3.5f,
    val accuracyMax: Float = 9.5f,
    val verticalAccBaseMultiplier: Float = 1.5f, // Vertical is typically 1.5x worse

    // Kinematics (Speed & Bearing)
    val speedMean: Double = 0.08,
    val speedStdDev: Double = 0.1,
    val speedMin: Float = 0.0f,
    val speedMax: Float = 0.67f,
    val speedZeroProbability: Float = 0.025f,
    val speedThresholdForBearing: Float = 0.05f, // Speed below which GPS drops bearing
    val bearingMaxDriftPerTick: Double = 5.0,    // ±5° random walk per tick

    // Altitude
    val altitudeNoiseM: Double = 1.5,            // ±1.5m per tick Gaussian
    val altitudeDriftAmplitude: Double = 4.0,    // ±4m slow sine wave
    val altitudeDriftFrequency: Double = 0.008,  // Sine wave frequency multiplier

    // Lifecycle
    val stopMode: StopMode = StopMode.GRADUAL,
    val gradualFadeDurationMs: Long = 12000
)

enum class StopMode { INSTANT, GRADUAL, FREEZE }

/**
 * The core GPS Simulation Engine.
 */
class SimulationEngine(
    private val locationManager: LocationManager,
    private val baseLat: Double,
    private val baseLon: Double,
    private val baseAlt: Double,
    private val config: SimConfig = SimConfig()
) {
    private var simulationJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val random = Random()

    // State Variables
    private var currentLat = baseLat
    private var currentLon = baseLon
    private var currentAccuracy = (config.accuracyMin + config.accuracyMax) / 2f
    private var currentBearing = random.nextDouble() * 360.0
    private var tickCount = 0L
    
    // Burst State
    private var nextBurstTarget = randomInt(config.burstIntervalMinTicks, config.burstIntervalMaxTicks)
    private var ticksSinceLastBurst = 0

    fun start() {
        if (simulationJob?.isActive == true) return

        simulationJob = coroutineScope.launch {
            try {
                while (isActive) {
                    processTick()
                }
            } catch (e: CancellationException) {
                // Normal cancellation, handled cleanly
            }
        }
    }

    fun stop() {
        when (config.stopMode) {
            StopMode.INSTANT, StopMode.FREEZE -> {
                simulationJob?.cancel()
                simulationJob = null
            }
            StopMode.GRADUAL -> {
                coroutineScope.launch {
                    fadeAndStop()
                }
            }
        }
    }

    private suspend fun processTick(fadeMultiplier: Double = 1.0) {
        // 1. Timing & Dropouts
        if (random.nextFloat() < config.dropoutProbability && fadeMultiplier == 1.0) {
            val dropDelay = randomLong(config.dropoutDelayMinMs, config.dropoutDelayMaxMs)
            delay(dropDelay)
        } else {
            val normalDelay = config.updateIntervalMs + randomLong(-config.updateJitterMs, config.updateJitterMs)
            delay(normalDelay)
        }

        // 2. Burst State Calculation
        val isBurst = (ticksSinceLastBurst < config.burstDurationTicks)
        ticksSinceLastBurst++

        if (ticksSinceLastBurst >= nextBurstTarget) {
            ticksSinceLastBurst = 0
            nextBurstTarget = randomInt(config.burstIntervalMinTicks, config.burstIntervalMaxTicks)
        }

        // 3. Position Jitter (Damped noise)
        val jitterBase = if (isBurst) config.jitterAmplitudeDeg * config.burstJitterMultiplier else config.jitterAmplitudeDeg
        val effectiveJitter = jitterBase * fadeMultiplier
        
        currentLat += random.nextGaussian() * effectiveJitter
        currentLon += random.nextGaussian() * effectiveJitter

        // 4. Accuracy & Metadata (Coupled to Burst State)
        val satellites: Int
        val hdop: Float
        val accuracyDrift: Float

        if (isBurst) {
            satellites = randomInt(4, 7)       // Degraded satellites
            hdop = randomFloat(1.5f, 3.0f)     // High HDOP (bad geometry)
            accuracyDrift = (random.nextFloat() - 0.5f) * 1.2f
        } else {
            satellites = randomInt(8, 12)      // Clear sky
            hdop = randomFloat(0.8f, 1.4f)     // Good geometry
            accuracyDrift = (random.nextFloat() - 0.5f) * 0.8f
        }

        currentAccuracy = (currentAccuracy + accuracyDrift).coerceIn(config.accuracyMin, config.accuracyMax)
        
        // Vertical accuracy breathes dynamically (1.5x - 2.0x worse than horizontal)
        val verticalAccuracy = currentAccuracy * (config.verticalAccBaseMultiplier + random.nextFloat() * 0.5f)

        // 5. Kinematics (Gaussian Speed & Nullable Bearing)
        var speed = 0.0f
        if (random.nextFloat() >= config.speedZeroProbability) {
            // Pull from normal distribution, coerce to bounds, scale down if fading
            val rawSpeed = random.nextGaussian() * config.speedStdDev + config.speedMean
            speed = rawSpeed.toFloat().coerceIn(config.speedMin, config.speedMax) * fadeMultiplier.toFloat()
        }

        val hasBearing = speed >= config.speedThresholdForBearing
        if (hasBearing) {
            // Random walk for bearing
            val drift = (random.nextDouble() * 2 - 1) * config.bearingMaxDriftPerTick
            currentBearing = (currentBearing + drift).rem(360.0)
            if (currentBearing < 0) currentBearing += 360.0
        }

        // 6. Altitude (Sine drift + Gaussian noise)
        val altitudeDrift = sin(tickCount * config.altitudeDriftFrequency) * config.altitudeDriftAmplitude
        val currentAlt = baseAlt + altitudeDrift + (random.nextGaussian() * config.altitudeNoiseM)

        // 7. Payload Assembly
        val location = Location("gps").apply {
            latitude = currentLat
            longitude = currentLon
            altitude = currentAlt
            accuracy = currentAccuracy
            
            // Speed and Bearing
            this.speed = speed
            if (hasBearing) {
                bearing = currentBearing.toFloat()
            } // If false, we leave it unset. Android API treats it as not having bearing.

            // API 26+ Vertical Accuracy
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = verticalAccuracy
            }

            // CRITICAL: Anti-Mock Detection Fields
            time = System.currentTimeMillis()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            
            extras = Bundle().apply {
                putInt("satellites", satellites)
                putFloat("hdop", hdop)
            }
        }

        // 8. Dispatch to Test Provider
        try {
            locationManager.setTestProviderLocation("gps", location)
        } catch (e: Exception) {
            // Failsafe if the provider hasn't been properly added via addTestProvider
            e.printStackTrace()
        }
        
        tickCount++
    }

    /**
     * Handles the GRADUAL stop mode by ramping down movement/jitter multipliers
     * before permanently killing the loop.
     */
    private suspend fun fadeAndStop() {
        val oldJob = simulationJob
        simulationJob = null // Prevent main loop from continuing
        oldJob?.cancelAndJoin()

        val steps = (config.gradualFadeDurationMs / config.updateIntervalMs).toInt()
        
        for (i in steps downTo 1) {
            val fadeMultiplier = i.toDouble() / steps.toDouble()
            processTick(fadeMultiplier = fadeMultiplier)
        }
    }

    // --- Utility Math Functions ---
    private fun randomLong(min: Long, max: Long): Long = min + (random.nextDouble() * (max - min)).toLong()
    private fun randomInt(min: Int, max: Int): Int = min + random.nextInt(max - min + 1)
    private fun randomFloat(min: Float, max: Float): Float = min + random.nextFloat() * (max - min)
}
