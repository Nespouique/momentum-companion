package com.momentum.companion.data.healthconnect

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectReader(private val client: HealthConnectClient) {

    /**
     * Filter out records from ignored sources (e.g. Google Fit which duplicates Samsung data).
     */
    private fun <T : androidx.health.connect.client.records.Record> List<T>.filterSource(): List<T> {
        return filter { it.metadata.dataOrigin.packageName !in IGNORED_PACKAGES }
    }

    /**
     * From a list of step records, keep only the preferred source per day to avoid
     * double-counting. Samsung Health writes a single full-day record; the Android
     * sensor platform writes the same steps as many short records in parallel.
     *
     * Priority: PREFERRED_STEP_SOURCES in order. If none present for a given day,
     * fall back to all non-ignored sources.
     */
    private fun List<StepsRecord>.deduplicateStepsBySource(): List<StepsRecord> {
        val zone = ZoneId.systemDefault()
        val byDate = groupBy { it.startTime.atZone(zone).toLocalDate() }
        return byDate.flatMap { (_, dayRecords) ->
            val preferredSource = PREFERRED_STEP_SOURCES.firstOrNull { source ->
                dayRecords.any { it.metadata.dataOrigin.packageName == source }
            }
            if (preferredSource != null) {
                dayRecords.filter { it.metadata.dataOrigin.packageName == preferredSource }
            } else {
                dayRecords
            }
        }
    }

    /**
     * Read raw step records and sum counts per day.
     * Uses raw records instead of aggregateGroupByPeriod because Samsung Health
     * writes full-day StepsRecord entries that return 0 from aggregation.
     *
     * Deduplication: when multiple sources overlap (e.g. Samsung Health + Android
     * sensor platform write the same steps), only the highest-priority source is
     * kept per day to prevent double-counting.
     */
    suspend fun readSteps(start: LocalDate, end: LocalDate): Map<LocalDate, Long> {
        val zone = ZoneId.systemDefault()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return response.records
            .filterSource()
            .deduplicateStepsBySource()
            .groupBy { record ->
                record.startTime.atZone(zone).toLocalDate()
            }.mapValues { (_, records) ->
                records.sumOf { it.count }
            }
    }

    /**
     * Read raw TotalCaloriesBurned records and sum per day (kcal).
     * Filtered to Samsung Health only (Google Fit writes full-day basal records
     * that would inflate the exercise-only total).
     */
    suspend fun readTotalCaloriesBurned(start: LocalDate, end: LocalDate): Map<LocalDate, Double> {
        val zone = ZoneId.systemDefault()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(zone).toInstant(),
                    end.plusDays(1).atStartOfDay(zone).toInstant(),
                ),
            ),
        )
        return response.records.filterSource().groupBy { record ->
            record.startTime.atZone(zone).toLocalDate()
        }.mapValues { (_, records) ->
            records.sumOf { it.energy.inKilocalories }
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
        return response.records.filterSource()
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
        return response.records.filterSource()
    }

    /**
     * Diagnostic: dump all raw Health Connect records for a given day.
     */
    suspend fun logRawData(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val startInstant = date.atStartOfDay(zone).toInstant()
        val endInstant = date.plusDays(1).atStartOfDay(zone).toInstant()
        val timeRange = TimeRangeFilter.between(startInstant, endInstant)

        // Raw StepsRecord entries
        val stepsResponse = client.readRecords(
            ReadRecordsRequest(recordType = StepsRecord::class, timeRangeFilter = timeRange),
        )
        Log.d(TAG, "=== RAW HEALTH CONNECT DATA for $date ===")
        Log.d(TAG, "StepsRecord entries: ${stepsResponse.records.size}")
        stepsResponse.records.forEachIndexed { i, r ->
            val dur = Duration.between(r.startTime, r.endTime).toMinutes()
            val rate = if (dur > 0) r.count / dur else 0
            Log.d(TAG, "  Steps[$i]: ${r.count} steps, " +
                "${r.startTime.atZone(zone).toLocalTime()}-${r.endTime.atZone(zone).toLocalTime()} " +
                "(${dur}min, ${rate} steps/min, src=${r.metadata.dataOrigin.packageName})")
        }

        // Raw ActiveCaloriesBurnedRecord entries
        val calResponse = client.readRecords(
            ReadRecordsRequest(recordType = ActiveCaloriesBurnedRecord::class, timeRangeFilter = timeRange),
        )
        Log.d(TAG, "ActiveCaloriesBurnedRecord entries: ${calResponse.records.size}")
        calResponse.records.forEachIndexed { i, r ->
            Log.d(TAG, "  ActiveCal[$i]: ${r.energy.inKilocalories}kcal, " +
                "${r.startTime.atZone(zone).toLocalTime()}-${r.endTime.atZone(zone).toLocalTime()} " +
                "(src=${r.metadata.dataOrigin.packageName})")
        }

        // Raw TotalCaloriesBurnedRecord entries
        try {
            val totalCalResponse = client.readRecords(
                ReadRecordsRequest(recordType = TotalCaloriesBurnedRecord::class, timeRangeFilter = timeRange),
            )
            Log.d(TAG, "TotalCaloriesBurnedRecord entries: ${totalCalResponse.records.size}")
            totalCalResponse.records.forEachIndexed { i, r ->
                Log.d(TAG, "  TotalCal[$i]: ${r.energy.inKilocalories}kcal, " +
                    "${r.startTime.atZone(zone).toLocalTime()}-${r.endTime.atZone(zone).toLocalTime()} " +
                    "(src=${r.metadata.dataOrigin.packageName})")
            }
        } catch (e: Exception) {
            Log.d(TAG, "TotalCaloriesBurnedRecord: permission denied or unavailable")
        }

        // Raw ExerciseSessionRecord entries
        val exResponse = client.readRecords(
            ReadRecordsRequest(recordType = ExerciseSessionRecord::class, timeRangeFilter = timeRange),
        )
        Log.d(TAG, "ExerciseSessionRecord entries: ${exResponse.records.size}")
        exResponse.records.forEachIndexed { i, r ->
            val dur = Duration.between(r.startTime, r.endTime).toMinutes()
            Log.d(TAG, "  Exercise[$i]: type=${r.exerciseType}, ${dur}min, " +
                "${r.startTime.atZone(zone).toLocalTime()}-${r.endTime.atZone(zone).toLocalTime()} " +
                "(src=${r.metadata.dataOrigin.packageName})")
        }

        Log.d(TAG, "=== END RAW DATA ===")
    }

    companion object {
        private const val TAG = "HealthConnectReader"
        private val IGNORED_PACKAGES = setOf(
            "com.google.android.apps.fitness",
        )

        /**
         * Ordered list of preferred step sources. When a day's records contain data
         * from one of these sources, only that source is used (first match wins).
         * This prevents double-counting when Samsung Health and the Android sensor
         * platform both write overlapping StepsRecords for the same time window.
         */
        private val PREFERRED_STEP_SOURCES = listOf(
            "com.sec.android.app.shealth",  // Samsung Health — authoritative source
        )
    }
}
