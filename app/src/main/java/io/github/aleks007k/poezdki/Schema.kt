package io.github.aleks007k.poezdki

/**
 * Устройство базы данных: таблицы новой базы и шаги обновления старых.
 * Без Android — чтобы автоматический тест проверял обновление на обычном компьютере.
 *
 * Правило: обновление только ДОБАВЛЯЕТ (столбцы, таблицы), старые поездки не трогает.
 * Новая версия = номер +1, шаг в [upgrade] и такой же столбец в [create].
 */
object Schema {
    const val VERSION = 3

    /** Новая база — сразу последней версии. */
    val create: List<String> = listOf(
        """CREATE TABLE trips (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            start_time INTEGER NOT NULL, end_time INTEGER NOT NULL,
            start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,
            start_addr TEXT, end_addr TEXT,
            distance_m REAL NOT NULL, l_per_100 REAL NOT NULL, price REAL NOT NULL,
            destination TEXT, max_speed REAL NOT NULL DEFAULT 0,
            stopped_ms INTEGER NOT NULL DEFAULT 0, costs TEXT,
            paid INTEGER NOT NULL DEFAULT 0)""",
        "CREATE INDEX trips_start ON trips(start_time)",
        // Точки GPS текущей поездки. После конца поездки удаляются.
        """CREATE TABLE points (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
            acc REAL NOT NULL, speed REAL)""",
        """CREATE TABLE places (
            id INTEGER PRIMARY KEY, name TEXT, address TEXT, lat REAL, lon REAL)""",
    ) + refuels()

    /** Шаги, чтобы довести базу версии [from] до [VERSION]. */
    fun upgrade(from: Int): List<String> = buildList {
        if (from < 2) {
            add("ALTER TABLE trips ADD COLUMN destination TEXT")
            add("ALTER TABLE trips ADD COLUMN max_speed REAL NOT NULL DEFAULT 0")
            add("ALTER TABLE trips ADD COLUMN stopped_ms INTEGER NOT NULL DEFAULT 0")
            add("ALTER TABLE trips ADD COLUMN costs TEXT")
            add("ALTER TABLE trips ADD COLUMN paid INTEGER NOT NULL DEFAULT 0")
        }
        if (from < 3) addAll(refuels())
    }

    /** Заправки: сумма в валюте чека, rate — сколько этой валюты за 1 €. */
    private fun refuels() = listOf(
        """CREATE TABLE refuels (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            time INTEGER NOT NULL, liters REAL NOT NULL, total REAL NOT NULL,
            currency TEXT NOT NULL, rate REAL NOT NULL,
            odometer REAL, full INTEGER NOT NULL DEFAULT 0, photo TEXT)""",
        "CREATE INDEX refuels_time ON refuels(time)",
    )
}
