package com.danza.noticontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "noti_control_prefs")

object PrefsKeys {
    val BLOCK_MODE = stringPreferencesKey("block_mode")           // "all" or "selected"
    val BLOCKED_PACKAGES = stringPreferencesKey("blocked_pkgs")   // comma-separated
    val DURATION_TYPE = stringPreferencesKey("duration_type")     // "preset" or "custom"
    val PRESET_MINUTES = intPreferencesKey("preset_minutes")      // 30, 60, 120
    val CUSTOM_FROM_HOUR = intPreferencesKey("custom_from_hour")
    val CUSTOM_FROM_MIN = intPreferencesKey("custom_from_min")
    val CUSTOM_TO_HOUR = intPreferencesKey("custom_to_hour")
    val CUSTOM_TO_MIN = intPreferencesKey("custom_to_min")
    val HIGH_IMPORTANCE_PKGS = stringPreferencesKey("high_importance_pkgs")
}

class PrefsStore(private val context: Context) {

    val blockMode: Flow<String> = context.dataStore.data.map { it[PrefsKeys.BLOCK_MODE] ?: "all" }
    val blockedPackages: Flow<Set<String>> = context.dataStore.data.map {
        val raw = it[PrefsKeys.BLOCKED_PACKAGES] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }
    val durationType: Flow<String> = context.dataStore.data.map { it[PrefsKeys.DURATION_TYPE] ?: "preset" }
    val presetMinutes: Flow<Int> = context.dataStore.data.map { it[PrefsKeys.PRESET_MINUTES] ?: 60 }
    val customFromHour: Flow<Int> = context.dataStore.data.map { it[PrefsKeys.CUSTOM_FROM_HOUR] ?: 22 }
    val customFromMin: Flow<Int> = context.dataStore.data.map { it[PrefsKeys.CUSTOM_FROM_MIN] ?: 0 }
    val customToHour: Flow<Int> = context.dataStore.data.map { it[PrefsKeys.CUSTOM_TO_HOUR] ?: 7 }
    val customToMin: Flow<Int> = context.dataStore.data.map { it[PrefsKeys.CUSTOM_TO_MIN] ?: 0 }
    val highImportancePackages: Flow<Set<String>> = context.dataStore.data.map {
        val raw = it[PrefsKeys.HIGH_IMPORTANCE_PKGS] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    suspend fun saveBlockMode(mode: String) {
        context.dataStore.edit { it[PrefsKeys.BLOCK_MODE] = mode }
    }

    suspend fun saveBlockedPackages(packages: Set<String>) {
        context.dataStore.edit { it[PrefsKeys.BLOCKED_PACKAGES] = packages.joinToString(",") }
    }

    suspend fun saveDurationPreset(minutes: Int) {
        context.dataStore.edit {
            it[PrefsKeys.DURATION_TYPE] = "preset"
            it[PrefsKeys.PRESET_MINUTES] = minutes
        }
    }

    suspend fun saveHighImportancePackages(packages: Set<String>) {
        context.dataStore.edit { it[PrefsKeys.HIGH_IMPORTANCE_PKGS] = packages.joinToString(",") }
    }

    suspend fun saveDurationCustom(fromHour: Int, fromMin: Int, toHour: Int, toMin: Int) {
        context.dataStore.edit {
            it[PrefsKeys.DURATION_TYPE] = "custom"
            it[PrefsKeys.CUSTOM_FROM_HOUR] = fromHour
            it[PrefsKeys.CUSTOM_FROM_MIN] = fromMin
            it[PrefsKeys.CUSTOM_TO_HOUR] = toHour
            it[PrefsKeys.CUSTOM_TO_MIN] = toMin
        }
    }
}
