package com.momentum.companion.data.log

import kotlinx.serialization.Serializable

@Serializable
data class SyncLogEntry(
    val timestamp: Long,
    val type: String,
    val status: String,
    val message: String,
)
