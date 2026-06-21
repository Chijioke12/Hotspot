package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HotspotDao {
    @Query("SELECT * FROM hotspot_devices ORDER BY isCurrentlyActive DESC, lastSeen DESC")
    fun getAllDevicesFlow(): Flow<List<HotspotDevice>>

    @Query("SELECT * FROM hotspot_devices WHERE ipAddress = :ip")
    suspend fun getDeviceByIp(ip: String): HotspotDevice?

    @Query("SELECT * FROM hotspot_devices WHERE macAddress = :mac")
    suspend fun getDeviceByMac(mac: String): HotspotDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDevice(device: HotspotDevice)

    @Query("UPDATE hotspot_devices SET isCurrentlyActive = :active")
    suspend fun setAllDevicesInactive(active: Boolean = false)

    @Query("UPDATE hotspot_devices SET isCurrentlyActive = :active WHERE ipAddress = :ip")
    suspend fun setDeviceActiveState(ip: String, active: Boolean)

    @Query("UPDATE hotspot_devices SET customLabel = :label, deviceType = :type, status = :status WHERE ipAddress = :ip")
    suspend fun updateDeviceProfile(ip: String, label: String, type: String, status: String)

    @Query("DELETE FROM hotspot_devices WHERE ipAddress = :ip")
    suspend fun deleteDevice(ip: String)

    // Logs Queries
    @Query("SELECT * FROM hotspot_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllLogsFlow(): Flow<List<HotspotLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HotspotLog)

    @Query("DELETE FROM hotspot_logs")
    suspend fun clearLogs()
}
