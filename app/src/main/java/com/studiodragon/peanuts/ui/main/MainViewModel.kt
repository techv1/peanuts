package com.studiodragon.peanuts.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studiodragon.peanuts.data.AltitudeRepository
import com.studiodragon.peanuts.data.NominatimRepository
import com.studiodragon.peanuts.data.RecentLocationsStore
import com.studiodragon.peanuts.data.model.SearchResult
import com.studiodragon.peanuts.data.model.SimLocation
import com.studiodragon.peanuts.util.GoogleMapsLinkParser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val recentLocationsStore = RecentLocationsStore(application)
    private val nominatimRepository = NominatimRepository()
    private val altitudeRepository = AltitudeRepository()

    val recentLocations: StateFlow<List<SimLocation>> = recentLocationsStore.recentLocationsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val lastLocation: StateFlow<SimLocation?> = recentLocationsStore.lastLocationFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _resolvedLocationEvent = MutableSharedFlow<Pair<Double, Double>>()
    val resolvedLocationEvent: SharedFlow<Pair<Double, Double>> = _resolvedLocationEvent.asSharedFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _fetchedAltitude = MutableStateFlow<Double?>(null)
    val fetchedAltitude: StateFlow<Double?> = _fetchedAltitude.asStateFlow()

    fun searchLocationOrLink(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // 1. Try Resolving as Google Maps Link or Direct Lat,Lon
                val coordsFromLink = GoogleMapsLinkParser.resolveCoordinatesWithoutApi(query)
                if (coordsFromLink != null) {
                    val (lat, lon) = coordsFromLink
                    _resolvedLocationEvent.emit(Pair(lat, lon))
                    _searchResults.value = listOf(
                        SearchResult(
                            displayName = "📍 Target Coordinates (Lat: %.6f, Lon: %.6f)".format(lat, lon),
                            lat = lat.toString(),
                            lon = lon.toString()
                        )
                    )
                    return@launch
                }

                // 2. Search OpenStreetMap Nominatim
                val nomResults = nominatimRepository.search(query)
                _searchResults.value = nomResults
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun resolveGoogleMapsLinkAndSet(urlOrText: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val coords = GoogleMapsLinkParser.resolveCoordinatesWithoutApi(urlOrText)
                if (coords != null) {
                    val (lat, lon) = coords
                    _resolvedLocationEvent.emit(Pair(lat, lon))
                    _searchResults.value = listOf(
                        SearchResult(
                            displayName = "📍 Google Maps Shared Location (Lat: %.6f, Lon: %.6f)".format(lat, lon),
                            lat = lat.toString(),
                            lon = lon.toString()
                        )
                    )
                } else {
                    searchLocationOrLink(urlOrText)
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun fetchAltitude(lat: Double, lon: Double) {
        viewModelScope.launch {
            val alt = altitudeRepository.getAltitude(lat, lon)
            _fetchedAltitude.value = alt
        }
    }

    fun saveRecentLocation(lat: Double, lon: Double, alt: Double = 0.0, name: String = "") {
        viewModelScope.launch {
            val loc = SimLocation(latitude = lat, longitude = lon, altitude = alt, name = name)
            recentLocationsStore.saveLocation(loc)
        }
    }
}
