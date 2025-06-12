package `in`.gauthama.network_monitor.models


data class NetworkState(
    val type: NetworkType,
    val isMetered: Boolean,
    val downloadBandwidthKbps: Int?,
    val uploadBandwidthKbps: Int?
)

