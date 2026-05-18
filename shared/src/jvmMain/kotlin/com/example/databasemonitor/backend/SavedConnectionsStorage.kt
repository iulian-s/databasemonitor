package com.example.databasemonitor.backend

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles persistence of saved database connections to the local filesystem.
 * Stores connections as JSON in the user's app data directory,
 * supporting Windows (APPDATA), macOS (Application Support), and Linux (dotfile).
 * Only one connection per profile type (LOCAL/DOCKER/SUPABASE) is kept to avoid duplicates.
 */
object SavedConnectionsStorage {
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

    private val connectionsFile = File(appDataDir, "saved_connections.json")
    private val jsonConfig = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Saves a database configuration, replacing any existing config with the same profile type.
     * @param config The database configuration to save.
     */
    fun saveConnection(config: DatabaseConfig) {
        val currentConfigs = loadConnections().toMutableList()
        currentConfigs.removeIf { it.profileType == config.profileType }
        currentConfigs.add(config)
        connectionsFile.writeText(jsonConfig.encodeToString(currentConfigs))
    }

    /**
     * Loads all saved database connections from the storage file.
     * Returns an empty list if no connections have been saved or if the file cannot be parsed.
     */
    fun loadConnections(): List<DatabaseConfig> {
        return if (connectionsFile.exists()) {
            try {
                jsonConfig.decodeFromString<List<DatabaseConfig>>(connectionsFile.readText())
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}
