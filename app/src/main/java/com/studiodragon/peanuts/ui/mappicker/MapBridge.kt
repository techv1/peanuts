package com.studiodragon.peanuts.ui.mappicker

import android.webkit.JavascriptInterface

class MapBridge(private val onLocationSelectedListener: (Double, Double) -> Unit) {

    @JavascriptInterface
    fun onLocationSelected(lat: Double, lon: Double) {
        onLocationSelectedListener(lat, lon)
    }
}
