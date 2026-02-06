package com.momentum.companion.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectReader @Inject constructor(
    private val client: HealthConnectClient,
) {

    /**
     * Aggregate steps per day over a date range.
     */
    suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long> {
        val zone = ZoneId.systemDefault()
        val result = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return result.associate { bucket ->
            bucket.startTime.atZone(zone).toLocalDate() to
                (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
        }
    }

    /**
     * Aggregate active calories burned per day (kcal).
     */
    suspend fun readActiveCalories(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val zone = ZoneId.systemDefault()
        val result = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return result.associate { bucket ->
            bucket.startTime.atZone(zone).toLocalDate() to
                (bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
                    ?.inKilocalories ?: 0.0)
        }
    }

    /**
     * Read individual exercise sessions with metadata.
     */
    suspend fun readExerciseSessions(
        start: LocalDate,
        end: LocalDate,
    ): List<ExerciseSessionRecord> {
        val zone = ZoneId.systemDefault()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return response.records
    }

    /**
     * Read sleep sessions with stages.
     */
    suspend fun readSleepSessions(
        start: LocalDate,
        end: LocalDate,
    ): List<SleepSessionRecord> {
        val zone = ZoneId.systemDefault()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return response.records
    }
}
