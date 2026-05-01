package com.fbint.collector.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fbint.collector.ui.nav.OnboardingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for device-level config: base URL, API key, environmentId, project name,
 * surveyor identity. Persisted in EncryptedSharedPreferences so the API key is at rest under the
 * Android Keystore.
 *
 * The admin and surveyor flows write disjoint subsets of these keys, so we don't need atomic txns.
 */
@Singleton
class ConfigRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            "fbint_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun baseUrl(): String? = prefs.getString(KEY_BASE_URL, null)
    fun apiKey(): String? = prefs.getString(KEY_API_KEY, null)
    fun environmentId(): String? = prefs.getString(KEY_ENV_ID, null)
    fun projectName(): String? = prefs.getString(KEY_PROJECT_NAME, null)
    fun surveyorId(): String? = prefs.getString(KEY_SURVEYOR_ID, null)

    fun saveServerConfig(baseUrl: String, apiKey: String, environmentId: String, projectName: String?) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_ENV_ID, environmentId.trim())
            .putString(KEY_PROJECT_NAME, projectName)
            .apply()
    }

    fun saveSurveyorId(id: String) {
        prefs.edit().putString(KEY_SURVEYOR_ID, id.trim()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun observeOnboardingState(): Flow<OnboardingState> = observePrefs().distinctUntilChanged()

    private fun observePrefs(): Flow<OnboardingState> = callbackFlow {
        fun snapshot(): OnboardingState = when {
            apiKey().isNullOrBlank() || environmentId().isNullOrBlank() || baseUrl().isNullOrBlank() ->
                OnboardingState.NoConfig
            surveyorId().isNullOrBlank() -> OnboardingState.NoSurveyor
            else -> OnboardingState.Ready
        }
        trySend(snapshot())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_ENV_ID = "env_id"
        const val KEY_PROJECT_NAME = "project_name"
        const val KEY_SURVEYOR_ID = "surveyor_id"
    }
}
