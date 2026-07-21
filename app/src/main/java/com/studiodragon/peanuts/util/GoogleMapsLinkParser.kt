package com.studiodragon.peanuts.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GoogleMapsLinkParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val AT_PATTERN = Pattern.compile("@(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")
    private val PLACE_PATTERN = Pattern.compile("/place/(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")
    private val QUERY_PATTERN = Pattern.compile("[?&](?:q|ll)=(-?\\d{1,2}\\.\\d+),(-?\\d{1,3}\\.\\d+)")
    private val PROTO_D_PATTERN = Pattern.compile("!3d(-?\\d{1,2}\\.\\d+)!4d(-?\\d{1,3}\\.\\d+)")
    private val GENERIC_COORD_PATTERN = Pattern.compile("(-?\\d{1,2}\\.\\d{3,})\\s*,\\s*(-?\\d{1,3}\\.\\d{3,})")
    private val DIRECT_LAT_LON_PATTERN = Pattern.compile("^\\s*(-?\\d{1,2}(?:\\.\\d+)?)[\\s,]+(-?\\d{1,3}(?:\\.\\d+)?)\\s*$")
    private val DMS_PATTERN = Pattern.compile("(\\d{1,2})°(\\d{1,2})'(\\d{1,2}(?:\\.\\d+)?)\"([NS])\\s*(\\d{1,3})°(\\d{1,2})'(\\d{1,2}(?:\\.\\d+)?)\"([EW])", Pattern.CASE_INSENSITIVE)
    private val URL_REGEX = "(https?://[a-zA-Z0-9./?=_%@!,-]+)".toRegex()

    fun extractUrlFromText(text: String): String? {
        if (text.isBlank()) return null
        val match = URL_REGEX.find(text)
        return match?.value ?: if (text.startsWith("http://") || text.startsWith("https://")) text else null
    }

    fun extractCoordinatesFromUrlOrText(urlOrText: String): Pair<Double, Double>? {
        if (urlOrText.isBlank()) return null

        // 0. Direct typed Lat, Lon (e.g., "27.9937, 73.3200" or "27.9937 73.3200")
        val directMatcher = DIRECT_LAT_LON_PATTERN.matcher(urlOrText.trim())
        if (directMatcher.find()) {
            val lat = directMatcher.group(1)?.toDoubleOrNull()
            val lon = directMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        // 1. DMS Format (e.g., 27°59'37.3"N 73°19'12.3"E)
        val dmsMatcher = DMS_PATTERN.matcher(urlOrText)
        if (dmsMatcher.find()) {
            val latDeg = dmsMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val latMin = dmsMatcher.group(2)?.toDoubleOrNull() ?: 0.0
            val latSec = dmsMatcher.group(3)?.toDoubleOrNull() ?: 0.0
            val latDir = dmsMatcher.group(4) ?: "N"

            val lonDeg = dmsMatcher.group(5)?.toDoubleOrNull() ?: 0.0
            val lonMin = dmsMatcher.group(6)?.toDoubleOrNull() ?: 0.0
            val lonSec = dmsMatcher.group(7)?.toDoubleOrNull() ?: 0.0
            val lonDir = dmsMatcher.group(8) ?: "E"

            var lat = latDeg + (latMin / 60.0) + (latSec / 3600.0)
            if (latDir.equalsIgnoreCase("S")) lat = -lat

            var lon = lonDeg + (lonMin / 60.0) + (lonSec / 3600.0)
            if (lonDir.equalsIgnoreCase("W")) lon = -lon

            if (isValidCoords(lat, lon)) return Pair(lat, lon)
        }

        // 2. Check proto 3d/4d parameters (!3d37.7749!4d-122.4194)
        val protoMatcher = PROTO_D_PATTERN.matcher(urlOrText)
        if (protoMatcher.find()) {
            val lat = protoMatcher.group(1)?.toDoubleOrNull()
            val lon = protoMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        // 3. Check @lat,lon
        val atMatcher = AT_PATTERN.matcher(urlOrText)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lon = atMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        // 4. Check /place/lat,lon
        val placeMatcher = PLACE_PATTERN.matcher(urlOrText)
        if (placeMatcher.find()) {
            val lat = placeMatcher.group(1)?.toDoubleOrNull()
            val lon = placeMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        // 5. Check query params (q=lat,lon or ll=lat,lon)
        val qMatcher = QUERY_PATTERN.matcher(urlOrText)
        if (qMatcher.find()) {
            val lat = qMatcher.group(1)?.toDoubleOrNull()
            val lon = qMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        // 6. Check generic decimal coordinates in text
        val genMatcher = GENERIC_COORD_PATTERN.matcher(urlOrText)
        if (genMatcher.find()) {
            val lat = genMatcher.group(1)?.toDoubleOrNull()
            val lon = genMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoords(lat, lon)) return Pair(lat!!, lon!!)
        }

        return null
    }

    suspend fun resolveCoordinatesWithoutApi(shortUrlOrText: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val rawInput = shortUrlOrText.trim()
        if (rawInput.isBlank()) return@withContext null

        // 1. Direct extraction before network request
        val directCoords = extractCoordinatesFromUrlOrText(rawInput)
        if (directCoords != null) return@withContext directCoords

        // 2. Extract URL from text (e.g. "Shared location: https://maps.app.goo.gl/...")
        val targetUrl = extractUrlFromText(rawInput) ?: rawInput
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            return@withContext null
        }

        // 3. Use OkHttp to automatically follow all redirects
        return@withContext try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                extractCoordinatesFromUrlOrText(finalUrl) ?: extractCoordinatesFromUrlOrText(response.body?.string() ?: "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            extractCoordinatesFromUrlOrText(targetUrl)
        }
    }

    private fun isValidCoords(lat: Double?, lon: Double?): Boolean {
        return lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun String.equalsIgnoreCase(other: String): Boolean = this.equals(other, ignoreCase = true)
}
