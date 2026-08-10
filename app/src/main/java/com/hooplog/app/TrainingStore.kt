package com.hooplog.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

class TrainingStore(context: Context) : SQLiteOpenHelper(context, "hooplog.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL DEFAULT 600,
                sets INTEGER NOT NULL,
                rest_seconds INTEGER NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                sort_order INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE daily_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                item_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                duration_seconds INTEGER NOT NULL DEFAULT 600,
                sets INTEGER NOT NULL,
                rest_seconds INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                UNIQUE(date, item_id)
            )
            """.trimIndent()
        )

        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE items ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 600")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 600")
        }
    }

    fun activeItems(): List<TrainingItem> = readableDatabase.query(
        "items",
        arrayOf("id", "title", "duration_seconds", "sets", "rest_seconds", "active"),
        "active = 1",
        null,
        null,
        null,
        "sort_order ASC, id ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TrainingItem(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        durationSeconds = cursor.getInt(2),
                        sets = cursor.getInt(3),
                        restSeconds = cursor.getInt(4),
                        active = cursor.getInt(5) == 1
                    )
                )
            }
        }
    }

    fun saveItem(id: Long?, title: String, durationSeconds: Int, sets: Int, restSeconds: Int) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("duration_seconds", durationSeconds.coerceAtLeast(1))
            put("sets", sets.coerceAtLeast(1))
            put("rest_seconds", restSeconds.coerceAtLeast(0))
            put("active", 1)
        }
        if (id == null) {
            val order = activeItems().size + 1
            values.put("sort_order", order)
            writableDatabase.insert("items", null, values)
        } else {
            writableDatabase.update("items", values, "id = ?", arrayOf(id.toString()))
            writableDatabase.update(
                "daily_entries",
                ContentValues().apply {
                    put("title", title.trim())
                    put("duration_seconds", durationSeconds.coerceAtLeast(1))
                    put("sets", sets.coerceAtLeast(1))
                    put("rest_seconds", restSeconds.coerceAtLeast(0))
                },
                "date = ? AND item_id = ?",
                arrayOf(LocalDate.now().toString(), id.toString())
            )
        }
    }

    fun archiveItem(id: Long) {
        val values = ContentValues().apply { put("active", 0) }
        writableDatabase.update("items", values, "id = ?", arrayOf(id.toString()))
        writableDatabase.delete(
            "daily_entries",
            "date = ? AND item_id = ?",
            arrayOf(LocalDate.now().toString(), id.toString())
        )
    }

    fun ensureEntriesFor(date: String = LocalDate.now().toString()) {
        val db = writableDatabase
        activeItems().forEach { item ->
            val values = ContentValues().apply {
                put("date", date)
                put("item_id", item.id)
                put("title", item.title)
                put("duration_seconds", item.durationSeconds)
                put("sets", item.sets)
                put("rest_seconds", item.restSeconds)
            }
            db.insertWithOnConflict("daily_entries", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun entriesFor(date: String): List<DailyEntry> {
        ensureEntriesFor(date)
        return readableDatabase.query(
            "daily_entries",
            arrayOf("id", "date", "item_id", "title", "duration_seconds", "sets", "rest_seconds", "completed", "completed_at"),
            "date = ?",
            arrayOf(date),
            null,
            null,
            "id ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DailyEntry(
                            id = cursor.getLong(0),
                            date = cursor.getString(1),
                            itemId = cursor.getLong(2),
                            title = cursor.getString(3),
                            durationSeconds = cursor.getInt(4),
                            sets = cursor.getInt(5),
                            restSeconds = cursor.getInt(6),
                            completed = cursor.getInt(7) == 1,
                            completedAt = if (cursor.isNull(8)) null else cursor.getLong(8)
                        )
                    )
                }
            }
        }
    }

    fun toggleEntry(id: Long, completed: Boolean) {
        val values = ContentValues().apply {
            put("completed", if (completed) 1 else 0)
            if (completed) put("completed_at", System.currentTimeMillis()) else putNull("completed_at")
        }
        writableDatabase.update("daily_entries", values, "id = ?", arrayOf(id.toString()))
    }

    fun summaries(): List<DaySummary> = readableDatabase.rawQuery(
        """
        SELECT date, SUM(completed), COUNT(*)
        FROM daily_entries
        GROUP BY date
        ORDER BY date DESC
        """.trimIndent(),
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(DaySummary(cursor.getString(0), cursor.getInt(1), cursor.getInt(2)))
            }
        }
    }

    private fun seed(db: SQLiteDatabase) {
        listOf(
            TrainingItem(0, "運球手感", 600, 4, 45),
            TrainingItem(0, "定點投籃", 900, 5, 60),
            TrainingItem(0, "上籃腳步", 600, 4, 45),
            TrainingItem(0, "罰球", 480, 3, 30),
            TrainingItem(0, "核心與伸展", 600, 3, 60)
        ).forEachIndexed { index, item ->
            db.insert("items", null, ContentValues().apply {
                put("title", item.title)
                put("duration_seconds", item.durationSeconds)
                put("sets", item.sets)
                put("rest_seconds", item.restSeconds)
                put("active", 1)
                put("sort_order", index)
            })
        }
    }
}
