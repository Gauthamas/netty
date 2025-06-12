package `in`.gauthama.network_monitor

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

class WiFiNetworkDetector(private val context: Context) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    data class WiFiInfo(
        val standard: WiFiStandard,        // 802.11n, 802.11ac, 802.11ax (WiFi 6)
        val frequency: WiFiFrequency,      // 2.4GHz vs 5GHz
        val signalStrength: SignalStrength, // Excellent/Good/Fair/Poor
        val linkSpeed: Int,                // Current link speed in Mbps
        val ssid: String,                  // Network name
        val security: WiFiSecurity         // WPA2, WPA3, Open, etc.
    )

    /**
     * Gets detailed WiFi network information including standard, frequency, and signal quality.
     * @return [WiFiInfo] with comprehensive network details, or null if WiFi not connected
     * Requires ACCESS_WIFI_STATE permission (normal permission, auto-granted)
     */
    fun getWiFiInfo(): WiFiInfo? {
        if (!wifiManager.isWifiEnabled) return null

        val connectionInfo = wifiManager.connectionInfo ?: return null

        // Check if actually connected (SSID will be <unknown ssid> if not)
        if (connectionInfo.ssid == WifiManager.UNKNOWN_SSID) return null

        return WiFiInfo(
            standard = determineWiFiStandard(connectionInfo),
            frequency = determineFrequency(connectionInfo.frequency),
            signalStrength = determineSignalStrength(connectionInfo.rssi),
            linkSpeed = connectionInfo.linkSpeed, // Already in Mbps
            ssid = connectionInfo.ssid.removeSurrounding("\""), // Remove quotes
            security = determineSecurityType(connectionInfo)
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

// Enums for WiFi information
enum class WiFiStandard(val displayName: String, val maxSpeed: Int) {
    WIFI_1_B("802.11b", 11),
    WIFI_3_G("802.11g", 54),
    WIFI_3_A("802.11a", 54),
    WIFI_4_N("802.11n (WiFi 4)", 300),
    WIFI_5_AC("802.11ac (WiFi 5)", 1300),
    WIFI_6_AX("802.11ax (WiFi 6)", 9600),
    UNKNOWN("Unknown", 0)
}

enum class WiFiFrequency(val displayName: String) {
    BAND_2_4_GHZ("2.4 GHz"),
    BAND_5_GHZ("5 GHz"),
    BAND_6_GHZ("6 GHz"), // WiFi 6E
    UNKNOWN("Unknown")
}

enum class SignalStrength(val displayName: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    VERY_POOR("Very Poor")
}

enum class WiFiSecurity {
    OPEN, WEP, WPA, WPA2, WPA3, UNKNOWN
}