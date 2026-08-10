package com.hooplog.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class TrainingStore(context: Context) : SQLiteOpenHelper(context, "hooplog.db", null, 6) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                tag TEXT NOT NULL DEFAULT '每日訓練',
                color_hex TEXT NOT NULL DEFAULT '#F4F1FF',
                priority INTEGER NOT NULL DEFAULT 3,
                mode TEXT NOT NULL DEFAULT 'time',
                duration_seconds INTEGER NOT NULL DEFAULT 600,
                reps_per_set INTEGER NOT NULL DEFAULT 10,
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
                tag TEXT NOT NULL DEFAULT '每日訓練',
                color_hex TEXT NOT NULL DEFAULT '#F4F1FF',
                priority INTEGER NOT NULL DEFAULT 3,
                mode TEXT NOT NULL DEFAULT 'time',
                duration_seconds INTEGER NOT NULL DEFAULT 600,
                reps_per_set INTEGER NOT NULL DEFAULT 10,
                sets INTEGER NOT NULL,
                rest_seconds INTEGER NOT NULL,
                completed_sets INTEGER NOT NULL DEFAULT 0,
                set_plans TEXT,
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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE items ADD COLUMN mode TEXT NOT NULL DEFAULT 'time'")
            db.execSQL("ALTER TABLE items ADD COLUMN reps_per_set INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN mode TEXT NOT NULL DEFAULT 'time'")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN reps_per_set INTEGER NOT NULL DEFAULT 10")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN completed_sets INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE daily_entries SET completed_sets = sets WHERE completed = 1")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN set_plans TEXT")
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE items ADD COLUMN tag TEXT NOT NULL DEFAULT '每日訓練'")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN tag TEXT NOT NULL DEFAULT '每日訓練'")
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE items ADD COLUMN color_hex TEXT NOT NULL DEFAULT '#F4F1FF'")
            db.execSQL("ALTER TABLE items ADD COLUMN priority INTEGER NOT NULL DEFAULT 3")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN color_hex TEXT NOT NULL DEFAULT '#F4F1FF'")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN priority INTEGER NOT NULL DEFAULT 3")
        }
    }

    fun activeItems(): List<TrainingItem> = readableDatabase.query(
        "items",
        arrayOf("id", "title", "tag", "color_hex", "priority", "mode", "duration_seconds", "reps_per_set", "sets", "rest_seconds", "active"),
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
                            tag = cursor.getString(2),
                            colorHex = cursor.getString(3),
                            priority = cursor.getInt(4),
                            mode = cursor.getString(5).toTrainingMode(),
                            durationSeconds = cursor.getInt(6),
                            repsPerSet = cursor.getInt(7),
                            sets = cursor.getInt(8),
                            restSeconds = cursor.getInt(9),
                            active = cursor.getInt(10) == 1
                    )
                )
            }
        }
    }

    fun saveItem(id: Long?, title: String, tag: String, colorHex: String, priority: Int, mode: TrainingMode, durationSeconds: Int, repsPerSet: Int, sets: Int, restSeconds: Int) {
        val values = ContentValues().apply {
            put("title", title.trim())
            put("tag", tag.cleanTag())
            put("color_hex", colorHex.cleanColorHex())
            put("priority", priority.coerceIn(1, 5))
            put("mode", mode.dbValue)
            put("duration_seconds", durationSeconds.coerceAtLeast(1))
            put("reps_per_set", repsPerSet.coerceAtLeast(1))
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
                    put("tag", tag.cleanTag())
                    put("color_hex", colorHex.cleanColorHex())
                    put("priority", priority.coerceIn(1, 5))
                    put("mode", mode.dbValue)
                    put("duration_seconds", durationSeconds.coerceAtLeast(1))
                    put("reps_per_set", repsPerSet.coerceAtLeast(1))
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
                put("tag", item.tag)
                put("color_hex", item.colorHex)
                put("priority", item.priority)
                put("mode", item.mode.dbValue)
                put("duration_seconds", item.durationSeconds)
                put("reps_per_set", item.repsPerSet)
                put("sets", item.sets)
                put("rest_seconds", item.restSeconds)
            }
            db.insertWithOnConflict("daily_entries", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun entriesFor(date: String, ensure: Boolean = true): List<DailyEntry> {
        if (ensure) ensureEntriesFor(date)
        return readableDatabase.query(
            "daily_entries",
            arrayOf("id", "date", "item_id", "title", "tag", "color_hex", "priority", "mode", "duration_seconds", "reps_per_set", "sets", "rest_seconds", "completed_sets", "set_plans", "completed", "completed_at"),
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
                            tag = cursor.getString(4),
                            colorHex = cursor.getString(5),
                            priority = cursor.getInt(6),
                            mode = cursor.getString(7).toTrainingMode(),
                            durationSeconds = cursor.getInt(8),
                            repsPerSet = cursor.getInt(9),
                            sets = cursor.getInt(10),
                            restSeconds = cursor.getInt(11),
                            completedSets = cursor.getInt(12),
                            setPlans = parseSetPlans(
                                json = if (cursor.isNull(13)) null else cursor.getString(13),
                                mode = cursor.getString(7).toTrainingMode(),
                                durationSeconds = cursor.getInt(8),
                                repsPerSet = cursor.getInt(9),
                                sets = cursor.getInt(10),
                                completedSets = cursor.getInt(12)
                            ),
                            completed = cursor.getInt(14) == 1,
                            completedAt = if (cursor.isNull(15)) null else cursor.getLong(15)
                        )
                    )
                }
            }
        }
    }

    fun toggleEntry(id: Long, completed: Boolean) {
        val row = readableDatabase.rawQuery(
            "SELECT mode, duration_seconds, reps_per_set, sets, set_plans FROM daily_entries WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val mode = cursor.getString(0).toTrainingMode()
                val duration = cursor.getInt(1)
                val reps = cursor.getInt(2)
                val sets = cursor.getInt(3)
                val json = if (cursor.isNull(4)) null else cursor.getString(4)
                Triple(sets, parseSetPlans(json, mode, duration, reps, sets, if (completed) sets else 0), mode)
            } else {
                Triple(0, emptyList(), TrainingMode.Time)
            }
        }
        val sets = row.first
        val plans = row.second.map { it.copy(completed = completed) }
        val values = ContentValues().apply {
            put("completed", if (completed) 1 else 0)
            put("completed_sets", if (completed) sets else 0)
            put("set_plans", plans.toJson())
            if (completed) put("completed_at", System.currentTimeMillis()) else putNull("completed_at")
        }
        writableDatabase.update("daily_entries", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateEntryPlan(
        id: Long,
        mode: TrainingMode,
        durationSeconds: Int,
        repsPerSet: Int,
        sets: Int,
        restSeconds: Int,
        completedSets: Int? = null,
        setPlans: List<TrainingSetPlan>? = null
    ) {
        val safeSets = sets.coerceAtLeast(1)
        val safePlans = setPlans?.normalizeSetPlans(mode, durationSeconds, repsPerSet, safeSets)
        val inferredCompletedSets = safePlans?.count { it.completed }
        val safeCompletedSets = (inferredCompletedSets ?: completedSets)?.coerceIn(0, safeSets)
        val values = ContentValues().apply {
            put("mode", mode.dbValue)
            put("duration_seconds", durationSeconds.coerceAtLeast(1))
            put("reps_per_set", repsPerSet.coerceAtLeast(1))
            put("sets", safeSets)
            put("rest_seconds", restSeconds.coerceAtLeast(0))
            safePlans?.let { put("set_plans", it.toJson()) }
            safeCompletedSets?.let {
                put("completed_sets", it)
                put("completed", if (it >= safeSets) 1 else 0)
                if (it >= safeSets) put("completed_at", System.currentTimeMillis()) else putNull("completed_at")
            }
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
            TrainingItem(0, "運球手感", "每日訓練", "#F4F1FF", 1, TrainingMode.Time, 600, 10, 4, 45),
            TrainingItem(0, "定點投籃", "每日訓練", "#EAF7EE", 1, TrainingMode.Reps, 60, 20, 5, 60),
            TrainingItem(0, "上籃腳步", "每周訓練", "#FFF3D8", 2, TrainingMode.Reps, 60, 12, 4, 45),
            TrainingItem(0, "罰球", "每日訓練", "#EAF3FF", 2, TrainingMode.Reps, 60, 10, 3, 30),
            TrainingItem(0, "核心與伸展", "特化訓練", "#FCECEC", 3, TrainingMode.Time, 600, 10, 3, 60)
        ).forEachIndexed { index, item ->
            db.insert("items", null, ContentValues().apply {
                put("title", item.title)
                put("tag", item.tag)
                put("color_hex", item.colorHex)
                put("priority", item.priority)
                put("mode", item.mode.dbValue)
                put("duration_seconds", item.durationSeconds)
                put("reps_per_set", item.repsPerSet)
                put("sets", item.sets)
                put("rest_seconds", item.restSeconds)
                put("active", 1)
                put("sort_order", index)
            })
        }
    }
}

