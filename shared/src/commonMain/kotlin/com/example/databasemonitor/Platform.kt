package com.example.databasemonitor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform