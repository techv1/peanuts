package com.studiodragon.peanuts.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SearchResult(
    @Json(name = "place_id") val placeId: Long = 0L,
    @Json(name = "display_name") val displayName: String = "",
    val lat: String = "0.0",
    val lon: String = "0.0"
) {
    val latitude: Double get() = lat.toDoubleOrNull() ?: 0.0
    val longitude: Double get() = lon.toDoubleOrNull() ?: 0.0
}
