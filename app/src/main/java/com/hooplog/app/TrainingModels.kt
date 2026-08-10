package com.hooplog.app

enum class TrainingMode {
    Time,
    Reps
}

enum class TagSchedule {
    Daily,
    Weekly,
    Manual
}

data class TrainingTag(
    val name: String,
    val colorHex: String,
    val priority: Int,
    val schedule: TagSchedule,
    val weeklyDay: Int = 1
)

data class TrainingItem(
    val id: Long,
    val title: String,
    val tag: String,
    val colorHex: String,
    val priority: Int,
    val mode: TrainingMode,
    val durationSeconds: Int,
    val repsPerSet: Int,
    val sets: Int,
    val restSeconds: Int,
    val comment: String = "",
    val videoUrl: String = "",
    val active: Boolean = true
)

data class TrainingSetPlan(
    val mode: TrainingMode,
    val durationSeconds: Int,
    val reps: Int,
    val completed: Boolean = false
)

data class DailyEntry(
    val id: Long,
    val date: String,
    val itemId: Long,
    val title: String,
    val tag: String,
    val colorHex: String,
    val priority: Int,
    val mode: TrainingMode,
    val durationSeconds: Int,
    val repsPerSet: Int,
    val sets: Int,
    val restSeconds: Int,
    val completedSets: Int,
    val setPlans: List<TrainingSetPlan>,
    val comment: String,
    val videoUrl: String,
    val completed: Boolean,
    val completedAt: Long?
)

data class DaySummary(
    val date: String,
    val completed: Int,
    val total: Int,
    val durationSeconds: Int = 0
)

data class DaySession(
    val date: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val durationSeconds: Int
)

data class UpdateSettings(
    val owner: String = "",
    val repo: String = ""
)

object UpdateDefaults {
    const val owner = "Kennythecat5566"
    const val repo = "HoopLog"
}

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val isNewer: Boolean
)

data class UiSettings(
    val primaryColorHex: String = "#111111",
    val surfaceColorHex: String = "#FFFFFF",
    val cardRadius: Int = 8,
    val fontScale: Float = 1.0f,
    val densityScale: Float = 1.0f,
    val style: String = "Minimal"
)
