package com.example.databasemonitor.backend

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Serializable snapshot of database metrics.
 * Unlike DbMetrics, this version is suitable for JSON persistence
 * and does not contain runtime-specific data structures.
 */
@Serializable
data class DbMetricsSnapshot(
    val timestamp: Long,
    val activeConnections: Int,
    val databaseSizeBytes: Long,
    val cacheHitRatio: Double,
    val indexUsageRatio: Double,
    val hostCpuUsage: Double?,
    val hostMemoryUsage: Double?,
    val slowQueries: List<SlowQuerySnapshot>
)

/**
 * Serializable representation of a slow query for persistence.
 */
@Serializable
data class SlowQuerySnapshot(
    val query: String,
    val durationSeconds: Double
)

/**
 * Converts a live DbMetrics object into a persistence-ready DbMetricsSnapshot.
 */
fun DbMetrics.toSnapshot() = DbMetricsSnapshot(
    timestamp = timestamp,
    activeConnections = activeConnections,
    databaseSizeBytes = databaseSizeBytes,
    cacheHitRatio = cacheHitRatio,
    indexUsageRatio = indexUsageRatio,
    hostCpuUsage = hostCpuUsage,
    hostMemoryUsage = hostMemoryUsage,
    slowQueries = slowQueries.map { SlowQuerySnapshot(it.query, it.durationSeconds) }
)

/**
 * Handles persistence of metric snapshots to the local filesystem.
 * Stores the history as a JSON file in the user's app data directory,
 * supporting Windows (APPDATA), macOS (Application Support), and Linux (dotfile).
 * Keeps a rolling window of the last 100 snapshots to prevent unbounded file growth.
 */
object SnapshotStorage {
    // Determines the appropriate platform-specific app data directory.
    private val appDataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        val appName = "DatabaseMonitor"
        val dir = when {
            os.contains("win") -> File(System.getenv("APPDATA"), appName)
            os.contains("mac") -> File(userHome, "Library/Application Support/$appName")
            else -> File(userHome, ".$appName")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val metricsFile = File(appDataDir, "metrics_history.json")
    private val jsonConfig = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Saves a new metric snapshot to the history file.
     * Maintains a maximum of 100 entries, removing the oldest when the limit is exceeded.
     */
    fun saveSnapshot(metric: DbMetrics) {
        val currentSnapshots = loadSnapshots().toMutableList()
        currentSnapshots.add(metric.toSnapshot())
        // Keep last 100 snapshots
        if (currentSnapshots.size > 100) {
            currentSnapshots.removeAt(0)
        }
        metricsFile.writeText(jsonConfig.encodeToString(currentSnapshots))
    }

    /**
     * Loads all persisted metric snapshots from the history file.
     * Returns an empty list if the file does not exist or cannot be parsed.
     */
    fun loadSnapshots(): List<DbMetricsSnapshot> {
        return if (metricsFile.exists()) {
            try {
                jsonConfig.decodeFromString<List<DbMetricsSnapshot>>(metricsFile.readText())
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
    
    /**
     * Clears all saved snapshots by deleting the history file.
     */
    fun clearSnapshots() {
        if (metricsFile.exists()) {
            metricsFile.delete()
        }
    }
}
