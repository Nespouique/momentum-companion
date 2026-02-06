package com.momentum.companion.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "momentum_companion_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER_URL, null)
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var jwtToken: String?
        get() = prefs.getString(KEY_JWT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_JWT_TOKEN, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var password: String?
        get() = prefs.getString(KEY_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var allowSelfSignedCerts: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_SELF_SIGNED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALLOW_SELF_SIGNED, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var syncFrequencyMinutes: Int
        get() = prefs.getInt(KEY_SYNC_FREQUENCY, DEFAULT_SYNC_FREQUENCY)
        set(value) = prefs.edit().putInt(KEY_SYNC_FREQUENCY, value).apply()

    val isConfigured: Boolean
        get() = serverUrl != null && jwtToken != null

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ALLOW_SELF_SIGNED = "allow_self_signed"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_SYNC_FREQUENCY = "sync_frequency_minutes"
        private const val DEFAULT_SYNC_FREQUENCY = 15
    }
}
