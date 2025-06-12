package `in`.gauthama.network_monitor

import `in`.gauthama.network_monitor.models.NetworkState
import `in`.gauthama.network_monitor.models.NetworkType
import kotlinx.coroutines.flow.Flow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.WiFiNetworkDetector.WiFiInfo
import `in`.gauthama.network_monitor.models.CellularNetworkType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * A utility class to monitor network state changes, identify network type,
 * check metered status, and get bandwidth estimates.
 *
 * Requires the `android.permission.ACCESS_NETWORK_STATE` permission in `AndroidManifest.xml`.
 *
 * @param context The application or activity context.
 */
class NetworkStateMonitor(private val context: Context) {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private val wifiDetector = WiFiNetworkDetector(context)
    private val cellularNetworkDetector = CellularNetworkDetector(context)


    /**
     * Checks for the `ACCESS_NETWORK_STATE` permission.
     * @return True if the permission is granted, false otherwise.
     */
    private fun hasNetworkStatePermission(): Boolean {
        return context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Retrieves the current network type.
     *
     * @return The [NetworkType] (WIFI, MOBILE, NONE, ETHERNET) of the currently active network.
     * Returns [NetworkType.NONE] if no active network or permission is not granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getCurrentNetworkType(): NetworkType {
        if (!hasNetworkStatePermission()) {
            return NetworkType.NONE // Permission not granted
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities =
            connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }
    }


    /**
     * Retrieves the current cellular network generation (2G/3G/4G/5G) from the device's telephony service.
     * @return [CellularNetworkType] indicating cellular generation or UNKNOWN if unavailable/no permission
     * @throws SecurityException if READ_PHONE_STATE permission not granted (handled internally)
     * Delegates to internal CellularNetworkDetector for detailed network type classification.
     * Note: Returns UNKNOWN when not connected to cellular or permission denied.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getCellularNetworkType(): CellularNetworkType {
        return cellularNetworkDetector.getCellularNetworkType()
    }


    /**
     * Retrieves comprehensive WiFi network information including standard, frequency, and signal quality.
     * @return [WiFiInfo] with detailed network data, or null if WiFi unavailable/disconnected
     * Requires ACCESS_FINE_LOCATION permission on Android 6+ to access SSID information.
     * Delegates to internal WiFiNetworkDetector for complete WiFi analysis and capabilities assessment.
     * Returns null when WiFi disabled, not connected, or location permission missing.
     */
    fun getWifiInfo(): WiFiInfo?{
        return wifiDetector.getWiFiInfo()
    }


    /**
     * Retrieves the estimated download bandwidth of the current active network.
     * This is an estimate provided by the system and may not reflect actual real-time bandwidth.
     *
     * @return The estimated download bandwidth in kilobits per second (Kbps), or 0 if
     * no active network, capabilities are not available, or permission is not granted.
     * Returns Long as per user's request, but underlying API returns Int.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getBandwidthEstimate(): Long {
        if (!hasNetworkStatePermission()) {
            return 0L // Permission not granted
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return 0L
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return 0L

        // LINK_DOWNSTREAM_BANDWIDTH_KBPS provides an estimate of the downstream bandwidth in Kbps
        return capabilities.linkDownstreamBandwidthKbps.toLong()
    }

    /**
     * Checks if the currently active network connection is metered.
     * Metered connections typically incur data charges (e.g., mobile data).
     *
     * @return True if the active network is metered, false otherwise (e.g., Wi-Fi),
     * or if there's no active network or permission is not granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isMeteredConnection(): Boolean {
        if (!hasNetworkStatePermission()) {
            return false // Permission not granted
        }
        return connectivityManager.isActiveNetworkMetered
    }

    /**
     * Gets the current comprehensive network state including type, metered status, and bandwidth.
     * This is a helper function used internally and by the flow.
     *
     * @return A [NetworkState] object representing the current network conditions.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun getCurrentNetworkState(): NetworkState {
        if (!hasNetworkStatePermission()) {
            return NetworkState(NetworkType.NONE, false, null, null)
        }

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val networkType = when {
            capabilities == null -> NetworkType.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.NONE
        }

        val isMetered = connectivityManager.isActiveNetworkMetered
        val downloadBandwidth = capabilities?.linkDownstreamBandwidthKbps
        val uploadBandwidth = capabilities?.linkUpstreamBandwidthKbps

        return NetworkState(
            type = networkType,
            isMetered = isMetered,
            downloadBandwidthKbps = downloadBandwidth,
            uploadBandwidthKbps = uploadBandwidth
        )
    }


    /**
     * Observes real-time changes in the network state.
     * Emits a new [NetworkState] object whenever the network connection status,
     * capabilities, or properties change.
     *
     * This [Flow] will emit the current network state immediately upon collection,
     * and then subsequently emit updates as they occur.
     *
     * Requires the `android.permission.ACCESS_NETWORK_STATE` permission.
     *
     * @return A [Flow] of [NetworkState] objects representing ongoing network changes.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun observeNetworkChanges(): Flow<NetworkState> = callbackFlow {
        if (!hasNetworkStatePermission()) {
            // If permission is not granted, immediately send a default state and close the flow.
            trySend(NetworkState(NetworkType.NONE, false, null, null))
            awaitClose { /* Nothing to unregister */ }
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            // Called when a network becomes available (connected).
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                trySend(getCurrentNetworkState())
            }

            // Called when a network is no longer available (disconnected).
            override fun onLost(network: Network) {
                super.onLost(network)
                trySend(getCurrentNetworkState())
            }

            // Called when the capabilities of a network have changed.
            // This is crucial for detecting changes in network type, metered status, bandwidth, etc.
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                trySend(getCurrentNetworkState())
            }

            // Called when the LinkProperties of a network have changed.
            // Not directly used for the requested properties, but good to know for other advanced scenarios.
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                super.onLinkPropertiesChanged(network, linkProperties)
                trySend(getCurrentNetworkState())
            }
        }

        // Build a NetworkRequest to listen for all types of networks.
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // Register the network callback.
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Ensure that the callback is unregistered when the flow is cancelled or no longer collected.
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
        // Emit the current state immediately when the flow starts, and filter out identical consecutive states.
        .onStart { emit(getCurrentNetworkState()) }
        .distinctUntilChanged() // Only emit if the state actually changes.
}