private val TrainingMode.dbValue: String
    get() = when (this) {
        TrainingMode.Time -> "time"
        TrainingMode.Reps -> "reps"
    }

private fun String.toTrainingMode(): TrainingMode = when (this) {
    "reps" -> TrainingMode.Reps
    else -> TrainingMode.Time
}

private fun parseSetPlans(
    json: String?,
    mode: TrainingMode,
    durationSeconds: Int,
    repsPerSet: Int,
    sets: Int,
    completedSets: Int
): List<TrainingSetPlan> {
    if (json.isNullOrBlank()) {
        return List(sets.coerceAtLeast(1)) { index ->
            TrainingSetPlan(
                mode = mode,
                durationSeconds = durationSeconds.coerceAtLeast(1),
                reps = repsPerSet.coerceAtLeast(1),
                completed = index < completedSets
            )
        }
    }

    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            TrainingSetPlan(
                mode = item.optString("mode", mode.dbValue).toTrainingMode(),
                durationSeconds = item.optInt("durationSeconds", durationSeconds).coerceAtLeast(1),
                reps = item.optInt("reps", repsPerSet).coerceAtLeast(1),
                completed = item.optBoolean("completed", false)
            )
        }.normalizeSetPlans(mode, durationSeconds, repsPerSet, sets)
    }.getOrElse {
        List(sets.coerceAtLeast(1)) { index ->
            TrainingSetPlan(mode, durationSeconds.coerceAtLeast(1), repsPerSet.coerceAtLeast(1), index < completedSets)
        }
    }
}

