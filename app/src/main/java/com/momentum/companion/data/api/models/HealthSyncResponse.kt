package com.momentum.companion.data.api.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthSyncResponse(
    val synced: SyncedCounts,
    val device: DeviceInfo,
)

@Serializable
data class SyncedCounts(
    val dailyMetrics: Int,
    val activities: Int,
    val sleepSessions: Int,
)

@Serializable
data class DeviceInfo(
    val id: String,
    val lastSyncAt: String,
)

@Serializable
data class SyncStatusResponse(
    val configured: Boolean,
    val lastSync: String? = null,
    val trackables: TrackablesStatus? = null,
)

@Serializable
data class TrackablesStatus(
    val steps: TrackableInfo? = null,
    val activeCalories: TrackableInfo? = null,
    val activeMinutes: TrackableInfo? = null,
    val sleepDuration: TrackableInfo? = null,
)

@Serializable
data class TrackableInfo(
    val id: String,
    val goalValue: Int? = null,
)
