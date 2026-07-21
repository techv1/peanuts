package com.studiodragon.peanuts.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SimLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val name: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
