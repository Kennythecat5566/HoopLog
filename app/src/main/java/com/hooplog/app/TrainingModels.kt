package com.hooplog.app

data class TrainingItem(
    val id: Long,
    val title: String,
    val durationSeconds: Int,
    val sets: Int,
    val restSeconds: Int,
    val active: Boolean = true
)

data class DailyEntry(
    val id: Long,
    val date: String,
    val itemId: Long,
    val title: String,
    val durationSeconds: Int,
    val sets: Int,
    val restSeconds: Int,
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

data class UpdateInfo(
    val latestVersion: String,
    val releaseUrl: String,
    val isNewer: Boolean
)
