package com.momentum.companion.sync

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.HealthSyncRequest
import com.momentum.companion.data.api.models.LoginRequest
import com.momentum.companion.data.healthconnect.HealthConnectMapper
import com.momentum.companion.data.healthconnect.HealthConnectReader
import com.momentum.companion.data.healthconnect.UserProfile
import com.momentum.companion.data.log.SyncLogEntry
import com.momentum.companion.data.log.SyncLogRepository
import com.momentum.companion.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val healthConnectReader: HealthConnectReader?,
    private val apiService: MomentumApiService,
    private val preferences: AppPreferences,
    private val syncLogRepository: SyncLogRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started, attempt #$runAttemptCount")

        // 1. Check configuration
        if (!preferences.isConfigured) {
            Log.w(TAG, "App not configured, skipping sync")
            return Result.failure()
        }

        // 2. Check Health Connect availability
        if (healthConnectReader == null) {
            Log.w(TAG, "Health Connect not available, skipping sync")
            syncLogRepository.log(
                SyncLogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = SYNC_TYPE_PERIODIC,
                    status = STATUS_ERROR,
                    message = "Health Connect not available on this device",
                ),
            )
            return Result.failure()
        }

        // 3. Ensure valid token
        val token = ensureValidToken()
        if (token == null) {
            syncLogRepository.log(
                SyncLogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = SYNC_TYPE_PERIODIC,
                    status = STATUS_ERROR,
                    message = "Authentication failed - could not obtain token",
                ),
            )
            return Result.failure()
        }

        // 4. Determine date range
        val lastSync = preferences.lastSyncTimestamp
        val startDate = if (lastSync > 0) {
            Instant.ofEpochMilli(lastSync)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        } else {
            LocalDate.now().minusDays(1)
        }
        val endDate = LocalDate.now()

        return try {
            // 5. Read Health Connect data
            val steps = healthConnectReader.readSteps(startDate, endDate)
            val exercises = healthConnectReader.readExerciseSessions(startDate, endDate)
            val exerciseCalories = healthConnectReader.readTotalCaloriesBurned(startDate, endDate)
            val sleep = healthConnectReader.readSleepSessions(startDate, endDate)

            // 6. Build request payload with estimation
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

            val request = HealthSyncRequest(
                deviceName = Build.MODEL,
                syncedAt = Instant.now().toString(),
                dailyMetrics = dailyMetrics,
                activities = activities,
                sleepSessions = sleepRecords,
            )

            // 7. Send to API
            val response = apiService.postHealthSync("Bearer $token", request)
            preferences.lastSyncTimestamp = System.currentTimeMillis()

            syncLogRepository.log(
                SyncLogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = SYNC_TYPE_PERIODIC,
                    status = STATUS_SUCCESS,
                    message = "Synced ${response.synced.dailyMetrics} days, " +
                        "${response.synced.activities} activities, " +
                        "${response.synced.sleepSessions} sleep sessions",
                ),
            )

            Log.d(TAG, "Sync successful")
            Result.success()
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "Sync failed with HTTP ${e.code()}", e)
            if (e.code() == 401) {
                // Clear stale token so next retry triggers re-login
                preferences.jwtToken = null
            }
            syncLogRepository.log(
                SyncLogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = SYNC_TYPE_PERIODIC,
                    status = if (runAttemptCount < MAX_RETRY_COUNT) STATUS_RETRY else STATUS_ERROR,
                    message = "Sync failed (HTTP ${e.code()}): ${e.message}",
                ),
            )
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            syncLogRepository.log(
                SyncLogEntry(
                    timestamp = System.currentTimeMillis(),
                    type = SYNC_TYPE_PERIODIC,
                    status = if (runAttemptCount < MAX_RETRY_COUNT) STATUS_RETRY else STATUS_ERROR,
                    message = "Sync failed: ${e.message}",
                ),
            )

            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private suspend fun ensureValidToken(): String? {
        val token = preferences.jwtToken
        if (token != null) return token

        val email = preferences.email ?: return null
        val password = preferences.password ?: return null

        return try {
            val response = apiService.login(LoginRequest(email, password))
            preferences.jwtToken = response.token
            response.token
        } catch (e: Exception) {
            Log.e(TAG, "Re-login failed", e)
            null
        }
    }

    companion object {
        const val WORK_NAME = "momentum_health_sync"
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_COUNT = 3
        private const val SYNC_TYPE_PERIODIC = "PERIODIC_SYNC"
        private const val STATUS_SUCCESS = "SUCCESS"
        private const val STATUS_ERROR = "ERROR"
        private const val STATUS_RETRY = "RETRY"
    }
}
