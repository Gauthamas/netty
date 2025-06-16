package `in`.gauthama.network_monitor.models

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