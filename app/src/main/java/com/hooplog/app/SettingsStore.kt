package com.hooplog.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadUpdateSettings(): UpdateSettings = UpdateSettings(
        owner = prefs.getString("github_owner", "") ?: "",
        repo = prefs.getString("github_repo", "") ?: ""
    )

    fun saveUpdateSettings(settings: UpdateSettings) {
        prefs.edit()
            .putString("github_owner", settings.owner.trim())
            .putString("github_repo", settings.repo.trim())
            .apply()
    }
}
