package com.momentum.companion.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "momentum_companion_prefs",
        masterKey,
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

    var stepsPerMin: Int
        get() = prefs.getInt(KEY_STEPS_PER_MIN, DEFAULT_STEPS_PER_MIN)
        set(value) = prefs.edit().putInt(KEY_STEPS_PER_MIN, value).apply()

    var weightKg: Float
        get() = prefs.getFloat(KEY_WEIGHT_KG, DEFAULT_WEIGHT_KG)
        set(value) = prefs.edit().putFloat(KEY_WEIGHT_KG, value).apply()

    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT_CM, DEFAULT_HEIGHT_CM)
        set(value) = prefs.edit().putInt(KEY_HEIGHT_CM, value).apply()

    var age: Int
        get() = prefs.getInt(KEY_AGE, DEFAULT_AGE)
        set(value) = prefs.edit().putInt(KEY_AGE, value).apply()

    var isMale: Boolean
        get() = prefs.getBoolean(KEY_IS_MALE, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_MALE, value).apply()

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
        private const val KEY_STEPS_PER_MIN = "steps_per_min"
        private const val DEFAULT_STEPS_PER_MIN = 100
        private const val KEY_WEIGHT_KG = "weight_kg"
        private const val DEFAULT_WEIGHT_KG = 70f
        private const val KEY_HEIGHT_CM = "height_cm"
        private const val DEFAULT_HEIGHT_CM = 170
        private const val KEY_AGE = "age"
        private const val DEFAULT_AGE = 30
        private const val KEY_IS_MALE = "is_male"
    }
}
