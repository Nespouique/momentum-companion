package com.momentum.companion.sync

import com.momentum.companion.data.api.MomentumApiService
import com.momentum.companion.data.api.models.ActivityRecord
import com.momentum.companion.data.api.models.DailyMetric
import com.momentum.companion.data.api.models.HealthSyncRequest
import com.momentum.companion.data.api.models.SleepRecord
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SyncCounts(
    val dailyMetrics: Int,
    val activities: Int,
    val sleepSessions: Int,
) {
    operator fun plus(other: SyncCounts): SyncCounts {
        return SyncCounts(
            dailyMetrics = dailyMetrics + other.dailyMetrics,
            activities = activities + other.activities,
            sleepSessions = sleepSessions + other.sleepSessions,
        )
    }
}

object HealthSyncUploader {

    suspend fun upload(
        apiService: MomentumApiService,
        token: String,
        deviceName: String,
        dailyMetrics: List<DailyMetric>,
        activities: List<ActivityRecord>,
        sleepSessions: List<SleepRecord>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): SyncCounts {
        val datedDaily = dailyMetrics.toDated { it.date }
        val datedActivities = activities.toDated { it.date }
        val datedSleep = sleepSessions.toDated { it.date }

        suspend fun postRange(rangeStart: LocalDate, rangeEnd: LocalDate): SyncCounts {
            val request = HealthSyncRequest(
                deviceName = deviceName,
                syncedAt = Instant.now().toString(),
                dailyMetrics = filterRange(datedDaily, rangeStart, rangeEnd),
                activities = filterRange(datedActivities, rangeStart, rangeEnd),
                sleepSessions = filterRange(datedSleep, rangeStart, rangeEnd),
            )

            return try {
                val response = apiService.postHealthSync("Bearer $token", request)
                SyncCounts(
                    dailyMetrics = response.synced.dailyMetrics,
                    activities = response.synced.activities,
                    sleepSessions = response.synced.sleepSessions,
                )
            } catch (e: HttpException) {
                if (e.code() == 413 && rangeStart.isBefore(rangeEnd)) {
                    val days = ChronoUnit.DAYS.between(rangeStart, rangeEnd)
                    val mid = rangeStart.plusDays(days / 2)
                    val left = postRange(rangeStart, mid)
                    val right = postRange(mid.plusDays(1), rangeEnd)
                    left + right
                } else {
                    throw e
                }
            }
        }

        return postRange(startDate, endDate)
    }

    private data class Dated<T>(val date: LocalDate, val item: T)

    private fun <T> List<T>.toDated(dateSelector: (T) -> String): List<Dated<T>> {
        return map { item -> Dated(LocalDate.parse(dateSelector(item)), item) }
    }

    private fun <T> filterRange(
        items: List<Dated<T>>,
        start: LocalDate,
        end: LocalDate,
    ): List<T> {
        return items.filter { dated ->
            !dated.date.isBefore(start) && !dated.date.isAfter(end)
        }.map { it.item }
    }
}
