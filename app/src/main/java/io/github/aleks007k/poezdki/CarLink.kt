package io.github.aleks007k.poezdki

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Связь с Bluetooth своей машины (механизм Android «сопутствующее устройство»).
 *
 * Зачем: Android запрещает приложению из фона открывать другие приложения. Для приложений,
 * связанных с устройством пользователя, Android делает исключение — тогда после Blitzer.de
 * можно через несколько секунд ещё раз вывести карту наверх. Связь делается один раз и
 * действует всегда, даже когда телефон к машине не подключён.
 */
object CarLink {
    /** Устройство из списка Bluetooth, к которому телефон уже подключался. */
    data class BtDevice(val address: String, val name: String, val isCar: Boolean)

    private fun manager(context: Context): CompanionDeviceManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            context.getSystemService(CompanionDeviceManager::class.java)
        } else null

    /** Телефон умеет такую связь. */
    fun available(context: Context) = manager(context) != null

    /** Адрес связанной машины или null. */
    @Suppress("DEPRECATION") // новый способ только с Android 13; этот работает везде
    fun linkedAddress(context: Context): String? = try {
        manager(context)?.associations?.firstOrNull()
    } catch (e: Exception) {
        null
    }

    /** Нужно разрешение «Устройства поблизости», чтобы увидеть список Bluetooth (Android 12+). */
    fun needsPermission(context: Context) = Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    /** Bluetooth‑устройства, к которым телефон подключался; похожие на машину — первыми. */
    @SuppressLint("MissingPermission") // проверено в needsPermission()
    fun pairedDevices(context: Context): List<BtDevice> = try {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        adapter?.bondedDevices.orEmpty().map { d ->
            val cls = d.bluetoothClass?.deviceClass
            BtDevice(
                d.address, d.name ?: d.address,
                cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
            )
        }.sortedWith(compareBy({ !it.isCar }, { it.name.lowercase() }))
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Попросить Android связать с машиной. Android покажет своё окно «Разрешить?» — его запускает [onConfirm]. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    fun link(context: Context, address: String, onConfirm: (IntentSender) -> Unit, onFail: () -> Unit) {
        val cdm = manager(context) ?: return onFail()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        try {
            cdm.associate(
                request,
                object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) = onConfirm(chooserLauncher)
                    override fun onFailure(error: CharSequence?) = onFail()
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            onFail()
        }
    }

    @Suppress("DEPRECATION")
    fun unlink(context: Context) {
        val address = linkedAddress(context) ?: return
        try {
            manager(context)?.disassociate(address)
        } catch (e: Exception) {
        }
    }
}
