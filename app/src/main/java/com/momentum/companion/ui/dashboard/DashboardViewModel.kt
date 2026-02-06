package com.momentum.companion.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.healthconnect.HealthConnectMapper
import com.momentum.companion.data.healthconnect.HealthConnectReader
import com.momentum.companion.data.preferences.AppPreferences
import com.momentum.companion.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class DashboardUiState(
    val serverUrl: String = "",
    val isConnected: Boolean = false,
    val lastSyncFormatted: String = "Jamais",
    val currentSteps: Int = 0,
    val goalSteps: Int = 10000,
    val currentMinutes: Int = 0,
    val goalMinutes: Int = 90,
    val currentCalories: Int = 0,
    val goalCalories: Int = 500,
    val todayActivities: List<TodayActivity> = emptyList(),
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
)

data class TodayActivity(
    val startTime: String,
    val activityType: String,
    val durationMinutes: Int,
    val distanceKm: Double?,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val healthConnectReader: HealthConnectReader?,
    private val apiService: MomentumApiService,
    private val preferences: AppPreferences,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            loadHealthConnectData()
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                lastSyncFormatted = formatLastSync(preferences.lastSyncTimestamp),
            )
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            syncScheduler.syncNow()
            // Give WorkManager a moment to start, then refresh
            kotlinx.coroutines.delay(3000)
            loadHealthConnectData()
            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                lastSyncFormatted = formatLastSync(preferences.lastSyncTimestamp),
            )
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                serverUrl = preferences.serverUrl?.replace("https://", "")?.trimEnd('/') ?: "",
                isConnected = preferences.isConfigured,
                lastSyncFormatted = formatLastSync(preferences.lastSyncTimestamp),
            )
            loadGoals()
            loadHealthConnectData()
        }
    }

    private suspend fun loadGoals() {
        try {
            val token = preferences.jwtToken ?: return
            val status = apiService.getSyncStatus("Bearer $token")
            _uiState.value = _uiState.value.copy(
                goalSteps = status.trackables?.steps?.goalValue ?: 10000,
                goalCalories = status.trackables?.activeCalories?.goalValue ?: 500,
                goalMinutes = status.trackables?.activeMinutes?.goalValue ?: 90,
            )
        } catch (e: Exception) {
            // Use default goals if API is unreachable
        }
    }

    private suspend fun loadHealthConnectData() {
        val reader = healthConnectReader ?: run {
            _uiState.value = _uiState.value.copy(
                error = "Health Connect non disponible sur cet appareil",
            )
            return
        }
        try {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            val steps = reader.readSteps(today, today)
            val todaySteps = steps[today]?.toInt() ?: 0

            val calories = reader.readActiveCalories(today, today)
            val todayCalories = calories[today]?.toInt() ?: 0

            val exercises = reader.readExerciseSessions(today, today)
            val todayMinutes = exercises.sumOf { session ->
                Duration.between(session.startTime, session.endTime).toMinutes()
            }.toInt()

            val activities = exercises.map { session ->
                TodayActivity(
                    startTime = session.startTime.atZone(zone)
                        .toLocalTime().format(timeFormatter),
                    activityType = HealthConnectMapper.exerciseTypeToLabel(session.exerciseType),
                    durationMinutes = Duration.between(session.startTime, session.endTime)
                        .toMinutes().toInt(),
                    distanceKm = null,
                )
            }

            _uiState.value = _uiState.value.copy(
                currentSteps = todaySteps,
                currentCalories = todayCalories,
                currentMinutes = todayMinutes,
                todayActivities = activities,
                error = null,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Erreur de lecture Health Connect: ${e.message}",
            )
        }
    }

    private fun formatLastSync(timestamp: Long): String {
        if (timestamp == 0L) return "Jamais"
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / 60_000
        return when {
            minutes < 1 -> "A l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 1440 -> "il y a ${minutes / 60}h"
            else -> "il y a ${minutes / 1440}j"
        }
    }
}
