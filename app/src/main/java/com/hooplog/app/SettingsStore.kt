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

    fun loadUiSettings(): UiSettings = UiSettings(
        primaryColorHex = prefs.getString("ui_primary", "#111111") ?: "#111111",
        surfaceColorHex = prefs.getString("ui_surface", "#FFFFFF") ?: "#FFFFFF",
        cardRadius = prefs.getInt("ui_card_radius", 8),
        fontScale = prefs.getFloat("ui_font_scale", 1.0f),
        densityScale = prefs.getFloat("ui_density_scale", 1.0f),
        style = prefs.getString("ui_style", "Minimal") ?: "Minimal"
    )

    fun saveUiSettings(settings: UiSettings) {
        prefs.edit()
            .putString("ui_primary", settings.primaryColorHex)
            .putString("ui_surface", settings.surfaceColorHex)
            .putInt("ui_card_radius", settings.cardRadius.coerceIn(0, 16))
            .putFloat("ui_font_scale", settings.fontScale.coerceIn(0.85f, 1.25f))
            .putFloat("ui_density_scale", settings.densityScale.coerceIn(0.85f, 1.2f))
            .putString("ui_style", settings.style)
            .apply()
    }
}
