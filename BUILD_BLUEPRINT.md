# 🏗️ BUILD BLUEPRINT — Fresh Rebuild Questionnaire & Architecture Doc

> **Purpose**: Answer every question in Section 1 before writing a single line of code.
> This doc, once filled, becomes the single source of truth for the AI assistant building the app.
> Do NOT skip questions — each one affects architecture decisions.

---

## SECTION 1 — QUESTIONNAIRE (Fill Before Building)

### 🏷️ 1. App Identity

| # | Question | Your Answer |
|---|----------|-------------|
| 1.1 | What is the app name? | _______peanuts______ |
| 1.2 | What is the package name? (e.g. `com.yourname.appname`) | _____com.studiodragon.peanuts________ |
| 1.3 | What label shows in Android Developer Options mock location picker? | ______peanuts_______ |
| 1.4 | App icon style preference? (minimal/colorful/dark/instrument-themed) | _______colorful______ |
| 1.5 | Short description for Play Store / about screen (1 line) | __best 3d game___________ |

> 💡 **Suggested names** that sound like utility/developer tools
> - **PinPoint** — sounds like a surveying tool
> - **CoordLab** — developer lab feel
> - **TerraMark** — geographic/fieldwork vibe
> -  — professional GPS field tool
> - **SitePulse** — construction/survey tool
> - **GeoFrame** — neutral, technical
> - **NavTrace** — navigation tracing
> - **ArcPin** — mapping professional feel

---

### 🎨 2. UI & Design

| # | Question | Your Answer |
|---|----------|-------------|
| 2.1 | Color theme preference? (Dark only / Light only / System auto) | _____light________ |
| 2.2 | Accent color? (e.g. blue, teal, orange, green) | ____as best practise suggest_________ |
| 2.3 | UI density? (compact for small screens / comfortable / spacious) | ______comfortable_______ |
| 2.4 | Map picker style? (full-screen takeover / bottom sheet half-screen) | _____half plus full screen icon________ |
| 2.5 | Coordinate display: decimal degrees only, or also DMS format? | ______decimal _______ |
| 2.6 | Should the main screen show a mini live map preview while simulating? | ___yep__________ |
| 2.7 | Font preference? (system default / rounded / monospace for coords) | ___rounded__________ |

---

### ⚙️ 3. Core Simulation Settings

| # | Question | Your Answer | 
|---|----------|-------------| most of these settings are already told later  use that or simulationengine.kt preconfigured by me
| 3.1 | Default jitter level when app first installs? (Low/Medium/High) | ____variable_________ |
| 3.2 | Default update interval? (500ms / 1s / 2s) | ______variable_______ |
| 3.3 | Should altitude simulation be ON by default? | ____yes_________ |
| 3.4 | Default stop behavior? (Instant snap / Gradual fade / Freeze) | ______freeze_______ |
| 3.5 | Should bearing auto-rotate by default? | ____no_________ |
| 3.6 | Should speed occasionally drop to 0.0 by default? | ____explain_________ |
| 3.7 | Max number of saved/recent locations to remember? (5 / 10 / 20) | ___5__________ |

// Timing
delay(1000 + Random.nextLong(-80, 120))
// every 40-70s: occasional 1500-2500ms gap

// Position — burst pattern
val burstActive = (tickCount % burstInterval < 3)  // burstInterval = random 15-30s
val jitterAmp = if (burstActive) 0.000035 else 0.000008
lat += dampedNoise(jitterAmp)
lon += dampedNoise(jitterAmp)

// Accuracy — slow drift, correlated with burst
currentAccuracy += (Random.nextDouble() - 0.5) * (if (burstActive) 1.2 else 0.8)
currentAccuracy = currentAccuracy.coerceIn(3.5, 9.5)
verticalAccuracy = currentAccuracy * (1.5 + Random.nextDouble() * 0.5)

// Altitude
altitude = baseAltitude + sin(tickCount * 0.008) * 4.0 + gaussianNoise(1.5)

// Speed
speed = if (rare(0.025)) 0.0 else normalDist(mean=0.08, sd=0.1).coerceIn(0.0, 0.67)

// Bearing
bearing = if (speed < 0.05) null else (previousBearing + randomWalk(±5°)).mod(360.0)

// Provider
provider = "gps"

---

### 🗺️ 4. Location Input

