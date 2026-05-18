package com.example.databasemonitor.ui

import com.example.databasemonitor.backend.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the complete UI state for the monitor application.
 * Includes connection status, live metrics, historical data, and AI insights.
 */
data class MonitorUiState(
    val isConnected: Boolean = false,
    val connectionError: String? = null,
    val currentMetrics: DbMetrics? = null,
    val historicalMetrics: List<DbMetricsSnapshot> = emptyList(),
    val aiInsights: String? = null,
    val isLoading: Boolean = false,
    val isInsightsLoading: Boolean = false,
    val slowQueryThresholdSec: Double = 1.0,
    val pollingIntervalMs: Long = 5000L
)

/**
 * ViewModel that orchestrates the database monitoring lifecycle.
 * Manages connection/disconnection, metric polling, and AI insight generation.
 */
class MonitorViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var config: DatabaseConfig? = null

    init {
        // Load any previously saved metric history from disk on startup.
        _uiState.update { it.copy(historicalMetrics = SnapshotStorage.loadSnapshots()) }
    }

    /**
     * Attempts to connect to the database using the provided configuration.
     * On success, transitions to the connected state and starts polling for metrics.
     * On failure, sets the connection error message.
     */
    fun connect(dbConfig: DatabaseConfig) {
        this.config = dbConfig
        _uiState.update { it.copy(isLoading = true, connectionError = null) }
        scope.launch(Dispatchers.IO) {
            try {
                val conn = DatabaseConnectionManager.connect(dbConfig)
                conn.close()
                _uiState.update { it.copy(isConnected = true, isLoading = false) }
                startPolling()
            } catch (e: Exception) {
                _uiState.update { it.copy(isConnected = false, isLoading = false, connectionError = e.message) }
            }
        }
    }

    /**
     * Disconnects from the database, cancels the polling job, and resets the UI state.
     * Keeps the historical metrics intact and clears AI insights.
     */
    fun disconnect() {
        pollingJob?.cancel()
        _uiState.update { 
            MonitorUiState(
                historicalMetrics = it.historicalMetrics,
                aiInsights = null
            ) 
        }
    }

    /**
     * Updates the slow query threshold and restarts polling with the new value.
     */
    fun updateSlowQueryThreshold(thresholdSec: Double) {
        _uiState.update { it.copy(slowQueryThresholdSec = thresholdSec) }
        if (uiState.value.isConnected) {
            startPolling()
        }
    }

    /**
     * Starts or restarts the metric collection flow.
     * Cancels any existing polling job and creates a new one.
     * Each metric result is saved to disk and reflected in the UI state.
     */
    private fun startPolling() {
        pollingJob?.cancel()
        val currentConfig = config ?: return
        
        val collector = MetricCollector(currentConfig)
        pollingJob = scope.launch {
            collector.collectMetricsStream(
                intervalMs = uiState.value.pollingIntervalMs,
                slowQueryThresholdSec = uiState.value.slowQueryThresholdSec
            ).collect { result ->
                result.onSuccess { metrics ->
                    SnapshotStorage.saveSnapshot(metrics)
                    _uiState.update { 
                        it.copy(
                            currentMetrics = metrics,
                            historicalMetrics = SnapshotStorage.loadSnapshots(),
                            connectionError = null
                        ) 
                    }
                }.onFailure { error ->
                    _uiState.update { it.copy(connectionError = "Connection dropped: ${error.message}") }
                }
            }
        }
    }

    /**
     * Fetches AI-powered insights based on the last 10 metric snapshots.
     */
    fun fetchInsights() {
        scope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(isInsightsLoading = true) }
            try {
                val snapshots = uiState.value.historicalMetrics.takeLast(10)
                val insights = AiInsights.generateInsights(snapshots)
                _uiState.update { it.copy(aiInsights = insights, isInsightsLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(aiInsights = "Failed to load insights.", isInsightsLoading = false) }
            }
        }
    }
}
