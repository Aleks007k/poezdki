package io.github.aleks007k.poezdki

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
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
enum class SetupStep { FUEL, LOCATION, BACKGROUND, ACTIVITY, NOTIFICATIONS, BATTERY }

private const val KEY_PENDING_DEST = "pending_dest"

/**
 * Страховка без связи с машиной: Blitzer.de закрыл карту — открыл «Борт №1» в первые
 * 2 минуты поездки, и карта сразу выходит наверх. Срабатывает один раз.
 */
private object NavOnReturn {
    private var until = 0L
    private var wentAway = false

    fun arm() {
        until = System.currentTimeMillis() + Config.NAV_ON_RETURN_MS
        wentAway = false
    }

    /** Приложение ушло с экрана (его закрыли Blitzer и карта). */
    fun left() {
        if (until > 0) wentAway = true
    }

    /** Пора открыть карту снова? (один раз) */
    fun take(): Boolean {
        val now = System.currentTimeMillis()
        val ok = wentAway && now < until
        if (ok || now >= until) {
            until = 0
            wentAway = false
        }
        return ok
    }
}
private const val KEY_PENDING_TARGET = "pending_target"

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var db: Db

    var setupStep by mutableStateOf<SetupStep?>(null)
        private set

    /** Открытая форма заправки (null — закрыта). Хранится в [RefuelSession] — переживает поворот экрана. */
    var refuelDraft: RefuelDraft?
        get() = RefuelSession.draft
        private set(v) {
            RefuelSession.draft = v
        }

    /** Курсы, которые пора подтвердить (раз в месяц, если были заправки не в евро). */
    var ratesToCheck by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    private var cameraFile: java.io.File?
        get() = RefuelSession.cameraPath?.let(::File)
        set(v) {
            RefuelSession.cameraPath = v?.path
        }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            onReceiptPhoto(Uri.fromFile(f))
        } else if (cameraOpenedForm) {
            // Открыли камеру кнопкой ⛽ и передумали — пустую форму не оставляем.
            refuelDraft = null
        }
        cameraOpenedForm = false
    }
    private var cameraOpenedForm: Boolean
        get() = RefuelSession.cameraOpenedForm
        set(v) {
            RefuelSession.cameraOpenedForm = v
        }

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onReceiptPhoto(uri)
    }

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
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSetup()
            AutoDetect.register(this)
        }

    private val askGps =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            val p = pending ?: return@registerForActivityResult
            pending = null
            if (r.resultCode == RESULT_OK) startTrip(p.first, p.second)
            else toast(R.string.need_gps)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тёмная тема всегда: светлые значки в строке состояния.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        prefs = Prefs(this)
        db = Db.get(this)
        restoreState(savedInstanceState)
        refreshSetup()
        setContent { PoezdkiApp(this, prefs, db) }
        handleDriveYes(intent)
        // Пока приложение на экране: при открытии и после каждой новой поездки
        // определить адреса, которые ещё не определены.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                AppState.dataVersion.collect { resolveAddresses() }
            }
        }
    }

    /** Android закрыл приложение (поворот экрана, нехватка памяти) — сохранить недоделанное. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        RefuelSession.save(outState)
        pending?.let { (dest, target) ->
            outState.putString(KEY_PENDING_DEST, dest)
            target?.let { outState.putDoubleArray(KEY_PENDING_TARGET, doubleArrayOf(it.lat, it.lon)) }
        }
    }

    private fun restoreState(saved: Bundle?) {
        if (saved == null) return
        // Чек читали, когда Android закрыл приложение, — прочитать снова с сохранённого фото.
        if (RefuelSession.restore(saved)) refuelDraft?.photo?.let(::rescanReceipt)
        saved.getString(KEY_PENDING_DEST)?.let { dest ->
            val t = saved.getDoubleArray(KEY_PENDING_TARGET)
            pending = dest to t?.let { LatLon(it[0], it[1]) }
        }
    }

    override fun onStop() {
        super.onStop()
        NavOnReturn.left()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDriveYes(intent)
    }

    /** «ДА» на вопросе «Едешь на своём авто?» — начать запись. */
    private fun handleDriveYes(intent: Intent?) {
        if (intent?.action != Questions.ACTION_DRIVE_YES) return
        intent.action = null // чтобы при повороте экрана не начать снова
        // Только настоящая кнопка «ДА» из нашего вопроса: метка должна совпасть.
        val token = intent.getStringExtra(Questions.EXTRA_TOKEN)
        if (token.isNullOrEmpty() || token != prefs.driveToken) return
        prefs.driveToken = ""
        Questions.cancel(this)
        prefs.askedUntil = 0
        prefs.askOpen = false
        if (!prefs.hasActiveTrip) recordNow()
    }

    override fun onResume() {
        super.onResume()
        reopenNavigatorIfNeeded()
        refreshSetup()
        TripService.resumeIfNeeded(this)
        AutoDetect.register(this)
        refreshRatesCheck()
    }

    // --- Первый запуск ---

    fun refreshSetup() {
        setupStep = when {
            !prefs.hasFuelSettings -> SetupStep.FUEL
            !hasFineLocation() && !prefs.wasAsked("loc") -> SetupStep.LOCATION
            Build.VERSION.SDK_INT >= 29 && hasFineLocation() && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) &&
                !prefs.wasAsked("bg") -> SetupStep.BACKGROUND
            Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACTIVITY_RECOGNITION) &&
                !prefs.wasAsked("activity") -> SetupStep.ACTIVITY
            Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) &&
                !prefs.wasAsked("notif") -> SetupStep.NOTIFICATIONS
            !batteryUnrestricted() && !prefs.wasAsked("battery") -> SetupStep.BATTERY
            else -> null
        }
    }

    fun saveFuel(litersPer100: Double, pricePerLiter: Double) {
        prefs.car = prefs.car.copy(litersPer100 = litersPer100, pricePerLiter = pricePerLiter)
        refreshSetup()
        AppState.dataChanged()
    }

    /** Экран «Машина». Действует на новые поездки. */
    fun saveCar(car: CarCosts) {
        prefs.car = car
        refreshSetup()
        AppState.dataChanged()
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
            SetupStep.ACTIVITY -> {
                prefs.markAsked("activity")
                if (Build.VERSION.SDK_INT >= 29) askOne.launch(Manifest.permission.ACTIVITY_RECOGNITION)
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

    fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (e: ActivityNotFoundException) {
            finishBatteryStep()
        }
    }

    // --- Поездка ---

    /** Кнопка «● Запись»: начать запись сейчас, без адреса и навигации. */
    fun recordNow() = go("", null)

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
        prefs.declinedDrive = false // сам начал запись — прошлое «НЕТ» больше не действует
        TripService.start(this, destination, target)
        // Сначала приложения «вместе с поездкой» (Blitzer.de, музыка…)…
        val opened = launchCompanions()
        if (destination.isBlank()) return
        if (target == null) {
            // Координаты цели нужны для правила «рядом с целью». Не нашлись — работает правило 10 минут.
            lifecycleScope.launch {
                Geo.coordsOf(this@MainActivity, destination)?.let { TripService.setTarget(this@MainActivity, it) }
            }
        }
        // …и сразу навигатор поверх них.
        Navigator.open(this, prefs.navigator, destination, target)
        if (opened == 0) return
        // Blitzer.de при первом запуске через секунду-две сам вылезает поверх карты.
        if (CarLink.linkedAddress(this) != null) {
            // Связь с машиной: Android разрешает ещё раз вывести карту наверх из фона.
            val app = applicationContext
            val nav = prefs.navigator
            Handler(Looper.getMainLooper()).postDelayed(
                { Navigator.open(app, nav, destination, Prefs(app).activeTarget ?: target, quiet = true) },
                Config.NAV_AGAIN_AFTER_MS,
            )
        } else {
            // Без связи: вернёшься в «Борт №1» — карта откроется сама (это Android разрешает).
            NavOnReturn.arm()
        }
    }

    /** Вернулся в приложение вскоре после старта — значит, карту закрыли: открыть её снова. */
    private fun reopenNavigatorIfNeeded() {
        if (!NavOnReturn.take() || !prefs.hasActiveTrip) return
        val dest = prefs.activeDestination
        if (dest.isNotBlank()) Navigator.open(this, prefs.navigator, dest, prefs.activeTarget, quiet = true)
    }

    fun stopTrip() = TripService.stop(this)

    // --- Заправки ---

    private fun blankDraft(): RefuelDraft {
        val cur = prefs.lastCurrency
        return RefuelDraft(
            date = java.time.LocalDate.now(),
            currency = cur,
            rate = if (cur == "EUR") "" else prefs.rates[cur]?.let { Format.number(it, 4) }.orEmpty(),
        )
    }

    /** Кнопка ⛽ / «Фото чека»: сразу камера, потом форма с прочитанными цифрами. */
    fun newRefuelFromCamera() {
        refuelDraft = blankDraft()
        cameraOpenedForm = true
        refuelCamera()
    }

    fun newRefuelManual() {
        refuelDraft = blankDraft()
    }

    fun editRefuel(r: Refuel) {
        refuelDraft = RefuelDraft.of(r)
    }

    fun updateRefuel(d: RefuelDraft) {
        // Сменили валюту — подставить её последний курс.
        val old = refuelDraft
        refuelDraft = if (old != null && old.currency != d.currency) {
            d.copy(rate = prefs.rates[d.currency]?.let { Format.number(it, 4) }.orEmpty())
        } else d
    }

    fun refuelCamera() {
        val (file, uri) = Receipts.cameraTarget(this)
        cameraFile = file
        try {
            takePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            cameraOpenedForm = false
        }
    }

    fun refuelGallery() {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Фото чека получено: сохранить, прочитать текст, дописать цифры в форму. */
    private fun onReceiptPhoto(uri: Uri) {
        val draft = refuelDraft ?: return
        val app = applicationContext
        val rates = prefs.rates
        // Не lifecycleScope: при повороте экрана чтение чека не должно обрываться.
        RefuelSession.scope.launch {
            refuelDraft = draft.copy(scanning = true)
            val stored = Receipts.store(app, uri)
            cameraFile?.delete()
            if (stored == null) {
                refuelDraft = refuelDraft?.copy(scanning = false, scanEmpty = true)
                return@launch
            }
            val (file, bitmap) = stored
            refuelDraft?.photo?.takeIf { it != file.path && draft.id == 0L }?.let { Receipts.delete(it) }
            refuelDraft = refuelDraft?.copy(photo = file.path)
            val text = Receipts.readText(bitmap)
            val parsed = text?.let { ReceiptParser.parse(it) } ?: ParsedReceipt()
            refuelDraft = refuelDraft?.merge(parsed, rates)
        }
    }

    /** Прочитать чек ещё раз с уже сохранённого фото (после перезапуска приложения). */
    private fun rescanReceipt(path: String) {
        val rates = prefs.rates
        RefuelSession.scope.launch {
            refuelDraft = refuelDraft?.copy(scanning = true)
            val text = Receipts.load(path, Receipts.MAX_SIDE)?.let { Receipts.readText(it) }
            val parsed = text?.let { ReceiptParser.parse(it) } ?: ParsedReceipt()
            refuelDraft = refuelDraft?.merge(parsed, rates)
        }
    }

    fun cancelRefuel() {
        val d = refuelDraft ?: return
        // Новая заправка не сохранена — её фото не нужно.
        if (d.id == 0L) Receipts.delete(d.photo)
        refuelDraft = null
    }

    /** Сохранить. true — сохранено (форму закрываем). */
    fun saveRefuel(): Boolean {
        val r = refuelDraft?.toRefuel() ?: return false
        RefuelSession.scope.launch {
            withContext(Dispatchers.IO) {
                db.saveRefuel(r)
                prefs.lastCurrency = r.currency
                if (r.currency != "EUR") prefs.rates = prefs.rates + (r.currency to r.rate)
                // Цена литра для расчётов — по самому свежему чеку.
                val newest = db.refuels().maxByOrNull { it.time }
                if (newest != null && newest.liters > 0) prefs.car = prefs.car.copy(pricePerLiter = newest.priceEur)
            }
            refreshRatesCheck()
            AppState.dataChanged()
        }
        refuelDraft = null
        return true
    }

    fun deleteRefuel() {
        val d = refuelDraft ?: return
        RefuelSession.scope.launch {
            withContext(Dispatchers.IO) { db.deleteRefuel(d.id) }
            Receipts.delete(d.photo)
            AppState.dataChanged()
        }
        refuelDraft = null
    }

    /** Реальный расход по заправкам — в расчёты (вместо «город / трасса»). */
    fun useRealConsumption(l100: Double) {
        prefs.car = prefs.car.copy(litersPer100 = l100, litersCity = 0.0)
        AppState.dataChanged()
    }

    private fun currentMonth() = java.time.YearMonth.now().toString()

    /** Раз в месяц: были ли заправки не в евро за 3 месяца — тогда спросить про курс. */
    private fun refreshRatesCheck() {
        if (prefs.ratesConfirmedMonth == currentMonth()) {
            ratesToCheck = emptyMap()
            return
        }
        lifecycleScope.launch {
            val since = java.time.LocalDate.now().minusMonths(3).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val used = withContext(Dispatchers.IO) { db.refuels(since).map { it.currency }.filter { it != "EUR" }.toSet() }
            val rates = prefs.rates
            ratesToCheck = used.mapNotNull { c -> rates[c]?.let { c to it } }.toMap()
        }
    }

    fun confirmRates(newRates: Map<String, Double>? = null) {
        if (newRates != null) prefs.rates = prefs.rates + newRates
        prefs.ratesConfirmedMonth = currentMonth()
        ratesToCheck = emptyMap()
    }

    /** Открыть по очереди приложения «вместе с поездкой». Возвращает, сколько открылось. */
    private fun launchCompanions(): Int {
        var opened = 0
        for (app in prefs.companions) {
            val launch = packageManager.getLaunchIntentForPackage(app.pkg)
            try {
                if (launch == null) throw ActivityNotFoundException() // удалили или Android его не видит
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                opened++
            } catch (e: Exception) {
                // Не открылось — поездка всё равно записывается, но скажем об этом.
                Toast.makeText(this, getString(R.string.companion_failed, app.label), Toast.LENGTH_LONG).show()
            }
        }
        return opened
    }

    fun addCompanion(app: LaunchableApp) {
        val list = prefs.companions
        if (list.none { it.pkg == app.packageName } && list.size < Config.MAX_COMPANIONS) {
            prefs.companions = list + CompanionApp(app.packageName, app.label)
            AppState.dataChanged()
        }
    }

    // --- Связь с машиной ---

    /** Список Bluetooth для выбора машины (null — окно закрыто). */
    var carPicker by mutableStateOf<List<CarLink.BtDevice>?>(null)
        private set
    private var linkingName: String? = null

    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) linkCar() else toast(R.string.car_link_need_permission)
    }

    private val confirmLink = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            prefs.carName = linkingName
            toast(R.string.car_linked)
        }
        AppState.dataChanged()
    }

    /** «Связать с машиной»: разрешение → выбор машины (если их несколько) → окно Android «Разрешить?». */
    fun linkCar() {
        if (CarLink.needsPermission(this)) {
            askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val devices = CarLink.pairedDevices(this)
        val cars = devices.filter { it.isCar }
        when {
            devices.isEmpty() -> toast(R.string.car_link_no_devices)
            cars.size == 1 -> linkCarTo(cars[0])
            else -> carPicker = devices
        }
    }

    fun linkCarTo(device: CarLink.BtDevice) {
        carPicker = null
        linkingName = device.name
        CarLink.link(
            this, device.address,
            onConfirm = { confirmLink.launch(IntentSenderRequest.Builder(it).build()) },
            onFail = { toast(R.string.car_link_failed) },
        )
    }

    fun closeCarPicker() {
        carPicker = null
    }

    fun unlinkCar() {
        CarLink.unlink(this)
        prefs.carName = null
        AppState.dataChanged()
    }

    fun removeCompanion(pkg: String) {
        prefs.companions = prefs.companions.filter { it.pkg != pkg }
        AppState.dataChanged()
    }

    fun setNavigator(nav: String) {
        prefs.navigator = nav
        AppState.dataChanged()
    }

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

    fun addPlace(name: String, address: String) {
        lifecycleScope.launch {
            val id = withContext(Dispatchers.IO) { db.nextPlaceId() }
            savePlace(id, name, address)
        }
    }

    fun deletePlace(id: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.deletePlace(id) }
            AppState.dataChanged()
        }
    }

    fun setPaid(id: Long, paid: Boolean) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.setPaid(id, paid) }
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