| # | Question | Your Answer |
|---|----------|-------------|
| 4.1 | Should Nominatim (OpenStreetMap) search be enabled by default? | ____yes_________ |
| 4.2 | Should MapmyIndia search be included? (needs free API key) | ______yes_______ |
| 4.3 | Should Google Maps share link paste be on main screen or hidden in menu? | ______main screen_______ |
| 4.4 | Should the map picker open in a new Activity or a bottom sheet? | ____explain_________ |
| 4.5 | Should map picker remember last viewed region between sessions? | ______yep_______ |

---

### 🔔 5. Background & Notification

| # | Question | Your Answer |
|---|----------|-------------|
| 5.1 | Should simulation run as a Foreground Service (keeps running when app minimized)? | _____yep________ |
| 5.2 | Notification style: compact (just coords) or expanded (coords + controls)? | ______real simulation like speed,cords,jitter etc._______ |
| 5.3 | Should notification show a Stop button? | ______no_______ |
| 5.4 | Should notification show a Freeze toggle? | _____no________ |
| 5.5 | Should app survive device screen-off without battery optimization issues? | _____yes________ |

---

### 🧪  For Testing Only

| # | Question | Your Answer |
|---|----------|-------------|
| 6.1 | Do you need to bypass apps that check `isFromMockProvider()`? | _______yep______ |
| 6.2 | Do you need to bypass apps that check `Location.extras` bundle? | ______yep_______ |
| 6.3 | Should provider name be randomized per session? | ____maybe i dont know how does effect_________ |
| 6.4 | Should the app label in Developer Options be disguised? | ___explain__________ |
| 6.5 | Target apps for testing (helps tune bypass level): | ______real games that chek live_______ |

---

### 🛠️ 7. Technical Preferences

| # | Question | Your Answer |
|---|----------|-------------|
| 7.1 | Architecture pattern? (MVC / MVVM / MVI — recommend MVVM) | _____mvvm________ |
| 7.2 | Min Android version to support? (recommend API 26 / Android 8.0) |  8|
| 7.3 | HTTP library? (OkHttp recommended / Retrofit / plain java.net.URL) | _____okhttp________ |
| 7.4 | JSON parsing? (Gson / Moshi / kotlinx.serialization — recommend Moshi) | _____moshi________ |
| 7.5 | Should settings be saved in SharedPreferences or DataStore? (recommend DataStore) | ____datastore_________ |
| 7.6 | Target APK size limit? (under 10MB / under 20MB / no limit) | _____10 may be________ |
| 7.7 | Should the app support tablets / large screens? | ___yes__________ |

---

## SECTION 2 — ARCHITECT'S DECISIONS (Pre-filled by AI)

> These are non-negotiable architecture choices based on best practices for 2025–2026 Android development.
> They are already decided. Do not change these without strong reason.

### 🏛️ Stack Decisions

| Layer | Choice | Reason |
|-------|--------|--------|
| Language | Kotlin 2.4.10 | Modern, null-safe, coroutine-native | latest is 2.4.10
| UI | XML Views + Material Design 3 | No Compose overhead (~6MB saved), faster build |
| Architecture | MVVM + Repository pattern | Clean separation, testable, lifecycle-safe |
| Async | Kotlin Coroutines + Flow | Built-in, no RxJava complexity |
| HTTP | OkHttp latest | Lightweight, reliable, interceptor support |use latest
| JSON | Moshi with Kotlin codegen | Faster than Gson, null-safe, no reflection |
| Settings | Jetpack DataStore (Preferences) | Replaces SharedPreferences, coroutine-native |
| DI | Manual (no Hilt/Koin) | App is small enough; Hilt adds 3MB+config complexity |
| Navigation | Manual Activity/Fragment | Simple enough — no NavGraph needed |
| Foreground Service | Yes, mandatory | Required for Android 14+ reliable background injection |

---

### 📁 Project Structure (Target)

