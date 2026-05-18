package com.example.databasemonitor.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

/**
 * Provides AI-powered insights on database health by analyzing metric snapshots.
 * Attempts to use an external AI API (pollinations.ai) and falls back to
 * a local heuristic-based analysis if the API is unavailable.
 */
object AiInsights {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    
    /**
     * Generates an insight summary based on the latest metric snapshots.
     * @param snapshots List of recent metric snapshots to analyze.
     * @return A human-readable analysis string.
     */
    suspend fun generateInsights(snapshots: List<DbMetricsSnapshot>): String = withContext(Dispatchers.IO) {
        if (snapshots.isEmpty()) return@withContext "Not enough data for insights."

        val latest = snapshots.last()

        // Build a prompt describing the current metrics for the AI model.
        val prompt = """
            Act as a PostgreSQL expert. Analyze the following database metrics and provide a short health summary with actionable optimization suggestions:
            - Cache Hit Ratio: ${latest.cacheHitRatio.format(2)}%
            - Index Usage Ratio: ${latest.indexUsageRatio.format(2)}%
            - Active Connections: ${latest.activeConnections}
            - Slow Queries Detected: ${latest.slowQueries.size}
            Provide only the direct text analysis.
        """.trimIndent()

        try {
            val encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8.toString())
            val urlString = "https://text.pollinations.ai/$encodedPrompt"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            
            if (response.statusCode() == 200) {
                val text = response.body()
                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            }
            
            throw Exception("API returned non-200 or empty response")
        } catch (e: Exception) {
            // Fallback to local logic if the free/public API fails
            generateLocalInsights(snapshots)
        }
    }

    /**
     * Local heuristic analysis when the AI API is unavailable.
     * Assigns a health score and generates actionable tips based on thresholds.
     */
    private fun generateLocalInsights(snapshots: List<DbMetricsSnapshot>): String {
        val latest = snapshots.last()
        var score = 100
        val anomalies = mutableListOf<String>()
        val tips = mutableListOf<String>()

        if (latest.cacheHitRatio < 90.0) {
            score -= 20
            anomalies.add("Cache hit ratio is critically low (${latest.cacheHitRatio.format(1)}%).")
            tips.add("Adjust `shared_buffers` in your PostgreSQL config to allocate more RAM for caching.")
        } else if (latest.cacheHitRatio < 95.0) {
            score -= 10
            tips.add("Consider slightly increasing `shared_buffers` to improve cache hit ratio.")
        }

        if (latest.indexUsageRatio < 80.0) {
            score -= 15
            anomalies.add("Index usage is low (${latest.indexUsageRatio.format(1)}%).")
            tips.add("Review slow queries. You might be missing indexes on frequently filtered columns.")
        }

        if (latest.activeConnections > 80) { // arbitrary threshold for demo
            score -= 10
            anomalies.add("High number of active connections (${latest.activeConnections}).")
            tips.add("Consider setting up a connection pooler like PgBouncer if you haven't already.")
        }

        val allSlowQueries = snapshots.flatMap { it.slowQueries }.distinctBy { it.query }
        if (allSlowQueries.isNotEmpty()) {
            score -= 15
            anomalies.add("${allSlowQueries.size} distinct slow queries detected recently.")
            tips.add("Run `EXPLAIN ANALYZE` on the following queries:\n${allSlowQueries.take(3).joinToString("\n") { "  - ${it.query.take(50)}..." }}")
        }

        if (score < 0) score = 0

        return """
        *(Generated locally due to API unavailability)*

        ### Overall Database Health Score: **$score%**
        
        #### Anomaly Flags:
        ${if (anomalies.isEmpty()) "✅ No critical anomalies detected." else anomalies.joinToString("\n") { "⚠️ $it" }}
        
        #### Actionable Insights:
        ${if (tips.isEmpty()) "✅ Database seems well optimized!" else tips.joinToString("\n") { "💡 $it" }}
        """.trimIndent()
    }

    private fun Double.format(digits: Int): String {
        return "%.${digits}f".format(this)
    }
}