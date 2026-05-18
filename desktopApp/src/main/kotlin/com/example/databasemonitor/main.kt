package com.example.databasemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.example.databasemonitor.ui.MonitorScreen

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PostgreSQL Database Monitor",
        state = WindowState(size = DpSize(1200.dp, 800.dp))
    ) {
        MonitorScreen()
    }
}
