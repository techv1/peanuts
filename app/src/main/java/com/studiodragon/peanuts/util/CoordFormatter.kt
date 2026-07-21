package com.studiodragon.peanuts.util

import java.util.Locale
import kotlin.math.abs

object CoordFormatter {

    fun formatDecimal(lat: Double, lon: Double): String {
        return String.format(Locale.US, "%.6f, %.6f", lat, lon)
    }

    fun toDms(lat: Double, lon: Double): String {
        val latDms = convertDecimalToDms(lat, isLatitude = true)
        val lonDms = convertDecimalToDms(lon, isLatitude = false)
        return "$latDms  $lonDms"
    }

    private fun convertDecimalToDms(decimal: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (decimal >= 0) "N" else "S"
        } else {
            if (decimal >= 0) "E" else "W"
        }
        val absVal = abs(decimal)
        val degrees = absVal.toInt()
        val remainderMinutes = (absVal - degrees) * 60
        val minutes = remainderMinutes.toInt()
        val seconds = (remainderMinutes - minutes) * 60

        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, direction)
    }

    fun parseCoords(input: String): Pair<Double, Double>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Try standard "lat, lon"
        val parts = trimmed.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                return Pair(lat, lon)
            }
        }

        // Try space-separated "lat lon"
        val spaceParts = trimmed.split(Regex("\\s+"))
        if (spaceParts.size == 2) {
            val lat = spaceParts[0].trim().toDoubleOrNull()
            val lon = spaceParts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                return Pair(lat, lon)
            }
        }

        return null
    }
}
