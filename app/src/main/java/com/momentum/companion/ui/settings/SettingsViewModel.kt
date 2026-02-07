package com.momentum.companion.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.HealthSyncRequest
import com.momentum.companion.data.healthconnect.HealthConnectMapper
import com.momentum.companion.data.healthconnect.HealthConnectReader
import com.momentum.companion.data.healthconnect.UserProfile
import com.momentum.companion.data.log.SyncLogEntry
import com.momentum.companion.data.log.SyncLogRepository
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class SettingsUiState(
    val serverUrl: String = "",
    val email: String = "",
    val syncFrequencyMinutes: Int = 15,
    val stepsPerMin: Int = 100,
    val weightKg: Float = 70f,
    val heightCm: Int = 170,
    val age: Int = 30,
    val isMale: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val importDaysCompleted: Int = 0,
    val error: String? = null,
)

data class SyncFrequencyOption(
    val label: String,
    val minutes: Int,
)

val SYNC_FREQUENCY_OPTIONS = listOf(
    SyncFrequencyOption("15 minutes", 15),
    SyncFrequencyOption("30 minutes", 30),
    SyncFrequencyOption("1 heure", 60),
    SyncFrequencyOption("2 heures", 120),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
    private val healthConnectReader: HealthConnectReader?,
    private val apiService: MomentumApiService,
    private val syncLogRepository: SyncLogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _logs = MutableStateFlow<List<SyncLogEntry>>(emptyList())
    val logs: StateFlow<List<SyncLogEntry>> = _logs.asStateFlow()

    init {
        loadSettings()
        loadLogs()
    }

    private fun loadSettings() {
        _uiState.value = _uiState.value.copy(
            serverUrl = preferences.serverUrl ?: "",
            email = preferences.email ?: "",
            syncFrequencyMinutes = preferences.syncFrequencyMinutes,
            stepsPerMin = preferences.stepsPerMin,
            weightKg = preferences.weightKg,
            heightCm = preferences.heightCm,
            age = preferences.age,
            isMale = preferences.isMale,
        )
    }

    fun loadLogs() {
        viewModelScope.launch {
            _logs.value = syncLogRepository.getRecentLogs()
        }
    }

    fun updateSyncFrequency(minutes: Int) {
        viewModelScope.launch {
            preferences.syncFrequencyMinutes = minutes
            syncScheduler.schedulePeriodic(minutes)
            _uiState.value = _uiState.value.copy(syncFrequencyMinutes = minutes)
        }
    }

    fun updateStepsPerMin(value: Int) {
        preferences.stepsPerMin = value
        _uiState.value = _uiState.value.copy(stepsPerMin = value)
    }

    fun updateWeightKg(value: Float) {
        preferences.weightKg = value
        _uiState.value = _uiState.value.copy(weightKg = value)
    }

    fun updateHeightCm(value: Int) {
        preferences.heightCm = value
        _uiState.value = _uiState.value.copy(heightCm = value)
    }

    fun updateAge(value: Int) {
        preferences.age = value
        _uiState.value = _uiState.value.copy(age = value)
    }

    fun updateIsMale(value: Boolean) {
        preferences.isMale = value
        _uiState.value = _uiState.value.copy(isMale = value)
    }

    fun startInitialImport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImporting = true,
                importProgress = 0f,
                error = null,
            )

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)

            val reader = healthConnectReader ?: run {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = "Health Connect non disponible",
                )
                return@launch
            }

            try {
                _uiState.value = _uiState.value.copy(importProgress = 0.1f)

                val steps = reader.readSteps(startDate, endDate)
                _uiState.value = _uiState.value.copy(importProgress = 0.3f)

                val exercises = reader.readExerciseSessions(startDate, endDate)
                _uiState.value = _uiState.value.copy(importProgress = 0.5f)

                val exerciseCalories = reader.readTotalCaloriesBurned(startDate, endDate)
                _uiState.value = _uiState.value.copy(importProgress = 0.6f)

                val sleep = reader.readSleepSessions(startDate, endDate)
                _uiState.value = _uiState.value.copy(importProgress = 0.7f)

                val userProfile = UserProfile(
                    stepsPerMin = preferences.stepsPerMin,
                    weightKg = preferences.weightKg,
                    heightCm = preferences.heightCm,
                    age = preferences.age,
                    isMale = preferences.isMale,
                )
                val dailyMetrics = HealthConnectMapper.buildDailyMetrics(
                    steps, exercises, exerciseCalories, userProfile, startDate, endDate,
                )
                val activities = HealthConnectMapper.mapExerciseSessions(exercises)
                val sleepRecords = HealthConnectMapper.mapSleepSessions(sleep)

                _uiState.value = _uiState.value.copy(
                    importProgress = 0.8f,
                    importDaysCompleted = dailyMetrics.size,
                )

                val token = preferences.jwtToken
                    ?: throw Exception("Non authentifie")

                val request = HealthSyncRequest(
                    deviceName = Build.MODEL,
                    syncedAt = Instant.now().toString(),
                    dailyMetrics = dailyMetrics,
                    activities = activities,
                    sleepSessions = sleepRecords,
                )

                val response = apiService.postHealthSync("Bearer $token", request)
                preferences.lastSyncTimestamp = System.currentTimeMillis()

                _uiState.value = _uiState.value.copy(
                    importProgress = 1.0f,
                    isImporting = false,
                )

                syncLogRepository.log(
                    SyncLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = "INITIAL_IMPORT",
                        status = "SUCCESS",
                        message = "Imported ${response.synced.dailyMetrics} days, " +
                            "${response.synced.activities} activities, " +
                            "${response.synced.sleepSessions} sleep sessions",
                    ),
                )
                loadLogs()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImporting = false,
                    error = "Erreur d'import: ${e.message}",
                )
                syncLogRepository.log(
                    SyncLogEntry(
                        timestamp = System.currentTimeMillis(),
                        type = "INITIAL_IMPORT",
                        status = "ERROR",
                        message = "Import failed: ${e.message}",
                    ),
                )
                loadLogs()
            }
        }
    }

    fun disconnect() {
        syncScheduler.cancelAll()
        preferences.clearAll()
    }
}
