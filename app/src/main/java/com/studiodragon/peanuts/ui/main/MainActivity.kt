package com.studiodragon.peanuts.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.studiodragon.peanuts.R
import com.studiodragon.peanuts.core.MockLocationService
import com.studiodragon.peanuts.data.model.SearchResult
import com.studiodragon.peanuts.data.model.SimLocation
import com.studiodragon.peanuts.databinding.ActivityMainBinding
import com.studiodragon.peanuts.ui.mappicker.MapPickerActivity
import com.studiodragon.peanuts.ui.settings.SettingsActivity
import com.studiodragon.peanuts.util.CoordFormatter
import com.studiodragon.peanuts.util.GoogleMapsLinkParser
import com.studiodragon.peanuts.util.toast
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val mapPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val lat = result.data!!.getDoubleExtra(MapPickerActivity.RESULT_LAT, 0.0)
            val lon = result.data!!.getDoubleExtra(MapPickerActivity.RESULT_LON, 0.0)
            binding.etLat.setText(String.format("%.6f", lat))
            binding.etLon.setText(String.format("%.6f", lon))
            viewModel.fetchAltitude(lat, lon)
            toast("Selected location: %.5f, %.5f".format(lat, lon))
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineLocationGranted) {
            toast("Fine Location permission is required for simulation.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
        checkFirstTimeDevInstructions()
        setupToolbar()
        setupListeners()
        observeViewModel()
        observeServiceState()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        var rawInput: String? = null

        // 1. Handled via Share Sheet (When user clicks "Share" in Google Maps app)
        if (Intent.ACTION_SEND == action) {
            rawInput = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (rawInput.isNullOrBlank()) {
                rawInput = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            }
            if (rawInput.isNullOrBlank() && intent.clipData != null && intent.clipData!!.itemCount > 0) {
                rawInput = intent.clipData!!.getItemAt(0).text?.toString()
            }
        } 
        // 2. Handled via direct link click (maps.app.goo.gl or maps.google.com)
        else if (Intent.ACTION_VIEW == action) {
            rawInput = intent.data?.toString()
        }

        if (!rawInput.isNullOrBlank()) {
            toast("Parsing Google Maps location link...")
            binding.etSearch.setText(rawInput)
            viewModel.resolveGoogleMapsLinkAndSet(rawInput)
        }
    }

    private fun checkFirstTimeDevInstructions() {
        val prefs = getSharedPreferences("peanuts_app_prefs", Context.MODE_PRIVATE)
        val hasSeenInstructions = prefs.getBoolean("has_seen_dev_instructions", false)

        if (!hasSeenInstructions) {
            showDeveloperOptionsInstructionsDialog()
        }
    }

    private fun showDeveloperOptionsInstructionsDialog() {
        val message = """
            Welcome to peanuts GPS Simulator! 🥜
            
            To enable GPS location spoofing on your Android phone, you must select peanuts as your Mock Location App:

            1️⃣ Open System Settings ➔ About Phone.
            2️⃣ Tap 'Build Number' 7 times to enable Developer Options.
            3️⃣ Open Developer Options ➔ Select mock location app.
            4️⃣ Choose peanuts.

            Tap below to open Developer Options directly!
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("⚙️ Developer Options Setup Required")
            .setMessage(message)
            .setPositiveButton("⚙️ Open Developer Options") { _, _ ->
                val prefs = getSharedPreferences("peanuts_app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_seen_dev_instructions", true).apply()
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (ex: Exception) {
                        toast("Could not open Settings directly.")
                    }
                }
            }
            .setNegativeButton("Got It") { dialog, _ ->
                val prefs = getSharedPreferences("peanuts_app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_seen_dev_instructions", true).apply()
                dialog.dismiss()
            }
            .show()
    }

    private fun checkPermissions() {
        val permsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupListeners() {
        binding.inputSearchLayout.setEndIconOnClickListener {
            val query = binding.etSearch.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                viewModel.searchLocationOrLink(query)
            } else {
                toast("Please enter a location, coordinates, or Google Maps link")
            }
        }

        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val query = binding.etSearch.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                viewModel.searchLocationOrLink(query)
                true
            } else {
                false
            }
        }

        binding.btnFetchAlt.setOnClickListener {
            val lat = binding.etLat.text?.toString()?.toDoubleOrNull()
            val lon = binding.etLon.text?.toString()?.toDoubleOrNull()
            if (lat != null && lon != null) {
                toast("Fetching altitude...")
                viewModel.fetchAltitude(lat, lon)
            } else {
                toast("Enter valid Lat and Lon first")
            }
        }

        binding.btnMapPicker.setOnClickListener {
            val currentLat = binding.etLat.text?.toString()?.toDoubleOrNull() ?: 37.7749
            val currentLon = binding.etLon.text?.toString()?.toDoubleOrNull() ?: -122.4194
            val intent = Intent(this, MapPickerActivity::class.java).apply {
                putExtra(MapPickerActivity.EXTRA_INITIAL_LAT, currentLat)
                putExtra(MapPickerActivity.EXTRA_INITIAL_LON, currentLon)
            }
            mapPickerLauncher.launch(intent)
        }

        binding.btnStart.setOnClickListener {
            val lat = binding.etLat.text?.toString()?.toDoubleOrNull()
            val lon = binding.etLon.text?.toString()?.toDoubleOrNull()
            val alt = binding.etAlt.text?.toString()?.toDoubleOrNull() ?: 0.0

            if (lat == null || lon == null) {
                toast("Please enter valid Latitude and Longitude")
                return@setOnClickListener
            }

            viewModel.saveRecentLocation(lat, lon, alt)
            MockLocationService.startService(this, lat, lon, alt)
            toast("Starting GPS simulation...")
        }

        binding.btnStop.setOnClickListener {
            MockLocationService.stopService(this)
            toast("Stopping simulation...")
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.recentLocations.collect { locations ->
                        updateRecentChips(locations)
                    }
                }
                launch {
                    viewModel.resolvedLocationEvent.collect { pair ->
                        val (lat, lon) = pair
                        binding.etLat.setText(String.format("%.6f", lat))
                        binding.etLon.setText(String.format("%.6f", lon))
                        viewModel.fetchAltitude(lat, lon)
                        toast("Resolved Target: %.6f, %.6f".format(lat, lon))
                    }
                }
                launch {
                    viewModel.searchResults.collect { results ->
                        if (results.isNotEmpty() && results.size > 1) {
                            showSearchResultsDialog(results)
                        } else if (results.size == 1) {
                            val selected = results[0]
                            val lat = selected.latitude
                            val lon = selected.longitude
                            binding.etLat.setText(String.format("%.6f", lat))
                            binding.etLon.setText(String.format("%.6f", lon))
                            viewModel.fetchAltitude(lat, lon)
                            toast("Selected: ${selected.displayName}")
                        }
                    }
                }
                launch {
                    viewModel.fetchedAltitude.collect { alt ->
                        if (alt != null) {
                            binding.etAlt.setText(String.format("%.1f", alt))
                            toast("Altitude updated: %.1fm".format(alt))
                        }
                    }
                }
                launch {
                    viewModel.lastLocation.collect { loc ->
                        if (loc != null && binding.etLat.text.isNullOrEmpty()) {
                            binding.etLat.setText(String.format("%.6f", loc.latitude))
                            binding.etLon.setText(String.format("%.6f", loc.longitude))
                            binding.etAlt.setText(String.format("%.1f", loc.altitude))
                        }
                    }
                }
            }
        }
    }

    private fun observeServiceState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    MockLocationService.isSimulating.collect { active ->
                        if (active) {
                            binding.statusIndicator.setBackgroundResource(R.drawable.shape_circle_green)
                            binding.tvStatus.text = "Status: Simulating"
                            binding.btnStart.isEnabled = false
                            binding.btnStop.isEnabled = true
                        } else {
                            binding.statusIndicator.setBackgroundResource(R.drawable.shape_circle_grey)
                            binding.tvStatus.text = "Status: Stopped"
                            binding.btnStart.isEnabled = true
                            binding.btnStop.isEnabled = false
                        }
                    }
                }
                launch {
                    MockLocationService.currentSimLocation.collect { triple ->
                        if (triple != null) {
                            val (lat, lon, alt) = triple
                            binding.tvLiveCoords.text = "Live Lat: %.6f | Lon: %.6f | Alt: %.1fm".format(lat, lon, alt)
                        } else {
                            binding.tvLiveCoords.text = "Lat: --, Lon: --"
                        }
                    }
                }
            }
        }
    }

    private fun updateRecentChips(locations: List<SimLocation>) {
        binding.chipGroupRecent.removeAllViews()
        for (loc in locations) {
            val chip = Chip(this).apply {
                text = if (loc.name.isNotEmpty()) loc.name else CoordFormatter.formatDecimal(loc.latitude, loc.longitude)
                isClickable = true
                setOnClickListener {
                    binding.etLat.setText(String.format("%.6f", loc.latitude))
                    binding.etLon.setText(String.format("%.6f", loc.longitude))
                    binding.etAlt.setText(String.format("%.1f", loc.altitude))
                }
            }
            binding.chipGroupRecent.addView(chip)
        }
    }

    private fun showSearchResultsDialog(results: List<SearchResult>) {
        val items = results.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Location")
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items)) { _, which ->
                val selected = results[which]
                val lat = selected.latitude
                val lon = selected.longitude
                binding.etLat.setText(String.format("%.6f", lat))
                binding.etLon.setText(String.format("%.6f", lon))
                toast("Selected: ${selected.displayName}")
                viewModel.fetchAltitude(lat, lon)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
