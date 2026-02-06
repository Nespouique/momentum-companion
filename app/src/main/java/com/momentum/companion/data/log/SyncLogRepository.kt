package com.momentum.companion.data.log

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SyncLogRepository(private val context: Context) {
    private val logFile: File
        get() = File(context.filesDir, LOG_FILE_NAME)

    private val json = Json { ignoreUnknownKeys = true }

    fun log(entry: SyncLogEntry) {
        val line = json.encodeToString(entry) + "\n"
        logFile.appendText(line)
        trimLogs(MAX_ENTRIES)
    }

    fun getRecentLogs(count: Int = DEFAULT_LOG_COUNT): List<SyncLogEntry> {
        if (!logFile.exists()) return emptyList()
        return logFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    json.decodeFromString<SyncLogEntry>(line)
                } catch (e: Exception) {
                    null
                }
            }
            .takeLast(count)
            .reversed()
    }

    fun clearLogs() {
        if (logFile.exists()) logFile.delete()
    }

    private fun trimLogs(maxEntries: Int) {
        if (!logFile.exists()) return
        val lines = logFile.readLines().filter { it.isNotBlank() }
        if (lines.size > maxEntries) {
            logFile.writeText(lines.takeLast(maxEntries).joinToString("\n") + "\n")
        }
    }

    companion object {
        private const val LOG_FILE_NAME = "sync_logs.jsonl"
        private const val MAX_ENTRIES = 200
        private const val DEFAULT_LOG_COUNT = 50
    }
}