```
app/src/main/java/com/studiodragon/peanuts
│
├── core/
│   ├── MockLocationService.kt       ← Foreground service, actual GPS injection
│   ├── LocationProvider.kt          ← Wraps LocationManager, test provider logic
│   └── SimulationEngine.kt          ← Jitter, noise, altitude, bearing math
│
├── data/
│   ├── NominatimRepository.kt       ← Nominatim API calls
│   ├── AltitudeRepository.kt        ← OpenTopoData API calls
│   ├── RecentLocationsStore.kt      ← DataStore persistence
│   └── model/
│       ├── SimLocation.kt           ← Core data model
│       └── SearchResult.kt
│
├── ui/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   └── layout/activity_main.xml
│   ├── mappicker/
│   │   ├── MapPickerActivity.kt
│   │   ├── MapBridge.kt             ← @JavascriptInterface
│   │   └── layout/activity_map_picker.xml
│   └── settings/
│       ├── SettingsActivity.kt
│       ├── SettingsFragment.kt      ← PreferenceFragmentCompat
│       └── xml/preferences.xml
│
├── util/
│   ├── GoogleMapsLinkParser.kt      ← Share link → lat/lon
│   ├── CoordFormatter.kt            ← Decimal ↔ DMS formatting
│   └── Extensions.kt
│
└── assets/
    └── map_picker.html              ← Leaflet.js + OSM map picker
```

---

### 📦 Gradle Setup (Modern — Kotlin DSL + Version Catalog)

Use **Kotlin DSL** (`build.gradle.kts`) + `libs.versions.toml` version catalog.
This is the modern standard as of Android Studio Iguana+.
[versions]
# Core Build
agp = "9.2.0"
kotlin = "2.4.10"

# Build Config
minSdk = "26"
targetSdk = "36"
compileSdk = "36"

# Jetpack & Libraries
coreKtx = "1.19.0"
appcompat = "1.7.1"
material = "1.14.0"
constraintlayout = "2.2.1"
lifecycle = "2.11.0"
coroutines = "1.11.0"      # UPDATED: Better match for Kotlin 2.4
okhttp = "5.4.0"
moshi = "1.15.2"
datastore = "1.3.0"        # UPDATED: Modern stable version
preference = "1.2.1"

