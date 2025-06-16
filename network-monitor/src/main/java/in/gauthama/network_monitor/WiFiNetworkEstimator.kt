package `in`.gauthama.network_monitor

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import `in`.gauthama.network_monitor.models.SignalStrength
import `in`.gauthama.network_monitor.models.WiFiFrequency
import `in`.gauthama.network_monitor.models.WiFiSecurity
import `in`.gauthama.network_monitor.models.WiFiStandard


/**
 * Estimates WiFi bandwidth using industry-typical efficiency factors.
 * Note: Coefficients are approximations and may not reflect actual conditions.
 * Real-world performance varies significantly based on environment and usage.
 */
class WiFiNetworkEstimator(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    data class WiFiInfo(
        val standard: WiFiStandard,        // 802.11n, 802.11ac, 802.11ax (WiFi 6)
        val frequency: WiFiFrequency,      // 2.4GHz vs 5GHz
        val signalStrength: SignalStrength, // Excellent/Good/Fair/Poor
        val linkSpeed: Int,                // Current link speed in Mbps
        val ssid: String,                  // Network name
        val security: WiFiSecurity,         // WPA2, WPA3, Open, etc.
        val rssi: Int  // ← Add this! Actual RSSI value in dBm
    )

    /**
     * Estimates realistic WiFi bandwidth based on actual signal strength, frequency, and standard.
     * @return [Long] estimated bandwidth in Kbps accounting for signal quality and real-world conditions
     * Combines theoretical WiFi capabilities with actual RSSI measurements for dynamic estimation.
     * Returns null if WiFi not connected or detailed information unavailable.
     * Uses conservative estimate between theoretical calculation and system-reported link speed.
     */
    fun estimateBandwidth(): Long? {
        val wifiInfo = getWiFiInfo() ?: return null
        return estimateWiFiBandwidth(wifiInfo)
    }

    /**
     * Estimates realistic WiFi bandwidth based on actual signal strength, frequency, and WiFi standard.
     * @param wifiInfo [WiFiInfo] containing RSSI, frequency band, standard, and link speed details
     * @return [Long] estimated bandwidth in Kbps accounting for signal quality and real-world conditions
     * Combines theoretical WiFi capabilities with actual signal strength (-30 to -80 dBm) for dynamic estimation.
     * Returns conservative estimate between theoretical calculation and system-reported link speed.
     */
    private fun estimateWiFiBandwidth(wifiInfo: WiFiInfo): Long {
        // Get actual signal strength (RSSI)
        val actualRssi = wifiInfo.rssi // This should come from your WiFiInfo

        // Base speed from WiFi standard and frequency
        val theoreticalMax = when {
            wifiInfo.frequency.name.contains("5_GHZ") && wifiInfo.standard.maxSpeed >= 400 -> 400_000L // WiFi 5/6 on 5GHz
            wifiInfo.frequency.name.contains("5_GHZ") -> 150_000L // Older WiFi on 5GHz
            else -> 72_000L // 2.4GHz WiFi
        }

        // Dynamic signal quality multiplier based on actual RSSI
        val signalQuality = when {
            actualRssi >= -30 -> 0.95  // Excellent: -30 dBm or better
            actualRssi >= -50 -> 0.80  // Good: -30 to -50 dBm
            actualRssi >= -60 -> 0.60  // Fair: -50 to -60 dBm
            actualRssi >= -70 -> 0.35  // Poor: -60 to -70 dBm
            actualRssi >= -80 -> 0.15  // Very Poor: -70 to -80 dBm
            else -> 0.05               // Barely connected: < -80 dBm
        }

        // Use actual link speed as upper bound (system-reported negotiated speed)
        val linkSpeedKbps = wifiInfo.linkSpeed * 1000L
        val theoreticalEstimate = (theoreticalMax * signalQuality * 0.7).toLong() // 70% efficiency

        // Return the more conservative estimate
        return minOf(linkSpeedKbps, theoreticalEstimate)
    }


    /**
     * Gets detailed WiFi network information including standard, frequency, and signal quality.
     * @return [WiFiInfo] with comprehensive network details, or null if WiFi not connected
     * Requires ACCESS_WIFI_STATE permission (normal permission, auto-granted)
     */
    fun getWiFiInfo(): WiFiInfo? {
        if (!wifiManager.isWifiEnabled) return null

        val connectionInfo = wifiManager.connectionInfo ?: return null

        // Check if actually connected (SSID will be <unknown ssid> if not)
        //if (connectionInfo.ssid == WifiManager.UNKNOWN_SSID) return null

        return WiFiInfo(
            standard = determineWiFiStandard(connectionInfo),
            frequency = determineFrequency(connectionInfo.frequency),
            signalStrength = determineSignalStrength(connectionInfo.rssi),
            linkSpeed = connectionInfo.linkSpeed, // Already in Mbps
            ssid = "Hidden",//connectionInfo.ssid.removeSurrounding("\""), // Remove quotes
            security = determineSecurityType(connectionInfo),
            rssi = connectionInfo.rssi // ← Add this!
        )
    }

    /**
     * Determines WiFi standard (802.11n/ac/ax) based on connection capabilities.
     * Uses frequency and link speed to estimate the WiFi generation.
     */
    private fun determineWiFiStandard(connectionInfo: WifiInfo): WiFiStandard {
        val frequency = connectionInfo.frequency
        val linkSpeed = connectionInfo.linkSpeed

        return when {
            // WiFi 6 (802.11ax) - typically 600+ Mbps, both bands
            linkSpeed >= 600 -> WiFiStandard.WIFI_6_AX

            // WiFi 5 (802.11ac) - 5GHz only, 433+ Mbps typical
            frequency > 5000 && linkSpeed >= 200 -> WiFiStandard.WIFI_5_AC

            // WiFi 4 (802.11n) - both bands, up to 150-300 Mbps
            linkSpeed >= 50 -> WiFiStandard.WIFI_4_N

            // Older standards (802.11g/b/a)
            frequency > 5000 -> WiFiStandard.WIFI_3_A  // 5GHz, older
            linkSpeed >= 20 -> WiFiStandard.WIFI_3_G   // 2.4GHz, 54 Mbps max
            else -> WiFiStandard.WIFI_1_B              // 2.4GHz, 11 Mbps max
        }
    }

    /**
     * Determines frequency band (2.4GHz vs 5GHz) from frequency value.
     * 2.4GHz: 2400-2500 MHz range
     * 5GHz: 5000-6000 MHz range
     */
    private fun determineFrequency(frequencyMhz: Int): WiFiFrequency {
        return when {
            frequencyMhz in 2400..2500 -> WiFiFrequency.BAND_2_4_GHZ
            frequencyMhz in 5000..6000 -> WiFiFrequency.BAND_5_GHZ
            frequencyMhz in 6000..7000 -> WiFiFrequency.BAND_6_GHZ // WiFi 6E
            else -> WiFiFrequency.UNKNOWN
        }
    }

    /**
     * Maps RSSI (signal strength) to user-friendly quality levels.
     * RSSI is measured in dBm (negative values, closer to 0 = stronger)
     */
    private fun determineSignalStrength(rssi: Int): SignalStrength {
        return when {
            rssi >= -30 -> SignalStrength.EXCELLENT  // Very close to router
            rssi >= -50 -> SignalStrength.GOOD       // Good signal
            rssi >= -70 -> SignalStrength.FAIR       // Usable signal
            rssi >= -80 -> SignalStrength.POOR       // Weak but connected
            else -> SignalStrength.VERY_POOR         // Barely connected
        }
    }

    /**
     * Attempts to determine security type from network configuration.
     * Note: Limited information available from WifiInfo on newer Android versions
     */
    private fun determineSecurityType(connectionInfo: WifiInfo): WiFiSecurity {
        // On newer Android versions, security info is limited due to privacy
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WiFiSecurity.UNKNOWN // Privacy restrictions
        } else {
            // For older versions, could check WifiConfiguration
            WiFiSecurity.UNKNOWN
        }
    }
}