package dev.robotvision.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.robotvision.domain.Settings
import dev.robotvision.domain.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("robot_vision_settings")
class UserSettings(context: Context) : SettingsRepository {
    private val store = context.preferences
    private val threshold = floatPreferencesKey("confidence")
    private val haptic = booleanPreferencesKey("haptic")
    override val settings = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { Settings((it[threshold] ?: .5f).coerceIn(.3f, .9f), it[haptic] ?: true) }
    override suspend fun setConfidence(value: Float) { store.edit { it[threshold] = value.coerceIn(.3f, .9f) } }
    override suspend fun setHaptic(value: Boolean) { store.edit { it[haptic] = value } }
}