[libraries]
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
lifecycle-runtime = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
moshi-kotlin = { group = "com.squareup.moshi", name = "moshi-kotlin", version.ref = "moshi" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
preference-ktx = { group = "androidx.preference", name = "preference-ktx", version.ref = "preference" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }


---

### 🎨 UI Design System

**Theme**: Material Design 3 with `Theme.Material3.DayNight.NoActionBar`
- Custom `Toolbar` instead of ActionBar
- `MaterialCardView` for all cards (elevation 2dp default, 8dp active simulation)
- `TextInputLayout` with `OutlinedBox` style for all input fields
- `MaterialButton` with icon for primary actions
- `ChipGroup` horizontal scroll for recent locations
- `BottomSheetDialog` for search results dropdown
- Smooth `CircularProgressIndicator` while fetching search/altitude
- Status color: Green `#4CAF50` (simulating) / Grey `#9E9E9E` (stopped) / Amber `#FF9800` (freezing)

**Color palette suggestion (Dark theme)**:
```xml
colorPrimary     = #4FC3F7   (sky blue — neutral, non-suspicious)
colorSecondary   = #81C784   (soft green — active/live indicator)
colorBackground  = #121212
colorSurface     = #1E1E1E
colorError       = #EF5350
```

---

## SECTION 3 — best practises


| `location.isFromMockProvider()` | Boolean flag set by Android on all mock locations | use a custom provider name matching a real GNSS provider |
| `location.isMock` (API 31+) | New stricter flag in Android 12+ | Same —
| Provider name check | App checks if provider is `"gps"` or `"network"` | Set provider name to `LocationManager.GPS_PROVIDER` (`"gps"`) exactly |
| `Location.extras` bundle | Mock locations often have null or missing extras | Inject realistic extras: `Bundle().apply { putInt("satellites", 8) }` |
| Speed/accuracy sanity check | App checks if speed is impossibly high | Already handled by your realistic 0.15–0.70 m/s range |
| Timestamp delta check | App checks if timestamps increment realistically | Use `SystemClock.elapsedRealtimeNanos()` for `setElapsedRealtimeNanos()` |
| NMEA sentence check | Some apps listen to raw NMEA data from chipset |

### Implement These

1. **Set provider name to `"gps"` exactly**
   ```kotlin
   locationManager.addTestProvider(
       LocationManager.GPS_PROVIDER,  // NOT a custom string
       false, false, false, false, true, true, true,
       Criteria.POWER_LOW, Criteria.ACCURACY_FINE
   )
   ```

2. **Inject realistic `extras` bundle**
   ```kotlin
   location.extras = Bundle().apply {
       putInt("satellites", (6..12).random())
       putFloat("hdop", (0.8f..2.5f).random())
   }
   ```

3. **Set `elapsedRealtimeNanos` correctly**
   ```kotlin
   location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
   ```

4. **Set bearing realistically** (many apps check for `0.0` bearing as a mock signal)
   ```kotlin
   location.bearing = (bearingBase + Random.nextFloat() * 10f - 5f)
       .coerceIn(0f, 360f)
   ```

5. **Vary provider between `"gps"` and `"fused"` per session**
   ```kotlin
   val provider = if (useFlp) "fused" else LocationManager.GPS_PROVIDER
   ```

---

## SECTION 4 — SIMULATION ENGINE SPEC

> Pre-filled. Give this entire section to the AI when asking it to build `SimulationEngine.kt`.

### Parameters (All User-Configurable via Settings)

```kotlin
data class SimConfig(
    val jitterAmplitudeDeg: Double = 0.000008,   // ±0.9m default
    val updateIntervalMs: Long = 1000,
    val updateJitterMs: Long = 80,               // ±80ms interval randomization
    val accuracyMin: Float = 3.5f,
    val accuracyMax: Float = 9.5f,
    val accuracyDriftRate: Float = 0.3f,         // max change per tick
    val speedMin: Float = 0.15f,
    val speedMax: Float = 0.70f,
    val speedZeroProbability: Float = 0.03f,     // 3% chance of 0.0 m/s tick
    val altitudeNoiseM: Double = 1.5,            // ±1.5m per tick Gaussian
    val altitudeDriftAmplitude: Double = 4.0,    // ±4m slow sine wave
    val altitudeDriftPeriodS: Int = 60,          // sine period in seconds
    val bearingDriftPerTick: Float = 5f,         // ±5° per tick
    val burstIntervalTicks: Int = 20,            // burst every ~20 ticks
    val burstDurationTicks: Int = 3,             // burst lasts 3 ticks
    val burstJitterMultiplier: Double = 4.0,     // 4x normal jitter during burst
    val stopMode: StopMode = StopMode.GRADUAL,
    val gradualFadeDurationMs: Long = 12000
)
data class SimConfig(
    // ... [keep previous timing & jitter config] ...

    // NEW: Occasional large signal gaps
    val dropoutProbability: Float = 0.015f,      // ~1.5% chance per tick to cause a dropout
    val dropoutDelayMinMs: Long = 1500,
    val dropoutDelayMaxMs: Long = 2500,

    // RESTORED: Randomized bursts
    val burstIntervalMinTicks: Int = 15,
    val burstIntervalMaxTicks: Int = 30,
    val burstDurationTicks: Int = 3,
    
    // RESTORED: Gaussian Speed Profile
    val speedMean: Float = 0.08f,
    val speedStdDev: Float = 0.1f,
    val speedMin: Float = 0.0f,
    val speedMax: Float = 0.67f,
    val speedZeroProbability: Float = 0.025f,
    
    // ... [keep previous altitude, accuracy & stop config] ...
)
enum class StopMode { INSTANT, GRADUAL, FREEZE }
```

### Core Tick Logic (Pseudocode)

```

```// State variables held outside the loop
var nextBurstTarget = Random(burstIntervalMin, burstIntervalMax)
var ticksSinceLastBurst = 0

every tick:
  // 1. Timing (Restoring the occasional gap)
  if (Random < dropoutProbability) {
      delay(Random(dropoutDelayMin, dropoutDelayMax))
  } else {
      delay(updateInterval + Random(-updateJitter, +updateJitter))
  }
  
  // 2. Burst State Calculation (Randomized intervals)
  isBurst = (ticksSinceLastBurst < burstDuration)
  ticksSinceLastBurst++
  
  if (ticksSinceLastBurst >= nextBurstTarget) {
      ticksSinceLastBurst = 0
      nextBurstTarget = Random(burstIntervalMin, burstIntervalMax)
  }

  // 3. Position Jitter (Using your damped noise concept)
  effectiveJitter = if(isBurst) 0.000035 else 0.000008
  lat += dampedNoise(effectiveJitter)
  lon += dampedNoise(effectiveJitter)

  // 4. Accuracy & Metadata 
  if (isBurst) {
      satellites = Random(4, 7)
      hdop = Random(1.5, 3.0)
      accuracyDrift = (Random - 0.5) * 1.2  // Faster drift during burst
  } else {
      satellites = Random(8, 12)
      hdop = Random(0.8, 1.4)
      accuracyDrift = (Random - 0.5) * 0.8  // Slower drift normal
  }
  
  accuracy = clamp(accuracy + accuracyDrift, accuracyMin, accuracyMax)
  
  // Restoring dynamic vertical accuracy multiplier
  verticalAccuracy = accuracy * (1.5 + Random * 0.5) 

  // 5. Kinematics (Restoring Gaussian distribution)
  if (Random < speedZeroProbability) {
      speed = 0.0
  } else {
      // Draw from normal curve, coerce to boundaries
      speed = clamp(nextGaussian(mean = speedMean, stdDev = speedStdDev), speedMin, speedMax)
  }

  if (speed >= 0.05) {
      bearing = wrap360(bearing + randomWalk(maxDelta = 5.0))
      location.hasBearing = true
  } else {
      location.hasBearing = false 
  }

  // 6. Altitude
  altitude = baseAlt + sin(tickCount * 0.008) * 4.0 + gaussianNoise(1.5)

  // 7. Payload Assembly & System Sync
  location.extras = Bundle(satellites = satellites, hdop = hdop)
  location.verticalAccuracyMeters = verticalAccuracy
  location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
  
  // 8. Dispatch
  locationManager.setTestProviderLocation("gps", location)
  tickCount++

ALREADY WRITTEN SIMULATIONENGINE.KT ASK IF ANYTHING IS CONFUSING

## SECTION 5 — FEATURE COMPLETION CHECKLIST

Copy this into a GitHub Project or issue to track build progress:

### Phase 1 — Core (Build First)
- [ ] Project setup: Kotlin DSL + version catalog
- [ ] `MockLocationService.kt` (ForegroundService)
- [ ] `SimulationEngine.kt` with full tick logic
- [ ] `MainActivity` + `MainViewModel` (MVVM)
- [ ] Basic lat/lon input + Start/Stop
- [ ] Foreground notification with Stop action
- [ ] Gradual stop / Freeze mode

### Phase 2 — Location Input
- [ ] Nominatim search with debounce
- [ ] Recent locations (DataStore)
- [ ] Google Maps share link parser
- [ ] `MapPickerActivity` (WebView + Leaflet)
- [ ] `map_picker.html` with tap-to-pin + confirm

### Phase 3 — Altitude & Polish
- [ ] OpenTopoData altitude fetch + caching
- [ ] Altitude sine drift simulation
- [ ] Settings screen (DataStore-backed)
- [ ] All simulation params user-configurable
- [ ] Dark/light theme support

### Phase 4 — Advanced (Optional)
- [ ] Route path simulation
- [ ] MapmyIndia search toggle
- [ ] Bearing auto-rotate along path
- [ ] Mini live map preview on main screen
- [ ] Export simulation log (CSV)

---

## SECTION 6 — MANIFEST TEMPLATE

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />

    <!-- Network -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Foreground Service (Android 9+) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Keep alive -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".App"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"          <!-- CHANGE THIS — no GPS/Mock words -->
        android:theme="@style/Theme.App"
        android:hardwareAccelerated="true">       <!-- Required for WebView map smoothness -->

        <activity android:name=".ui.main.MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".ui.mappicker.MapPickerActivity"
            android:exported="false" />

        <activity android:name=".ui.settings.SettingsActivity"
            android:exported="false" />

        <service android:name=".core.MockLocationService"
            android:exported="false"
            android:foregroundServiceType="location" />

    </application>
</manifest>
```

---

## HOW TO USE THIS DOCUMENT WITH AN AI

Once you have answered all questions in Section 1, use this prompt:

> "Read `BUILD_BLUEPRINT.md` in my GitHub repo `techv1/peanuts`. I have answered all questions in Section 1.
> Build the complete Android app from scratch using the architecture in Section 2.
> Start with Phase 1 from the checklist in Section 5.
> Use Kotlin DSL gradle, Material Design 3 XML Views, MVVM, Coroutines, OkHttp, Moshi, DataStore.
> Do NOT use Jetpack Compose. Do NOT use Hilt. Generate complete, working, production-quality code."

