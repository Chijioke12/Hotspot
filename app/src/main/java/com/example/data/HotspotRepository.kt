package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale

class HotspotRepository(
    private val context: Context,
    private val dao: HotspotDao
) {
    val allDevices: Flow<List<HotspotDevice>> = dao.getAllDevicesFlow()
    val allLogs: Flow<List<HotspotLog>> = dao.getAllLogsFlow()

    private val tag = "HotspotRepository"

    suspend fun updateDeviceProfile(ip: String, label: String, type: String, status: String) {
        dao.updateDeviceProfile(ip, label, type, status)
    }

    suspend fun deleteDevice(ip: String) {
        dao.deleteDevice(ip)
    }

    suspend fun clearLogs() {
        dao.clearLogs()
    }

    // Retrieve active IPv4 subnets from network interfaces
    fun getLocalSubnets(): List<String> {
        val subnets = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        // We check for IPv4 addresses
                        if (ip.contains(".")) {
                            Log.d(tag, "Found interface: ${networkInterface.name} IP: $ip")
                            // Typically, we extract the subnet class C prefix
                            val parts = ip.split(".")
                            if (parts.size == 4) {
                                val subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                                if (!subnets.contains(subnetPrefix) && parts[0] != "127") {
                                    subnets.add(subnetPrefix)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading network interfaces", e)
        }

        // Add typical hotspot configurations to scan if we didn't find them, or as default helpers
        val defaults = listOf("192.168.43", "192.168.50", "192.168.49")
        for (default in defaults) {
            if (!subnets.contains(default)) {
                subnets.add(default)
            }
        }

        return subnets
    }

    // Scanning 1 IP in the subnet
    private suspend fun scanAddress(
        subnetPrefix: String,
        host: Int,
        activeDevices: MutableList<HotspotDevice>
    ) = withContext(Dispatchers.IO) {
        val ip = "$subnetPrefix.$host"
        val startTime = System.currentTimeMillis()
        var reachable = false
        var latency: Long = -1

        try {
            val inet = InetAddress.getByName(ip)
            // 1. Try traditional ICMP ping/reachability (200ms timeout)
            if (inet.isReachable(200)) {
                reachable = true
                latency = System.currentTimeMillis() - startTime
            } else {
                // 2. Fallback: try socket connect to common ports (80, 443, 22, 139, 445, 8080)
                val testPorts = listOf(80, 139, 443, 22, 8080)
                for (port in testPorts) {
                    if (reachable) break
                    val socket = Socket()
                    try {
                        socket.connect(InetSocketAddress(ip, port), 120)
                        reachable = true
                        latency = System.currentTimeMillis() - startTime
                    } catch (e: Exception) {
                        // ignore and try next port
                    } finally {
                        try { socket.close() } catch (ignored: Exception) {}
                    }
                }
            }

            if (reachable) {
                // Determine hostname if possible
                var hostname = inet.canonicalHostName ?: ""
                if (hostname == ip) {
                    hostname = inet.hostName ?: ""
                }
                if (hostname == ip) {
                    hostname = "Device-$host"
                }

                // Attempt to parse MAC address from ARP table
                val mac = getMacFromArpTable(ip) ?: generateVirtualMac(ip, hostname)

                val device = HotspotDevice(
                    ipAddress = ip,
                    macAddress = mac,
                    hostname = hostname,
                    isCurrentlyActive = true,
                    lastSeen = System.currentTimeMillis(),
                    responseTimeMs = latency
                )
                synchronized(activeDevices) {
                    activeDevices.add(device)
                }
            }
        } catch (e: Exception) {
            // Address not reachable or other network issue
            Log.v(tag, "Skip IP $ip: ${e.message}")
        }
    }

    // Main scan function
    suspend fun scanNetwork(onProgress: (Int, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            // Set all existing devices to inactive before starting fresh scan so we can update active connections
            dao.setAllDevicesInactive(false)

            val subnets = getLocalSubnets()
            val activeDevices = mutableListOf<HotspotDevice>()
            
            // Limit scanning to standard Wi-Fi local gateways of interest to make it super fast and reliable
            // We scan the subnets in parallel
            val totalSteps = subnets.size * 254
            var currentStep = 0
            val scanLock = Any()

            coroutineScope {
                val jobs = mutableListOf<Deferred<Unit>>()
                for (subnet in subnets) {
                    for (host in 1..254) {
                        val job = async {
                            scanAddress(subnet, host, activeDevices)
                            synchronized(scanLock) {
                                currentStep++
                                val progress = ((currentStep.toFloat() / totalSteps.toFloat()) * 100).toInt()
                                onProgress(progress, activeDevices.size)
                            }
                        }
                        jobs.add(job)
                    }
                }
                jobs.awaitAll()
            }

            // Save active devices to DB and handle logs/alerts
            for (scannedDevice in activeDevices) {
                val existingDevice = dao.getDeviceByIp(scannedDevice.ipAddress)
                    ?: dao.getDeviceByMac(scannedDevice.macAddress)

                if (existingDevice == null) {
                    // This is a brand new device! Log it and sound alert if flagged
                    val initialLabel = getDeviceVendorFromMac(scannedDevice.macAddress) + " Device"
                    val guestType = guessDeviceType(scannedDevice.hostname)
                    val newDevice = scannedDevice.copy(
                        customLabel = initialLabel,
                        deviceType = guestType,
                        firstDiscovered = System.currentTimeMillis()
                    )
                    dao.insertOrUpdateDevice(newDevice)

                    dao.insertLog(
                        HotspotLog(
                            ipAddress = newDevice.ipAddress,
                            macAddress = newDevice.macAddress,
                            hostname = newDevice.hostname,
                            deviceLabel = newDevice.customLabel,
                            eventType = "CONNECTED"
                        )
                    )
                } else {
                    // Device already known, update and preserve custom label/type/status
                    val updatedDevice = scannedDevice.copy(
                        customLabel = existingDevice.customLabel.ifEmpty { getDeviceVendorFromMac(scannedDevice.macAddress) + " Device" },
                        deviceType = existingDevice.deviceType.ifEmpty { guessDeviceType(scannedDevice.hostname) },
                        status = existingDevice.status,
                        firstDiscovered = existingDevice.firstDiscovered
                    )
                    dao.insertOrUpdateDevice(updatedDevice)

                    // If it was inactive but is now active, log connection
                    if (!existingDevice.isCurrentlyActive) {
                        dao.insertLog(
                            HotspotLog(
                                ipAddress = updatedDevice.ipAddress,
                                macAddress = updatedDevice.macAddress,
                                hostname = updatedDevice.hostname,
                                deviceLabel = updatedDevice.customLabel,
                                eventType = "CONNECTED"
                            )
                        )
                    }

                    // Check if it is a FLAGGED intruder! Log alert
                    if (existingDevice.status == "FLAGGED") {
                        dao.insertLog(
                            HotspotLog(
                                ipAddress = updatedDevice.ipAddress,
                                macAddress = updatedDevice.macAddress,
                                hostname = updatedDevice.hostname,
                                deviceLabel = updatedDevice.customLabel,
                                eventType = "ALERT_TRIGGERED"
                            )
                        )
                    }
                }
            }
        }
    }

    // Parse ARP table to read MAC address associated with IP
    private fun getMacFromArpTable(ip: String): String? {
        var reader: BufferedReader? = null
        try {
            val file = File("/proc/net/arp")
            if (!file.exists()) return null
            reader = BufferedReader(FileReader(file))
            var line: String? = reader.readLine() // Skip header
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (tokens.size >= 4) {
                    val arpIp = tokens[0]
                    val arpMac = tokens[3]
                    if (arpIp.equals(ip, ignoreCase = true)) {
                        // Validate MAC format
                        if (arpMac.matches("..:..:..:..:..:..".toRegex()) && arpMac != "00:00:00:00:00:00") {
                            return arpMac.lowercase(Locale.ROOT)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing ARP table", e)
        } finally {
            try { reader?.close() } catch (ignored: Exception) {}
        }
        return null
    }

    // Fallback: stable virtual MAC based on IP and hostname hash
    private fun generateVirtualMac(ip: String, hostname: String): String {
        val uniqueString = "$ip|$hostname"
        val hash = uniqueString.hashCode()
        val h1 = String.format("%02x", (hash shr 16) and 0xFF)
        val h2 = String.format("%02x", (hash shr 8) and 0xFF)
        val h3 = String.format("%02x", hash and 0xFF)
        // Generates a local/private unicast MAC prefix (starts with 02, 06, 0a, 0e...)
        return "1e:a9:fc:$h1:$h2:$h3"
    }

    // Basic heuristic lookup for MAC address vendor prefixes
    fun getDeviceVendorFromMac(mac: String): String {
        val prefix = mac.take(8).lowercase(Locale.ROOT)
        return when (prefix) {
            "00:1a:11", "1e:a9:fc" -> "Virtual Host"
            "00:03:93", "00:0d:93", "00:10:fa", "00:14:51", "00:16:cb" -> "Apple"
            "00:1c:b3", "00:1e:c2", "00:21:5c", "00:23:12", "00:25:00" -> "Apple"
            "00:1f:3b", "04:18:0f", "04:26:65", "08:1c:4b", "0c:3e:9f", "10:1c:0c" -> "Samsung"
            "00:1a:11", "18:cf:24", "20:34:fb", "24:da:9b", "34:e6:ad", "44:80:eb" -> "Google"
            "00:0e:35", "00:13:02", "00:15:00", "00:16:76", "00:19:d1", "00:1a:80" -> "Intel"
            "00:12:f0", "00:17:31", "00:19:70", "00:1a:70", "00:1e:65", "00:21:63" -> "Intel"
            "00:22:15", "00:24:d6", "00:24:d7", "00:28:f8", "10:0b:a9", "18:5e:0f" -> "Intel"
            "08:00:27" -> "VirtualBox"
            "00:05:69", "00:0c:29", "00:1c:14", "00:50:56" -> "VMware"
            "3c:15:c2", "3c:25:d9", "38:4b:76", "70:f3:95", "74:60:fa" -> "Xiaomi"
            "00:26:37", "00:def:bb", "40:83:de", "48:5a:3f", "50:a4:c8", "54:5d:a2" -> "Huawei"
            else -> "Generic"
        }
    }

    // Guess device type based on hostname
    private fun guessDeviceType(hostname: String): String {
        val nameLower = hostname.lowercase(Locale.ROOT)
        return when {
            nameLower.contains("phone") || nameLower.contains("iphone") || nameLower.contains("android") || nameLower.contains("galaxy") || nameLower.contains("pixel") -> "MOBILE"
            nameLower.contains("pad") || nameLower.contains("ipad") || nameLower.contains("tablet") -> "TABLET"
            nameLower.contains("pc") || nameLower.contains("lap") || nameLower.contains("mac") || nameLower.contains("desk") || nameLower.contains("window") || nameLower.contains("ubuntu") -> "LAPTOP"
            nameLower.contains("tv") || nameLower.contains("television") || nameLower.contains("apple-tv") || nameLower.contains("chromecast") || nameLower.contains("fire") || nameLower.contains("roku") -> "TV"
            nameLower.contains("smart") || nameLower.contains("alexa") || nameLower.contains("echo") || nameLower.contains("bulb") || nameLower.contains("plug") || nameLower.contains("home") || nameLower.contains("watch") -> "SMART_HOME"
            else -> "UNKNOWN"
        }
    }
}
