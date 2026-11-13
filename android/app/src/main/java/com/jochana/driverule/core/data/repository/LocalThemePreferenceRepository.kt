package com.jochana.driverule.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jochana.driverule.core.model.ThemeMode
import com.jochana.driverule.domain.repository.ThemePreferenceRepository
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance")

class LocalThemePreferenceRepository(context: Context) : ThemePreferenceRepository {
    private val store = context.applicationContext.appearanceDataStore
    private val key = stringPreferencesKey("theme_mode")

    override val themeMode = store.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[key] } ?: ThemeMode.System
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[key] = mode.name }
    }
}
