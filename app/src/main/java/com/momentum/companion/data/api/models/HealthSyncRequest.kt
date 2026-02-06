package com.momentum.companion.data.api.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthSyncRequest(
    val deviceName: String,
    val syncedAt: String,
    val dailyMetrics: List<DailyMetric>,
    val activities: List<ActivityRecord>,
    val sleepSessions: List<SleepRecord>,
)

@Serializable
data class DailyMetric(
    val date: String,
    val steps: Int?,
    val activeCalories: Int?,
    val activeMinutes: Int?,
)

@Serializable
data class ActivityRecord(
    val hcRecordId: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val activityType: String,
    val title: String?,
    val durationMinutes: Double,
    val calories: Double?,
    val distance: Double?,
    val heartRateAvg: Int?,
    val sourceApp: String?,
)

@Serializable
data class SleepRecord(
    val date: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Double,
    val score: Int?,
    val stages: List<SleepStage>?,
)

@Serializable
data class SleepStage(
    val stage: String,
    val startTime: String,
    val endTime: String,
)