private fun List<TrainingSetPlan>.normalizeSetPlans(
    mode: TrainingMode,
    durationSeconds: Int,
    repsPerSet: Int,
    sets: Int
): List<TrainingSetPlan> {
    val targetSize = sets.coerceAtLeast(1)
    val defaults = TrainingSetPlan(mode, durationSeconds.coerceAtLeast(1), repsPerSet.coerceAtLeast(1))
    return List(targetSize) { index ->
        getOrNull(index)?.let {
            it.copy(
                durationSeconds = it.durationSeconds.coerceAtLeast(1),
                reps = it.reps.coerceAtLeast(1)
            )
        } ?: defaults
    }
}

private fun List<TrainingSetPlan>.toJson(): String {
    val array = JSONArray()
    forEach { plan ->
        array.put(
            JSONObject()
                .put("mode", plan.mode.dbValue)
                .put("durationSeconds", plan.durationSeconds)
                .put("reps", plan.reps)
                .put("completed", plan.completed)
        )
    }
    return array.toString()
}

private fun String.cleanTag(): String = trim().ifBlank { "每日訓練" }

private fun String.cleanColorHex(): String {
    val value = trim()
    return if (Regex("^#[0-9A-Fa-f]{6}$").matches(value)) value.uppercase() else "#F4F1FF"
}
