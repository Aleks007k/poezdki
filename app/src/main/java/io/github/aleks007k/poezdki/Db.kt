package io.github.aleks007k.poezdki

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Поездка. Расход и цена — те, что действовали в момент поездки. */
data class Trip(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val start: LatLon?,
    val end: LatLon?,
    val startAddress: String?,
    val endAddress: String?,
    val distanceM: Double,
    val litersPer100: Double,
    val pricePerLiter: Double,
) {
    val cost get() = Format.cost(distanceM, litersPer100, pricePerLiter)
}

/** Кнопка места: 1 — Дом, 2 — Работа, 3 — своя. */
data class Place(val id: Int, val name: String?, val address: String?, val coords: LatLon?)

/** База данных SQLite. Лежит только в телефоне. */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "poezdki.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL, end_time INTEGER NOT NULL,
                start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,
                start_addr TEXT, end_addr TEXT,
                distance_m REAL NOT NULL, l_per_100 REAL NOT NULL, price REAL NOT NULL)"""
        )
        db.execSQL("CREATE INDEX trips_start ON trips(start_time)")
        // Точки GPS текущей поездки. После конца поездки удаляются.
        db.execSQL(
            """CREATE TABLE points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
                acc REAL NOT NULL, speed REAL)"""
        )
        db.execSQL(
            """CREATE TABLE places (
                id INTEGER PRIMARY KEY, name TEXT, address TEXT, lat REAL, lon REAL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Новые версии базы будут добавлять изменения здесь, не удаляя поездки.
    }

    // --- Поездки ---

    fun insertTrip(t: Trip) {
        writableDatabase.insert("trips", null, ContentValues().apply {
            put("start_time", t.startTime)
            put("end_time", t.endTime)
            t.start?.let { put("start_lat", it.lat); put("start_lon", it.lon) }
            t.end?.let { put("end_lat", it.lat); put("end_lon", it.lon) }
            put("start_addr", t.startAddress)
            put("end_addr", t.endAddress)
            put("distance_m", t.distanceM)
            put("l_per_100", t.litersPer100)
            put("price", t.pricePerLiter)
        })
    }

    fun trips(fromMillis: Long, toMillis: Long): List<Trip> =
        readableDatabase.rawQuery(
            "SELECT * FROM trips WHERE start_time >= ? AND start_time < ? ORDER BY start_time DESC",
            arrayOf(fromMillis.toString(), toMillis.toString()),
        ).use { c -> c.list { readTrip(it) } }

    fun tripsWithoutAddress(): List<Trip> =
        readableDatabase.rawQuery(
            """SELECT * FROM trips WHERE (start_addr IS NULL AND start_lat IS NOT NULL)
               OR (end_addr IS NULL AND end_lat IS NOT NULL)""",
            null,
        ).use { c -> c.list { readTrip(it) } }

    fun setAddresses(id: Long, startAddress: String?, endAddress: String?) {
        writableDatabase.update("trips", ContentValues().apply {
            put("start_addr", startAddress)
            put("end_addr", endAddress)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteTrip(id: Long) {
        writableDatabase.delete("trips", "id = ?", arrayOf(id.toString()))
    }

    private fun readTrip(c: Cursor) = Trip(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
        endTime = c.getLong(c.getColumnIndexOrThrow("end_time")),
        start = c.latLon("start_lat", "start_lon"),
        end = c.latLon("end_lat", "end_lon"),
        startAddress = c.str("start_addr"),
        endAddress = c.str("end_addr"),
        distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m")),
        litersPer100 = c.getDouble(c.getColumnIndexOrThrow("l_per_100")),
        pricePerLiter = c.getDouble(c.getColumnIndexOrThrow("price")),
    )

    // --- Точки GPS ---

    fun insertPoint(p: GpsPoint) {
        writableDatabase.insert("points", null, ContentValues().apply {
            put("t", p.time)
            put("lat", p.lat)
            put("lon", p.lon)
            put("acc", p.accuracyM)
            p.speedKmh?.let { put("speed", it) }
        })
    }

    fun points(): List<GpsPoint> =
        readableDatabase.rawQuery("SELECT * FROM points ORDER BY id", null).use { c ->
            c.list {
                GpsPoint(
                    time = it.getLong(it.getColumnIndexOrThrow("t")),
                    lat = it.getDouble(it.getColumnIndexOrThrow("lat")),
                    lon = it.getDouble(it.getColumnIndexOrThrow("lon")),
                    accuracyM = it.getFloat(it.getColumnIndexOrThrow("acc")),
                    speedKmh = it.dbl("speed"),
                )
            }
        }

    fun clearPoints() {
        writableDatabase.delete("points", null, null)
    }

    // --- Места ---

    fun places(): Map<Int, Place> =
        readableDatabase.rawQuery("SELECT * FROM places", null).use { c ->
            c.list {
                Place(
                    id = it.getInt(it.getColumnIndexOrThrow("id")),
                    name = it.str("name"),
                    address = it.str("address"),
                    coords = it.latLon("lat", "lon"),
                )
            }
        }.associateBy { it.id }

    fun savePlace(p: Place) {
        writableDatabase.insertWithOnConflict("places", null, ContentValues().apply {
            put("id", p.id)
            put("name", p.name)
            put("address", p.address)
            if (p.coords != null) {
                put("lat", p.coords.lat); put("lon", p.coords.lon)
            } else {
                putNull("lat"); putNull("lon")
            }
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        @Volatile private var instance: Db? = null
        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }
    }
}

private inline fun <T> Cursor.list(read: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count)
    while (moveToNext()) out += read(this)
    return out
}

private fun Cursor.str(col: String): String? =
    getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }

private fun Cursor.dbl(col: String): Double? =
    getColumnIndexOrThrow(col).let { if (isNull(it)) null else getDouble(it) }

private fun Cursor.latLon(latCol: String, lonCol: String): LatLon? {
    val lat = dbl(latCol) ?: return null
    val lon = dbl(lonCol) ?: return null
    return LatLon(lat, lon)
}
