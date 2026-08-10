package com.hooplog.app

enum class TrainingMode {
    Time,
    Reps
}

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
    val completed: Boolean,
    val completedAt: Long?
)

data class DaySummary(
    val date: String,
    val completed: Int,
    val total: Int
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
