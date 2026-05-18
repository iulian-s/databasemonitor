package com.example.databasemonitor.backend

import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

/**
 * Defines the environment/profile type for the database connection.
 * - LOCAL: Standard localhost PostgreSQL instance
 * - DOCKER: PostgreSQL running inside a Docker container
 * - SUPABASE: Supabase cloud PostgreSQL with specific SSL requirements
 */
enum class ProfileType {
    LOCAL, DOCKER, SUPABASE
}

/**
 * Holds all configuration parameters needed to connect to a PostgreSQL database.
 * Serializable for easy persistence across app sessions.
 */
@Serializable
data class DatabaseConfig(
    val profileType: ProfileType,
    val host: String,
    val port: Int = 5432,
    val user: String,
    val pass: String,
    val dbName: String,
    val sslMode: String = "prefer"
)

/**
 * Manages JDBC connections to PostgreSQL databases.
 * Provides a single static method to establish database connections
 * with appropriate SSL settings based on the profile type.
 */
object DatabaseConnectionManager {

    init {
        // Load the PostgreSQL JDBC driver class explicitly.
        // Required for JDBC 4.0+ compatibility in certain environments.
        Class.forName("org.postgresql.Driver")
    }

    /**
     * Creates a new JDBC connection using the provided database configuration.
     * @param config Database configuration containing host, port, credentials, etc.
     * @return A live JDBC Connection object that must be closed by the caller.
     */
    fun connect(config: DatabaseConfig): Connection {
        val url = "jdbc:postgresql://${config.host}:${config.port}/${config.dbName}"
        val props = Properties()
        props.setProperty("user", config.user)
        props.setProperty("password", config.pass)
        
        // Supabase requires strict SSL; other profiles use configurable mode (prefer by default).
        if (config.profileType == ProfileType.SUPABASE) {
            props.setProperty("ssl", "true")
            props.setProperty("sslmode", "require")
        } else {
            props.setProperty("sslmode", config.sslMode)
        }

        return DriverManager.getConnection(url, props)
    }
}
