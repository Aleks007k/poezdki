package io.github.aleks007k.poezdki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Обновление приложения не должно терять поездки: старая база после обновления
 * устроена так же, как новая, и все записи на месте.
 */
class SchemaTest {
    /** База самой первой версии приложения (как она лежит у тех, кто его не обновлял). */
    private val v1 = listOf(
        """CREATE TABLE trips (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            start_time INTEGER NOT NULL, end_time INTEGER NOT NULL,
            start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL,
            start_addr TEXT, end_addr TEXT,
            distance_m REAL NOT NULL, l_per_100 REAL NOT NULL, price REAL NOT NULL)""",
        "CREATE INDEX trips_start ON trips(start_time)",
        """CREATE TABLE points (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            t INTEGER NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
            acc REAL NOT NULL, speed REAL)""",
        """CREATE TABLE places (
            id INTEGER PRIMARY KEY, name TEXT, address TEXT, lat REAL, lon REAL)""",
    )

    private fun db(sql: List<String>): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").apply { exec(sql) }

    private fun Connection.exec(sql: List<String>) = createStatement().use { st -> sql.forEach { st.execute(it) } }

    private fun Connection.rows(sql: String): List<List<String?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            val n = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..n).map { rs.getString(it) }) }
        }
    }

    /** Всё устройство базы: таблицы, индексы и каждый столбец (тип, обязательность, значение по умолчанию). */
    private fun Connection.structure(): List<String> {
        val objects = rows("SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name")
        val tables = objects.filter { it[0] == "table" }.map { it[1]!! }
        return objects.map { it.joinToString(" ") } + tables.flatMap { t ->
            rows("PRAGMA table_info($t)").map { c -> "$t.${c[1]} ${c[2]} notnull=${c[3]} default=${c[4]} pk=${c[5]}" }.sorted()
        }
    }

    @Test
    fun upgradeFromFirstVersionKeepsTripsAndMatchesNewDatabase() {
        val old = db(v1)
        old.exec(
            listOf(
                "INSERT INTO trips (start_time, end_time, start_addr, end_addr, distance_m, l_per_100, price) " +
                    "VALUES (1000, 5000, 'Lindenstraße 5', 'Hauptstraße 120', 87000, 6.5, 1.84)",
                "INSERT INTO places (id, name, address) VALUES (1, 'Дом', 'Lindenstraße 5')",
            )
        )
        old.exec(Schema.upgrade(1))

        assertEquals(db(Schema.create).structure(), old.structure())
        assertEquals(
            listOf(listOf("Lindenstraße 5", "Hauptstraße 120", "87000.0", "6.5", "0", "0.0", "0", null)),
            old.rows("SELECT start_addr, end_addr, distance_m, l_per_100, paid, max_speed, stopped_ms, destination FROM trips"),
        )
        assertEquals(listOf(listOf("Дом")), old.rows("SELECT name FROM places"))
    }

    @Test
    fun upgradeFromSecondVersionMatchesNewDatabase() {
        val v2 = db(v1)
        v2.exec(Schema.upgrade(1).filter { it.startsWith("ALTER") }) // так выглядела версия 2
        v2.exec(Schema.upgrade(2))
        assertEquals(db(Schema.create).structure(), v2.structure())
    }

    @Test
    fun everyOldVersionHasStepsAndCurrentHasNone() {
        for (v in 1 until Schema.VERSION) assertTrue("нет шагов обновления с версии $v", Schema.upgrade(v).isNotEmpty())
        assertTrue(Schema.upgrade(Schema.VERSION).isEmpty())
    }
}
