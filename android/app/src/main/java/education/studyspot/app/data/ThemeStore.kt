package education.studyspot.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** On-device appearance preferences (mirrors ios/StudySpot/State/ThemeStore.swift). */
data class ThemeSettings(
    /** Used only when [syncWithDevice] is off. */
    val dark: Boolean = false,
    /** Follow the system light/dark setting (default). */
    val syncWithDevice: Boolean = true,
    /** Motion and transitions (respect this in custom animations). */
    val animations: Boolean = true,
) {
    /** null = follow the system. */
    val forcedDark: Boolean? get() = if (syncWithDevice) null else dark
}

class ThemeStore(private val context: Context, private val scope: CoroutineScope) {
    val settings: StateFlow<ThemeSettings> = context.settingsDataStore.data
        .map { p ->
            ThemeSettings(
                dark = p[DARK] ?: false,
                syncWithDevice = p[SYNC] ?: true,
                animations = p[ANIMATIONS] ?: true,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, ThemeSettings())

    /** Manually picking a mode turns off device sync, as on the web and iOS. */
    fun setDarkMode(on: Boolean) = edit { it[SYNC] = false; it[DARK] = on }

    fun setSyncWithDevice(on: Boolean) = edit { it[SYNC] = on }

    fun setAnimations(on: Boolean) = edit { it[ANIMATIONS] = on }

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { context.settingsDataStore.edit(block) }
    }

    private companion object {
        val DARK = booleanPreferencesKey("cf-theme-dark")
        val SYNC = booleanPreferencesKey("cf-theme-sync")
        val ANIMATIONS = booleanPreferencesKey("cf-animations")
    }
}
