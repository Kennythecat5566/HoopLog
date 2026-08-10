package com.hooplog.app

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadUpdateSettings(): UpdateSettings {
        val owner = prefs.getString("github_owner", "")?.ifBlank { UpdateDefaults.owner }
            ?: UpdateDefaults.owner
        val repo = prefs.getString("github_repo", "")?.ifBlank { UpdateDefaults.repo }
            ?: UpdateDefaults.repo
        return UpdateSettings(owner = owner, repo = repo)
    }

    fun saveUpdateSettings(settings: UpdateSettings) {
        prefs.edit()
            .putString("github_owner", settings.owner.trim())
            .putString("github_repo", settings.repo.trim())
            .apply()
    }
}
