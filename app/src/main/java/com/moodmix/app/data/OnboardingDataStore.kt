package com.moodmix.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moodmix_settings")

sealed interface OnboardingState {
    object Loading : OnboardingState
    object Completed : OnboardingState
    object NotCompleted : OnboardingState
}

class OnboardingDataStore(private val context: Context) {

    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val onboardingState: Flow<OnboardingState> = context.dataStore.data
        .map { preferences ->
            val completed = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            if (completed) OnboardingState.Completed else OnboardingState.NotCompleted
        }
        .catch {
            emit(OnboardingState.NotCompleted)
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
}
