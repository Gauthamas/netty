package `in`.gauthama.network_monitor.models

enum class NetworkType {
    /** Connected via Wi-Fi. */
    WIFI,
    /** Connected via mobile data (e.g., 4G, 5G). */
    MOBILE,
    /** No active network connection. */
    NONE,
    /** Connected via Ethernet. */
    ETHERNET
}

enum class CellularNetworkType {
    CELLULAR_2G,
    CELLULAR_3G,
    CELLULAR_4G,
    CELLULAR_5G,
    UNKNOWN
}

