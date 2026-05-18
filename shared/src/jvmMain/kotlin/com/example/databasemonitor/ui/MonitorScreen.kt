package com.example.databasemonitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.databasemonitor.backend.DatabaseConfig
import com.example.databasemonitor.backend.ProfileType
import com.example.databasemonitor.backend.SavedConnectionsStorage

/**
 * Top-level screen for the database monitor application.
 * Shows either the ConnectionForm or the DashboardView depending on connection state.
 */
@Composable
fun MonitorScreen() {
    val viewModel = remember { MonitorViewModel() }
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (uiState.isConnected) {
                DashboardView(viewModel, uiState)
            } else {
                ConnectionForm(
                    isLoading = uiState.isLoading,
                    error = uiState.connectionError,
                    onConnect = { viewModel.connect(it) }
                )
            }
        }
    }
}

/**
 * The connection form that displays fields for database credentials.
 * Supports three profile types (LOCAL, DOCKER, SUPABASE) and autofills
 * saved connections if "Remember Me" was previously checked.
 */
@Composable
fun ConnectionForm(
    isLoading: Boolean,
    error: String?,
    onConnect: (DatabaseConfig) -> Unit
) {
    var profileType by remember { mutableStateOf(ProfileType.LOCAL) }
    var host by remember { mutableStateOf("localhost") }
    var port by remember { mutableStateOf("5432") }
    var user by remember { mutableStateOf("postgres") }
    var pass by remember { mutableStateOf("") }
    var dbName by remember { mutableStateOf("postgres") }
    var rememberMe by remember { mutableStateOf(false) }

    val savedConnections = remember { SavedConnectionsStorage.loadConnections() }

    // When the selected profile type changes, load saved defaults or fallback to sane defaults.
    LaunchedEffect(profileType) {
        val saved = savedConnections.find { it.profileType == profileType }
        if (saved != null) {
            host = saved.host
            port = saved.port.toString()
            user = saved.user
            pass = saved.pass
            dbName = saved.dbName
        } else if (profileType == ProfileType.SUPABASE) {
            host = "aws-0-us-east-1.pooler.supabase.com"
            port = "5432"
            user = "postgres"
            pass = ""
            dbName = "postgres"
        } else if (profileType == ProfileType.LOCAL) {
            host = "localhost"
            port = "5432"
            user = "postgres"
            pass = ""
            dbName = "postgres"
        }
        else if (profileType == ProfileType.DOCKER) {
            host = "localhost"
            port = "5433"
            user = "monitor_user"
            pass = "monitor_password"
            dbName = "monitor_db"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("PostgreSQL Monitor", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(32.dp))

        Row {
            ProfileType.entries.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = profileType == type,
                        onClick = { profileType = type }
                    )
                    Text(type.name)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") })
        OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
        OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("User") })
        OutlinedTextField(
            value = pass, 
            onValueChange = { pass = it }, 
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        OutlinedTextField(value = dbName, onValueChange = { dbName = it }, label = { Text("Database") })

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it })
            Text("Remember Me")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = {
                val config = DatabaseConfig(
                    profileType = profileType,
                    host = host,
                    port = port.toIntOrNull() ?: 5432,
                    user = user,
                    pass = pass,
                    dbName = dbName
                )
                if (rememberMe) {
                    SavedConnectionsStorage.saveConnection(config)
                }
                onConnect(config)
            },
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Connecting..." else "Connect")
        }
    }
}

/**
 * The main dashboard shown after a successful database connection.
 * Displays current metrics, historical trend charts, AI-powered insights,
 * and a list of currently running slow queries.
 */
