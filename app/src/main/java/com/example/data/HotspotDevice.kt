package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hotspot_devices")
data class HotspotDevice(
    @PrimaryKey val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val customLabel: String = "",
    val deviceType: String = "UNKNOWN", // MOBILE, LAPTOP, TV, SMART_HOME, UNKNOWN
    val status: String = "UNKNOWN",     // APPROVED, FLAGGED (Blocked Alert), UNKNOWN
    val isCurrentlyActive: Boolean = false,
    val firstDiscovered: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val responseTimeMs: Long = -1
)

@Entity(tableName = "hotspot_logs")
data class HotspotLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ipAddress: String,
    val macAddress: String,
    val hostname: String,
    val deviceLabel: String,
    val eventType: String, // "CONNECTED", "DISCONNECTED", "ALERT_TRIGGERED"
    val timestamp: Long = System.currentTimeMillis()
)
