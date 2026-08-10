package com.hooplog.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class TrainingStore(context: Context) : SQLiteOpenHelper(context, "hooplog.db", null, 8) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE tags (
                name TEXT PRIMARY KEY,
                color_hex TEXT NOT NULL DEFAULT '#F4F1FF',
                priority INTEGER NOT NULL DEFAULT 3,
                schedule TEXT NOT NULL DEFAULT 'manual',
                weekly_day INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
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
                comment TEXT NOT NULL DEFAULT '',
                video_url TEXT NOT NULL DEFAULT '',
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
                comment TEXT NOT NULL DEFAULT '',
                video_url TEXT NOT NULL DEFAULT '',
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                UNIQUE(date, item_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE day_sessions (
                date TEXT PRIMARY KEY,
                started_at INTEGER,
                ended_at INTEGER,
                duration_seconds INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        seedTags(db)
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
        if (oldVersion < 7) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tags (
                    name TEXT PRIMARY KEY,
                    color_hex TEXT NOT NULL DEFAULT '#F4F1FF',
                    priority INTEGER NOT NULL DEFAULT 3,
                    schedule TEXT NOT NULL DEFAULT 'manual',
                    weekly_day INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
            seedTags(db)
            db.rawQuery("SELECT DISTINCT tag, color_hex, priority FROM items", null).use { cursor ->
                while (cursor.moveToNext()) {
                    db.insertWithOnConflict("tags", null, ContentValues().apply {
                        put("name", cursor.getString(0).cleanTag())
                        put("color_hex", cursor.getString(1).cleanColorHex())
                        put("priority", cursor.getInt(2).coerceIn(1, 5))
                        put("schedule", inferSchedule(cursor.getString(0)).dbValue)
                        put("weekly_day", 1)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }
        }
        if (oldVersion < 8) {
            db.execSQL("ALTER TABLE items ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE items ADD COLUMN video_url TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE daily_entries ADD COLUMN video_url TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS day_sessions (
                    date TEXT PRIMARY KEY,
                    started_at INTEGER,
                    ended_at INTEGER,
                    duration_seconds INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
    }

    fun activeItems(): List<TrainingItem> = readableDatabase.query(
        "items",
        arrayOf("id", "title", "tag", "color_hex", "priority", "mode", "duration_seconds", "reps_per_set", "sets", "rest_seconds", "comment", "video_url", "active"),
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
                            comment = cursor.getString(10),
                            videoUrl = cursor.getString(11),
                            active = cursor.getInt(12) == 1
                    )
                )
            }
        }
    }

    fun tags(): List<TrainingTag> = readableDatabase.query(
        "tags",
        arrayOf("name", "color_hex", "priority", "schedule", "weekly_day"),
        null,
        null,
        null,
        null,
        "priority ASC, name ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TrainingTag(
                        name = cursor.getString(0),
                        colorHex = cursor.getString(1),
                        priority = cursor.getInt(2),
                        schedule = cursor.getString(3).toTagSchedule(),
                        weeklyDay = cursor.getInt(4).coerceIn(1, 7)
                    )
                )
            }
        }
    }

    fun saveTag(originalName: String?, tag: TrainingTag) {
        val cleanName = tag.name.cleanTag()
        writableDatabase.insertWithOnConflict("tags", null, ContentValues().apply {
            put("name", cleanName)
            put("color_hex", tag.colorHex.cleanColorHex())
            put("priority", tag.priority.coerceIn(1, 5))
            put("schedule", tag.schedule.dbValue)
            put("weekly_day", tag.weeklyDay.coerceIn(1, 7))
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (originalName != null && originalName != cleanName) {
            writableDatabase.update("items", ContentValues().apply { put("tag", cleanName) }, "tag = ?", arrayOf(originalName))
            writableDatabase.update("daily_entries", ContentValues().apply { put("tag", cleanName) }, "tag = ?", arrayOf(originalName))
            writableDatabase.delete("tags", "name = ?", arrayOf(originalName))
        }
    }

    fun deleteTag(name: String) {
        writableDatabase.update("items", ContentValues().apply { put("tag", "手動訓練") }, "tag = ?", arrayOf(name))
        writableDatabase.update("daily_entries", ContentValues().apply { put("tag", "手動訓練") }, "tag = ?", arrayOf(name))
        writableDatabase.delete("tags", "name = ?", arrayOf(name))
    }

    fun saveItem(id: Long?, title: String, tag: String, colorHex: String, priority: Int, mode: TrainingMode, durationSeconds: Int, repsPerSet: Int, sets: Int, restSeconds: Int, comment: String, videoUrl: String) {
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
            put("comment", comment.trim())
            put("video_url", videoUrl.trim())
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
                    put("comment", comment.trim())
                    put("video_url", videoUrl.trim())
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
        val targetDate = LocalDate.parse(date)
        val tagRules = tags().associateBy { it.name }
        activeItems().forEach { item ->
            val tag = tagRules[item.tag] ?: TrainingTag(item.tag, item.colorHex, item.priority, inferSchedule(item.tag))
            if (!tag.shouldAppearOn(targetDate)) return@forEach
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
                put("comment", item.comment)
                put("video_url", item.videoUrl)
            }
            db.insertWithOnConflict("daily_entries", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    fun entriesFor(date: String, ensure: Boolean = true): List<DailyEntry> {
        if (ensure) ensureEntriesFor(date)
        return readableDatabase.query(
            "daily_entries",
            arrayOf("id", "date", "item_id", "title", "tag", "color_hex", "priority", "mode", "duration_seconds", "reps_per_set", "sets", "rest_seconds", "completed_sets", "set_plans", "comment", "video_url", "completed", "completed_at"),
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
                            comment = cursor.getString(14),
                            videoUrl = cursor.getString(15),
                            completed = cursor.getInt(16) == 1,
                            completedAt = if (cursor.isNull(17)) null else cursor.getLong(17)
                        )
                    )
                }
            }
        }
    }

    fun historyEntriesFor(date: String): List<DailyEntry> =
        entriesFor(date, ensure = false).filter { it.completed || it.completedSets > 0 }

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
        entryDate(id)?.let { markSessionCompleteIfDone(it) }
    }

    fun startSession(date: String = LocalDate.now().toString()) {
        writableDatabase.insertWithOnConflict("day_sessions", null, ContentValues().apply {
            put("date", date)
            put("started_at", System.currentTimeMillis())
            putNull("ended_at")
            put("duration_seconds", 0)
        }, SQLiteDatabase.CONFLICT_IGNORE)
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
        entryDate(id)?.let { markSessionCompleteIfDone(it) }
    }

    fun sessionFor(date: String): DaySession =
        readableDatabase.query(
            "day_sessions",
            arrayOf("date", "started_at", "ended_at", "duration_seconds"),
            "date = ?",
            arrayOf(date),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                DaySession(
                    date = cursor.getString(0),
                    startedAt = if (cursor.isNull(1)) null else cursor.getLong(1),
                    endedAt = if (cursor.isNull(2)) null else cursor.getLong(2),
                    durationSeconds = cursor.getInt(3)
                )
            } else {
                DaySession(date, null, null, 0)
            }
        }

    private fun entryDate(id: Long): String? =
        readableDatabase.rawQuery("SELECT date FROM daily_entries WHERE id = ?", arrayOf(id.toString())).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun markSessionCompleteIfDone(date: String) {
        val counts = readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(completed), 0) FROM daily_entries WHERE date = ?",
            arrayOf(date)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) to cursor.getInt(1) else 0 to 0
        }
        if (counts.first == 0 || counts.first != counts.second) return
        val session = sessionFor(date)
        if (session.startedAt == null || session.endedAt != null) return
        val ended = System.currentTimeMillis()
        writableDatabase.update("day_sessions", ContentValues().apply {
            put("ended_at", ended)
            put("duration_seconds", ((ended - session.startedAt) / 1000L).toInt().coerceAtLeast(0))
        }, "date = ?", arrayOf(date))
    }

    fun summaries(): List<DaySummary> = readableDatabase.rawQuery(
        """
        SELECT d.date, SUM(d.completed), COUNT(*), COALESCE(s.duration_seconds, 0)
        FROM daily_entries d
        LEFT JOIN day_sessions s ON s.date = d.date
        WHERE d.completed = 1 OR d.completed_sets > 0
        GROUP BY d.date, s.duration_seconds
        ORDER BY d.date DESC
        """.trimIndent(),
        null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(DaySummary(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3)))
            }
        }
    }

    private fun seedTags(db: SQLiteDatabase) {
        listOf(
            TrainingTag("每日訓練", "#F4F1FF", 1, TagSchedule.Daily, 1),
            TrainingTag("每周訓練", "#FFF3D8", 2, TagSchedule.Weekly, 1),
            TrainingTag("特化訓練", "#FCECEC", 3, TagSchedule.Manual, 1),
            TrainingTag("手動訓練", "#F2F2F2", 5, TagSchedule.Manual, 1)
        ).forEach { tag ->
            db.insertWithOnConflict("tags", null, ContentValues().apply {
                put("name", tag.name)
                put("color_hex", tag.colorHex)
                put("priority", tag.priority)
                put("schedule", tag.schedule.dbValue)
                put("weekly_day", tag.weeklyDay)
            }, SQLiteDatabase.CONFLICT_IGNORE)
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

private val TagSchedule.dbValue: String
    get() = when (this) {
        TagSchedule.Daily -> "daily"
        TagSchedule.Weekly -> "weekly"
        TagSchedule.Manual -> "manual"
    }

private fun String.toTagSchedule(): TagSchedule = when (this) {
    "daily" -> TagSchedule.Daily
    "weekly" -> TagSchedule.Weekly
    else -> TagSchedule.Manual
}

private fun inferSchedule(tag: String): TagSchedule = when {
    tag.contains("每日") -> TagSchedule.Daily
    tag.contains("每周") || tag.contains("每週") -> TagSchedule.Weekly
    else -> TagSchedule.Manual
}

private fun TrainingTag.shouldAppearOn(date: LocalDate): Boolean = when (schedule) {
    TagSchedule.Daily -> true
    TagSchedule.Weekly -> date.dayOfWeek.value == weeklyDay.coerceIn(1, 7)
    TagSchedule.Manual -> false
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
