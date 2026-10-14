package io.github.aleks007k.poezdki

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Шаги первого запуска. Каждый показывается один раз. */
enum class SetupStep { FUEL, LOCATION, BACKGROUND, NOTIFICATIONS, BATTERY }

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var db: Db

    var setupStep by mutableStateOf<SetupStep?>(null)
        private set

    /** Поездка, которая ждёт разрешения или включения GPS. */
    private var pending: Pair<String, LatLon?>? = null
    private var resolving = false
    private var resolveAgain = false

    private val askLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshSetup()
            val p = pending ?: return@registerForActivityResult
            pending = null
            if (hasFineLocation()) go(p.first, p.second)
            else toast(R.string.need_location)
        }

    private val askOne =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSetup() }

    private val askGps =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            val p = pending ?: return@registerForActivityResult
            pending = null
            if (r.resultCode == RESULT_OK) startTrip(p.first, p.second)
            else toast(R.string.need_gps)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = Prefs(this)
        db = Db.get(this)
        refreshSetup()
        setContent { PoezdkiApp(this, prefs, db) }
        // Пока приложение на экране: при открытии и после каждой новой поездки
        // определить адреса, которые ещё не определены.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppState.dataVersion.collect { resolveAddresses() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        TripService.resumeIfNeeded(this)
    }

    // --- Первый запуск ---

    fun refreshSetup() {
        setupStep = when {
            !prefs.hasFuelSettings -> SetupStep.FUEL
            !hasFineLocation() && !prefs.wasAsked("loc") -> SetupStep.LOCATION
            Build.VERSION.SDK_INT >= 29 && hasFineLocation() && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                !prefs.wasAsked("bg") -> SetupStep.BACKGROUND
            Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) &&
                !prefs.wasAsked("notif") -> SetupStep.NOTIFICATIONS
            !batteryUnrestricted() && !prefs.wasAsked("battery") -> SetupStep.BATTERY
            else -> null
        }
    }

    fun saveFuel(litersPer100: Double, pricePerLiter: Double) {
        prefs.litersPer100 = litersPer100
        prefs.pricePerLiter = pricePerLiter
        refreshSetup()
    }

    fun setupAction(step: SetupStep) {
        when (step) {
            SetupStep.FUEL -> Unit
            SetupStep.LOCATION -> {
                prefs.markAsked("loc")
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            SetupStep.BACKGROUND -> {
                prefs.markAsked("bg")
                if (Build.VERSION.SDK_INT >= 29) askOne.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            SetupStep.NOTIFICATIONS -> {
                prefs.markAsked("notif")
                if (Build.VERSION.SDK_INT >= 33) askOne.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            SetupStep.BATTERY -> openAppSettings()
        }
    }

    fun finishBatteryStep() {
        prefs.markAsked("battery")
        refreshSetup()
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            finishBatteryStep()
        }
    }

    // --- Поездка ---

    /** Стрелка или кнопка места: включить GPS, начать запись, открыть Google Maps. */
    fun go(destination: String, target: LatLon?) {
        if (!hasFineLocation()) {
            pending = destination to target
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Config.GPS_INTERVAL_MS).build()
        LocationServices.getSettingsClient(this)
            .checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build())
            .addOnSuccessListener { startTrip(destination, target) }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    // Системное окно «Включить геолокацию?»
                    pending = destination to target
                    askGps.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else if (gpsOn()) {
                    startTrip(destination, target)
                } else {
                    // На телефонах без сервисов Google — обычный экран настроек.
                    toast(R.string.need_gps)
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (e: ActivityNotFoundException) {
                    }
                }
            }
    }

    private fun startTrip(destination: String, target: LatLon?) {
        TripService.start(this, destination, target)
        if (target == null) {
            // Координаты цели нужны для правила «рядом с целью». Не нашлись — работает правило 10 минут.
            lifecycleScope.launch {
                Geo.coordsOf(this@MainActivity, destination)?.let { TripService.setTarget(this@MainActivity, it) }
            }
        }
        openMaps(destination)
    }

    private fun openMaps(destination: String) {
        val q = Uri.encode(destination)
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q&mode=d"))
                    .setPackage("com.google.android.apps.maps")
            )
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q&travelmode=driving"))
                )
            } catch (e: ActivityNotFoundException) {
            }
        }
    }

    fun stopTrip() = TripService.stop(this)

    // --- Места и адреса ---

    fun savePlace(id: Int, name: String?, address: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.savePlace(Place(id, name, address, null)) }
            AppState.dataChanged()
            val coords = Geo.coordsOf(this@MainActivity, address) ?: return@launch
            withContext(Dispatchers.IO) { db.savePlace(Place(id, name, address, coords)) }
            AppState.dataChanged()
        }
    }

    fun deleteTrip(id: Long) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.deleteTrip(id) }
            AppState.dataChanged()
        }
    }

    /** Определить адреса, которые не определились раньше. Только пока приложение на экране. */
    private fun resolveAddresses() {
        if (resolving) {
            resolveAgain = true
            return
        }
        resolving = true
        resolveAgain = false
        lifecycleScope.launch {
            try {
                var changed = false
                val places = withContext(Dispatchers.IO) { db.places().values.toList() }
                for (p in places) {
                    if (p.coords != null || p.address.isNullOrBlank()) continue
                    val c = Geo.coordsOf(this@MainActivity, p.address) ?: continue
                    withContext(Dispatchers.IO) { db.savePlace(p.copy(coords = c)) }
                    changed = true
                }
                val trips = withContext(Dispatchers.IO) { db.tripsWithoutAddress() }
                for (t in trips) {
                    val from = t.startAddress ?: t.start?.let { Geo.addressOf(this@MainActivity, it) }
                    val to = t.endAddress ?: t.end?.let { Geo.addressOf(this@MainActivity, it) }
                    if (from != t.startAddress || to != t.endAddress) {
                        withContext(Dispatchers.IO) { db.setAddresses(t.id, from, to) }
                        changed = true
                    }
                }
                if (changed) AppState.dataChanged()
            } finally {
                resolving = false
                if (resolveAgain) resolveAddresses()
            }
        }
    }

    // --- Проверки ---

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasFineLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun gpsOn() = getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun batteryUnrestricted() =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_LONG).show()
}
