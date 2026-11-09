package com.moodmix.app.data.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moodmix.app.domain.model.AppRegion
import com.moodmix.app.domain.model.RegionalThemeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "moodmix_theme_settings")

class RegionalThemePreferences(
    private val context: Context? = null,
    private val customDataStore: DataStore<Preferences>? = null
) {
    private val dataStore: DataStore<Preferences>
        get() = customDataStore ?: requireNotNull(context).themeDataStore

    companion object {
        val KEY_AUTO_THEME_ENABLED = booleanPreferencesKey("auto_theme_enabled")
        val KEY_SELECTED_REGION = stringPreferencesKey("selected_region")

        @Volatile
        private var INSTANCE: RegionalThemePreferences? = null

        fun getInstance(context: Context): RegionalThemePreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RegionalThemePreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val regionalThemeState: Flow<RegionalThemeState> = dataStore.data
        .map { preferences ->
            val autoEnabled = preferences[KEY_AUTO_THEME_ENABLED] ?: true
            val regionStr = preferences[KEY_SELECTED_REGION] ?: AppRegion.AUTOMATIC.name
            val selectedRegion = try {
                AppRegion.valueOf(regionStr)
            } catch (_: Exception) {
                AppRegion.AUTOMATIC
            }

            val countryCode = Locale.getDefault().country
            val occasion = OccasionProvider.getActiveOccasion(
                countryCode = countryCode,
                calendar = Calendar.getInstance(),
                isAutoThemeEnabled = autoEnabled,
                selectedRegion = selectedRegion
            )

            RegionalThemeState(
                isAutoThemeEnabled = autoEnabled,
                selectedRegion = selectedRegion,
                activeRegionCode = countryCode,
                activeOccasion = occasion
            )
        }
        .catch {
            emit(RegionalThemeState())
        }

    suspend fun setAutoThemeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_AUTO_THEME_ENABLED] = enabled
        }
    }

    suspend fun setSelectedRegion(region: AppRegion) {
        dataStore.edit { preferences ->
            preferences[KEY_SELECTED_REGION] = region.name
        }
    }
}
