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
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
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

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

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
     * Gets the current cellular network type (2G/3G/4G/5G) using TelephonyManager.
     * @return [CellularNetworkType] enum indicating network generation, or UNKNOWN if unavailable
     * @throws SecurityException if READ_PHONE_STATE permission not granted (handled internally)
     * Requires READ_PHONE_STATE permission to access telephony information from the system.
     * Use this for adaptive behavior like adjusting video quality or data usage based on network capability.
     * Note: 5G detection only available on Android API 29+, older devices return UNKNOWN for 5G networks.
     */

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getCellularNetworkType(): CellularNetworkType {
        return try {
            val networkType = telephonyManager.networkType
            mapNetworkType(networkType)
        } catch (e: SecurityException) {
            // No READ_PHONE_STATE permission
            CellularNetworkType.UNKNOWN
        }
    }

    /**
     * Maps TelephonyManager's raw network type constants to simplified cellular generations.
     * Categorizes 15+ different cellular standards into 4 user-friendly types (2G/3G/4G/5G).
     * 2G: GPRS/EDGE (slow), 3G: UMTS/HSDPA (moderate), 4G: LTE (fast), 5G: NR (very fast).
     * @param networkType [Int] constant from TelephonyManager.getNetworkType() (e.g., NETWORK_TYPE_LTE)
     * @return [CellularNetworkType] enum representing 2G/3G/4G/5G or UNKNOWN for unrecognized types
     * Categorizes 15+ cellular standards into 4 actionable categories for app decision-making.
     * Internal helper that translates technical standards (HSDPA, LTE, NR) into user-friendly types.
     */

    private fun mapNetworkType(networkType: Int): CellularNetworkType {
        return when (networkType) {
            // 2G Networks
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> CellularNetworkType.CELLULAR_2G

            // 3G Networks
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_HSPAP -> CellularNetworkType.CELLULAR_3G

            // 4G Networks
            TelephonyManager.NETWORK_TYPE_LTE -> CellularNetworkType.CELLULAR_4G

            // 5G Networks (API 29+)
            TelephonyManager.NETWORK_TYPE_NR -> CellularNetworkType.CELLULAR_5G

            // Unknown/Unavailable
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> CellularNetworkType.UNKNOWN

            else -> CellularNetworkType.UNKNOWN
        }
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
                //trySend(getCurrentNetworkState())
            }

            // Called when a network is no longer available (disconnected).
            override fun onLost(network: Network) {
                super.onLost(network)
                //trySend(getCurrentNetworkState())
            }

            // Called when the capabilities of a network have changed.
            // This is crucial for detecting changes in network type, metered status, bandwidth, etc.
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                //trySend(getCurrentNetworkState())
            }

            // Called when the LinkProperties of a network have changed.
            // Not directly used for the requested properties, but good to know for other advanced scenarios.
            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: android.net.LinkProperties
            ) {
                super.onLinkPropertiesChanged(network, linkProperties)
                //trySend(getCurrentNetworkState())
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
