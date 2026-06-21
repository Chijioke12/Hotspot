package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.HotspotDatabase
import com.example.data.HotspotDevice
import com.example.data.HotspotLog
import com.example.data.HotspotRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ScanState {
    object Idle : ScanState
    data class Scanning(val progress: Int, val foundCount: Int) : ScanState
    data class Success(val foundCount: Int) : ScanState
    data class Error(val message: String) : ScanState
}

class HotspotViewModel(application: Application) : AndroidViewModel(application) {
    private val database = HotspotDatabase.getDatabase(application)
    private val repository = HotspotRepository(application, database.dao)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val devices: StateFlow<List<HotspotDevice>> = repository.allDevices
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val logs: StateFlow<List<HotspotLog>> = repository.allLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _lastAlert = MutableStateFlow<String?>(null)
    val lastAlert: StateFlow<String?> = _lastAlert.asStateFlow()

    fun dismissAlert() {
        _lastAlert.value = null
    }

    fun startScan() {
        if (_scanState.value is ScanState.Scanning) return

        viewModelScope.launch {
            _scanState.value = ScanState.Scanning(0, 0)
            try {
                repository.scanNetwork { progress, foundCount ->
                    _scanState.value = ScanState.Scanning(progress, foundCount)
                }
                
                // Read active devices from flow state safely to check for flagged intruders
                val activeList = devices.value.filter { it.isCurrentlyActive }
                val flaggedList = activeList.filter { it.status == "FLAGGED" }
                if (flaggedList.isNotEmpty()) {
                    _lastAlert.value = "WARNING: ${flaggedList.size} unapproved/flagged device(s) detected connected to your network!"
                }

                _scanState.value = ScanState.Success(activeList.size)
            } catch (e: Exception) {
                Log.e("HotspotViewModel", "Failed to scan", e)
                _scanState.value = ScanState.Error(e.localizedMessage ?: "Network scanning failed")
            }
        }
    }

    fun updateDevice(ipAddress: String, customLabel: String, deviceType: String, status: String) {
        viewModelScope.launch {
            repository.updateDeviceProfile(ipAddress, customLabel, deviceType, status)
        }
    }

    fun removeDevice(ipAddress: String) {
        viewModelScope.launch {
            repository.deleteDevice(ipAddress)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }
}