@Composable
fun DashboardView(viewModel: MonitorViewModel, uiState: MonitorUiState) {
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        // Top bar with title, slow query threshold dropdown, and disconnect button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show connection error indicator if the polling loop has disconnected.
                if (uiState.connectionError != null) {
                    Text(
                        text = "Connection Error: ${uiState.connectionError}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
                
                var showDropdown by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { showDropdown = true }) {
                        Text("Slow Query Threshold: ${uiState.slowQueryThresholdSec}s")
                    }
                    DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                        listOf(0.5, 1.0, 1.5, 2.0).forEach { th ->
                            DropdownMenuItem(
                                text = { Text("${th}s") },
                                onClick = { 
                                    viewModel.updateSlowQueryThreshold(th)
                                    showDropdown = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val current = uiState.currentMetrics
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Active Connections", "${current.activeConnections}", Modifier.weight(1f))
                MetricCard("DB Size (MB)", "${current.databaseSizeBytes / (1024 * 1024)}", Modifier.weight(1f))
                MetricCard("Cache Hit Ratio", "%.1f%%".format(current.cacheHitRatio), Modifier.weight(1f), tooltip = "Percentage of reads served from shared buffer cache vs disk. Aim for >95%.")
                MetricCard("Index Usage", "%.1f%%".format(current.indexUsageRatio), Modifier.weight(1f), tooltip = "Proportion of table scans that used indexes. Higher values indicate better query performance.")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Host CPU", current.hostCpuUsage?.let { "%.1f%%".format(it) } ?: "N/A", Modifier.weight(1f))
                MetricCard("Host Memory", current.hostMemoryUsage?.let { "%.1f%%".format(it) } ?: "N/A", Modifier.weight(1f))
                MetricCard("Slow Queries", "${current.slowQueries.size}", Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Historical Trends", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Active Connections")
                    Spacer(modifier = Modifier.height(4.dp))
                    LineChart(
                        data = uiState.historicalMetrics.map { it.activeConnections.toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        formatValue = { "${it.toInt()} conn" }
                    )
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("DB Size Growth (MB)")
                    Spacer(modifier = Modifier.height(4.dp))
                    LineChart(
                        data = uiState.historicalMetrics.map { (it.databaseSizeBytes / (1024 * 1024)).toFloat() },
                        modifier = Modifier.fillMaxSize(),
                        formatValue = { "${it.toInt()} MB" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        var isInsightsExpanded by remember { mutableStateOf(true) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI Insights & Health", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { isInsightsExpanded = !isInsightsExpanded }) {
                    Icon(
                        imageVector = if (isInsightsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Insights"
                    )
                }
            }
            Button(onClick = { viewModel.fetchInsights() }, enabled = !uiState.isInsightsLoading) {
                Text(if (uiState.isInsightsLoading) "Loading..." else if (uiState.aiInsights == null) "Generate AI Insights" else "Refresh Insights")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        if (isInsightsExpanded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (uiState.isInsightsLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    val insights = uiState.aiInsights
                    if (insights != null) {
                        SelectionContainer {
                            Text(
                                text = insights,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Click 'Generate AI Insights' to analyze the latest data.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Recent Slow Queries", style = MaterialTheme.typography.titleLarge)
        Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            val allSlowQueries = uiState.currentMetrics?.slowQueries ?: emptyList()
            if (allSlowQueries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No slow queries detected.")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(allSlowQueries) { sq ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Duration: %.2fs".format(sq.durationSeconds), fontWeight = FontWeight.Bold)
                            Text(sq.query, style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable card component that displays a single metric label and its value.
 * Optionally shows a tooltip on hover for additional context.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, tooltip: String? = null) {
    var isHovered by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { isHovered = tooltip != null }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
    ) {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        if (isHovered && tooltip != null) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.small,
                tonalElevation = 8.dp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-8).dp)
            ) {
                Text(
                    text = tooltip,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * A simple line chart composable that plots data points on a Canvas.
 * Supports hover interaction to highlight the closest data point and display its value.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LineChart(data: List<Float>, modifier: Modifier = Modifier, formatValue: (Float) -> String) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data")
        }
        return
    }

    var hoverOffset by remember { mutableStateOf<Offset?>(null) }
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    
    val maxVal = data.maxOrNull() ?: 0f
    val minVal = data.minOrNull() ?: 0f
    val range = if (maxVal == minVal) 1f else maxVal - minVal

    Box(modifier = modifier
        .onSizeChanged { componentSize = it }
        .onPointerEvent(PointerEventType.Move) {
            hoverOffset = it.changes.first().position
        }
        .onPointerEvent(PointerEventType.Exit) {
            hoverOffset = null
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
            val width = size.width
            val height = size.height

            val points = data.mapIndexed { index, value ->
                val x = if (data.size > 1) index.toFloat() / (data.size - 1) * width else width / 2
                val y = height - ((value - minVal) / range * height)
                Offset(x, y)
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                if (index == 0) {
                    path.moveTo(point.x, point.y)
                } else {
                    path.lineTo(point.x, point.y)
                }
            }

            drawPath(
                path = path,
                color = Color.Blue,
                style = Stroke(width = 3.dp.toPx())
            )

            val currentHover = hoverOffset
            if (currentHover != null) {
                val closestIndex = points.indexOfMinBy { kotlin.math.abs(it.x - currentHover.x) }
                if (closestIndex != -1) {
                    val closestPoint = points[closestIndex]
                    drawCircle(Color.Red, radius = 5.dp.toPx(), center = closestPoint)
                }
            }
        }
        
        val currentHover = hoverOffset
        if (currentHover != null && data.isNotEmpty() && componentSize.width > 0) {
            // Find the closest point roughly to calculate string (lazy but works here)
            val index = ((currentHover.x / componentSize.width.toFloat()) * (data.size - 1)).toInt().coerceIn(0, data.size - 1)
            val value = data[index]
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = formatValue(value),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Extension function that returns the index of the element with the minimum value
 * as determined by the given selector function. Returns -1 if the list is empty.
 */
fun <T> List<T>.indexOfMinBy(selector: (T) -> Float): Int {
    if (isEmpty()) return -1
    var minIndex = 0
    var minValue = selector(this[0])
    for (i in 1 until size) {
        val v = selector(this[i])
        if (v < minValue) {
            minIndex = i
            minValue = v
        }
    }
    return minIndex
}
