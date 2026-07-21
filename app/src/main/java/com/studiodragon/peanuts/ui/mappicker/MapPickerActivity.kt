package com.studiodragon.peanuts.ui.mappicker

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.studiodragon.peanuts.databinding.ActivityMapPickerBinding
import java.util.Locale

class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding

    companion object {
        const val EXTRA_INITIAL_LAT = "extra_initial_lat"
        const val EXTRA_INITIAL_LON = "extra_initial_lon"
        const val RESULT_LAT = "result_lat"
        const val RESULT_LON = "result_lon"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialLat = intent.getDoubleExtra(EXTRA_INITIAL_LAT, 37.7749)
        val initialLon = intent.getDoubleExtra(EXTRA_INITIAL_LON, -122.4194)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            addJavascriptInterface(MapBridge { lat, lon ->
                runOnUiThread {
                    val data = Intent().apply {
                        putExtra(RESULT_LAT, lat)
                        putExtra(RESULT_LON, lon)
                    }
                    setResult(RESULT_OK, data)
                    finish()
                }
            }, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val jsCall = String.format(Locale.US, "initMap(%.6f, %.6f);", initialLat, initialLon)
                    evaluateJavascript(jsCall, null)
                }
            }

            loadUrl("file:///android_asset/map_picker.html")
        }
    }
}
