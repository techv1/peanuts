package com.studiodragon.peanuts.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenTopoResult(
    val elevation: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenTopoResponse(
    val results: List<OpenTopoResult> = emptyList(),
    val status: String = ""
)

class AltitudeRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(OpenTopoResponse::class.java)
    private val cache = mutableMapOf<String, Double>()

    suspend fun getAltitude(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        val cacheKey = "%.4f,%.4f".format(lat, lon)
        cache[cacheKey]?.let { return@withContext it }

        // 1. Primary Elevation API: OpenTopoData (ETOPO1 Global DEM)
        val openTopoUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.opentopodata.org")
            .addPathSegments("v1/etopo1")
            .addQueryParameter("locations", "$lat,$lon")
            .build()

        val request = Request.Builder()
            .url(openTopoUrl)
            .header("User-Agent", "PeanutsApp/1.0 (com.studiodragon.peanuts)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val resultObj = adapter.fromJson(bodyString)
                    val elevation = resultObj?.results?.firstOrNull()?.elevation
                    if (elevation != null) {
                        cache[cacheKey] = elevation
                        return@withContext elevation
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Secondary Fallback Elevation API: Open-Elevation
        val openElevationUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.open-elevation.com")
            .addPathSegments("api/v1/lookup")
            .addQueryParameter("locations", "$lat,$lon")
            .build()

        val request2 = Request.Builder()
            .url(openElevationUrl)
            .header("User-Agent", "PeanutsApp/1.0 (com.studiodragon.peanuts)")
            .build()

        try {
            client.newCall(request2).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val resultObj = adapter.fromJson(bodyString)
                    val elevation = resultObj?.results?.firstOrNull()?.elevation
                    if (elevation != null) {
                        cache[cacheKey] = elevation
                        return@withContext elevation
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }
}
