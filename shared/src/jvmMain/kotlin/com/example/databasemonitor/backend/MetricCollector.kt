package com.example.databasemonitor.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import oshi.SystemInfo
import java.sql.Connection

/**
 * Represents a snapshot of database and host system metrics collected at a specific point in time.
 * Used for both live monitoring and historical analysis.
 */
data class DbMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val activeConnections: Int,
    val databaseSizeBytes: Long,
    val cacheHitRatio: Double,
    val indexUsageRatio: Double,
    val hostCpuUsage: Double?,
    val hostMemoryUsage: Double?,
    val slowQueries: List<SlowQuery>
)

/**
 * Represents a query that is currently executing and has exceeded the defined slow query threshold.
 */
data class SlowQuery(
    val query: String,
    val durationSeconds: Double
)

/**
 * Collects real-time PostgreSQL metrics by periodically querying system catalog views.
 * Emits a stream of DbMetrics through a Kotlin Flow.
 * Uses OSHI library to gather host-level CPU and memory usage (when available).
 */
class MetricCollector(private val config: DatabaseConfig) {
    private val systemInfo = try { SystemInfo() } catch (e: Throwable) { null }
    private val hardware = try { systemInfo?.hardware } catch (e: Throwable) { null }

    /**
     * Returns a cold Flow that periodically collects database metrics.
     * The flow runs on Dispatchers.IO and will auto-reconnect if the connection drops.
     *
     * @param intervalMs Milliseconds between each metric collection. Defaults to 5000ms.
     * @param slowQueryThresholdSec Queries running longer than this are considered slow. Defaults to 1.0s.
     * @return Flow emitting Result<DbMetrics>. Failures indicate collection errors or reconnection.
     */
    fun collectMetricsStream(intervalMs: Long = 5000L, slowQueryThresholdSec: Double = 1.0): Flow<Result<DbMetrics>> = flow {
        var connection: Connection? = null
        try {
            connection = DatabaseConnectionManager.connect(config)
            while (true) {
                try {
                    val metrics = fetchMetrics(connection!!, config.dbName, slowQueryThresholdSec)
                    emit(Result.success(metrics))
                } catch (e: Exception) {
                    // On error, attempt to reconnect before the next collection cycle.
                    emit(Result.failure(e))
                    connection?.close()
                    connection = DatabaseConnectionManager.connect(config)
                }
                delay(intervalMs)
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        } finally {
            connection?.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Executes all metric queries against the given connection and aggregates the results.
     * Queries include: active connections, database size, cache hit ratio, index usage,
     * and slow queries. Host system metrics are included when available (not available for Supabase).
     */
    private fun fetchMetrics(conn: Connection, dbName: String, slowQueryThresholdSec: Double): DbMetrics {
        // Count currently active (running) database sessions.
        var activeConnections = 0
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE state = 'active'")
            if (rs.next()) activeConnections = rs.getInt(1)
        }

        // Get total size of the target database in bytes.
        var dbSizeBytes = 0L
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT pg_database_size('$dbName')")
            if (rs.next()) dbSizeBytes = rs.getLong(1)
        }

        // Cache hit ratio: proportion of block reads served from shared buffer cache vs disk.
        var cacheHitRatio = 100.0
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
        SELECT 
          COALESCE(sum(heap_blks_hit) / nullif(sum(heap_blks_hit) + sum(heap_blks_read), 0), 1.0) AS ratio
        FROM pg_statio_user_tables
    """.trimIndent())
            if (rs.next()) cacheHitRatio = rs.getDouble(1) * 100.0
        }

        // Index usage ratio: proportion of scans that used indexes vs sequential scans.
        var indexUsageRatio = 100.0
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
        SELECT 
          COALESCE(sum(idx_scan) / nullif(sum(idx_scan) + sum(seq_scan), 0), 1.0) as ratio
        FROM pg_stat_user_tables
    """.trimIndent())
            if (rs.next()) indexUsageRatio = rs.getDouble(1) * 100.0
        }

        // Identify currently running queries that exceed the slow query duration threshold.
        val slowQueries = mutableListOf<SlowQuery>()
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("""
                SELECT query, EXTRACT(EPOCH FROM (now() - query_start)) AS duration
                FROM pg_stat_activity
                WHERE state = 'active' AND EXTRACT(EPOCH FROM (now() - query_start)) > $slowQueryThresholdSec
            """.trimIndent())
            while (rs.next()) {
                slowQueries.add(SlowQuery(rs.getString("query"), rs.getDouble("duration")))
            }
        }

        // Host-level metrics are collected via OSHI but skipped for Supabase
        // as the monitored host is remote and not representative of the database server.
        var hostCpuUsage: Double? = null
        var hostMemoryUsage: Double? = null
        
        if (config.profileType != ProfileType.SUPABASE && hardware != null) {
            try {
                val processor = hardware.processor
                val cpuLoad = processor.getSystemCpuLoad(1000)
                hostCpuUsage = if (cpuLoad >= 0.0) cpuLoad * 100.0 else null

                val memory = hardware.memory
                val usedMemory = memory.total - memory.available
                hostMemoryUsage = (usedMemory.toDouble() / memory.total.toDouble()) * 100.0
            } catch (e: Throwable) {
                // OSHI may fail on certain systems or without elevated privileges; fall back to nulls.
            }
        }

        return DbMetrics(
            activeConnections = activeConnections,
            databaseSizeBytes = dbSizeBytes,
            cacheHitRatio = if (cacheHitRatio.isNaN()) 0.0 else cacheHitRatio,
            indexUsageRatio = if (indexUsageRatio.isNaN()) 0.0 else indexUsageRatio,
            hostCpuUsage = hostCpuUsage,
            hostMemoryUsage = hostMemoryUsage,
            slowQueries = slowQueries
        )
    }
}
