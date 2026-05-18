package com.example.databasemonitor

/**
 * JVM-specific platform implementation.
 * Reports the Java runtime version as the platform name.
 */
class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()