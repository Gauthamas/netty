package `in`.gauthama.network_monitor

import `in`.gauthama.network_monitor.models.NetworkState
import `in`.gauthama.network_monitor.models.NetworkType
import kotlinx.coroutines.flow.Flow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.WiFiNetworkDetector.WiFiInfo
import `in`.gauthama.network_monitor.models.BatteryImpact
import `in`.gauthama.network_monitor.models.CellularNetworkType
import `in`.gauthama.network_monitor.models.DataCostImpact
import `in`.gauthama.network_monitor.models.ImageQuality
import `in`.gauthama.network_monitor.models.NetworkQuality
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

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

    @Volatile
    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private val _networkState = MutableSharedFlow<NetworkState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )


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
    fun getWifiInfo(): WiFiInfo? {
        return wifiDetector.getWiFiInfo()
    }

    /**
     * Gets enhanced bandwidth estimate using cellular/WiFi intelligence instead of unreliable system values.
     * @return [Long] realistic bandwidth estimate in Kbps based on network type and signal quality
     * Delegates to specialized detectors for accurate network-specific bandwidth calculations.
     * Provides fallback estimates when detailed network information unavailable due to permissions.
     * Returns 0 for offline connections or when no network detection possible.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getEnhancedBandwidthEstimate(): Long {
        return when (getCurrentNetworkType()) {
            NetworkType.WIFI -> {
                wifiDetector.estimateBandwidth() ?: getBandwidthEstimate()
            }

            NetworkType.MOBILE -> {
                cellularNetworkDetector.estimateBandwidth()
            }

            else -> 0L
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
     * Provides intelligent suggestions for app behavior based on current network conditions.
     * @return [NetworkSuggestions] with actionable guidance for optimal user experience
     * Combines network type, bandwidth estimates, signal quality, and metered status for decisions.
     * Helps developers make smart choices about data usage, quality settings, and operation timing.
     * Optimizes for user experience while respecting data costs and battery life.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkSuggestions(): NetworkSuggestions {
        val enhancedBandwidth = getEnhancedBandwidthEstimate()
        val isMetered = isMeteredConnection()
        val networkQuality = assessNetworkQuality(getCurrentNetworkType(), enhancedBandwidth)

        return NetworkSuggestions(
            canStreamHDVideo = canHandleHDVideo(enhancedBandwidth, networkQuality, isMetered),
            canMakeVideoCalls = canHandleVideoCalls(enhancedBandwidth, networkQuality),
            shouldDeferLargeDownloads = shouldDeferLargeOperations(
                isMetered,
                networkQuality,
                enhancedBandwidth
            ),
            shouldDeferLargeUploads = shouldDeferLargeOperations(
                isMetered,
                networkQuality,
                enhancedBandwidth,
                isUpload = true
            ),
            suggestedImageQuality = getSuggestedImageQuality(
                enhancedBandwidth,
                isMetered,
                networkQuality
            ),
            suggestedVideoQuality = getSuggestedVideoQuality(
                enhancedBandwidth,
                isMetered,
                networkQuality
            ),
            batteryImpact = assessBatteryImpact(getCurrentNetworkType(), networkQuality),
            dataCostImpact = assessDataCostImpact(isMetered, getCurrentNetworkType()),
            maxSuggestedFileSize = getMaxSuggestedFileSize(
                isMetered,
                enhancedBandwidth,
                networkQuality
            ),
            batchOperations = shouldBatchOperations(isMetered, networkQuality)
        )
    }


    private fun canHandleHDVideo(
        bandwidth: Long,
        quality: NetworkQuality,
        isMetered: Boolean
    ): Boolean {
        return bandwidth >= 5_000L && // 5 Mbps minimum for HD
                quality != NetworkQuality.POOR &&
                !isMetered // Ni HD video on cellular
    }

    private fun canHandleVideoCalls(bandwidth: Long, quality: NetworkQuality): Boolean {
        return bandwidth >= 1_000L && // 1 Mbps minimum for video calls
                quality != NetworkQuality.POOR
    }

    private fun shouldDeferLargeOperations(
        isMetered: Boolean,
        quality: NetworkQuality,
        bandwidth: Long,
        isUpload: Boolean = false
    ): Boolean {
        return when {
            // Always defer large operations on poor networks
            quality == NetworkQuality.POOR -> true

            // Defer on metered connections with low bandwidth
            isMetered && bandwidth < 10_000L -> true

            // Be more conservative with uploads (they're often slower)
            isUpload && isMetered && bandwidth < 5_000L -> true

            else -> false
        }
    }

    private fun getSuggestedImageQuality(
        bandwidth: Long,
        isMetered: Boolean,
        quality: NetworkQuality
    ): ImageQuality {
        return when {
            // Poor network = low quality always
            quality == NetworkQuality.POOR -> ImageQuality.LOW

            // Excellent unmetered = high quality
            !isMetered && quality == NetworkQuality.EXCELLENT -> ImageQuality.HIGH

            // Good bandwidth unmetered = high quality
            !isMetered && bandwidth >= 10_000L -> ImageQuality.HIGH

            // Decent bandwidth = medium quality
            bandwidth >= 2_000L -> ImageQuality.MEDIUM

            // Default to low quality
            else -> ImageQuality.LOW
        }
    }

    private fun getSuggestedVideoQuality(
        bandwidth: Long,
        isMetered: Boolean,
        quality: NetworkQuality
    ): VideoQuality {
        return when {
            // Very poor conditions = audio only
            quality == NetworkQuality.POOR || bandwidth < 500L -> VideoQuality.AUDIO_ONLY

            // Metered with low bandwidth = SD
            isMetered && bandwidth < 3_000L -> VideoQuality.SD_480P

            // Good unmetered connection = HD
            !isMetered && bandwidth >= 8_000L && quality == NetworkQuality.EXCELLENT -> VideoQuality.HD_1080P

            // Decent unmetered = 720p
            !isMetered && bandwidth >= 5_000L -> VideoQuality.HD_720P

            // Default to SD
            else -> VideoQuality.SD_480P
        }
    }

    private fun assessBatteryImpact(
        networkType: NetworkType,
        quality: NetworkQuality
    ): BatteryImpact {
        return when (networkType) {
            NetworkType.WIFI -> BatteryImpact.LOW
            NetworkType.MOBILE -> when (quality) {
                NetworkQuality.EXCELLENT -> BatteryImpact.MODERATE
                NetworkQuality.GOOD -> BatteryImpact.HIGH
                NetworkQuality.POOR -> BatteryImpact.SEVERE
                else -> BatteryImpact.MODERATE
            }

            NetworkType.ETHERNET -> BatteryImpact.MINIMAL
            else -> BatteryImpact.MINIMAL
        }
    }

    private fun assessDataCostImpact(isMetered: Boolean, networkType: NetworkType): DataCostImpact {
        return when {
            !isMetered -> DataCostImpact.FREE
            networkType == NetworkType.WIFI -> DataCostImpact.FREE
            else -> DataCostImpact.MODERATE // Assume moderate cost for cellular
        }
    }

    private fun getMaxSuggestedFileSize(
        isMetered: Boolean,
        bandwidth: Long,
        quality: NetworkQuality
    ): Long {
        return when {
            // Poor quality = small files only
            quality == NetworkQuality.POOR -> 1_000_000L // 1 MB

            // Metered connections = be conservative
            isMetered -> when {
                bandwidth >= 10_000L -> 10_000_000L // 10 MB
                bandwidth >= 5_000L -> 5_000_000L   // 5 MB
                else -> 2_000_000L                  // 2 MB
            }

            // Unmetered = larger files OK
            else -> when {
                bandwidth >= 20_000L -> 100_000_000L // 100 MB
                bandwidth >= 10_000L -> 50_000_000L  // 50 MB
                else -> 20_000_000L                  // 20 MB
            }
        }
    }

    private fun shouldBatchOperations(isMetered: Boolean, quality: NetworkQuality): Boolean {
        // Batch operations on poor networks or metered connections to be efficient
        return isMetered || quality == NetworkQuality.POOR
    }

    /**
     * Assesses overall network quality combining bandwidth, network type, and signal conditions.
     * @param networkType Current network type (WiFi, Cellular, etc.)
     * @param bandwidth Estimated bandwidth in Kbps
     * @return [NetworkQuality] indicating overall connection assessment
     * Provides simplified quality rating for easy decision-making in app logic.
     * Considers both raw bandwidth and network stability characteristics.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun assessNetworkQuality(networkType: NetworkType, bandwidth: Long): NetworkQuality {
        return when (networkType) {
            NetworkType.WIFI -> {
                // WiFi quality based on bandwidth and signal
                val wifiInfo = getWifiInfo()
                when {
                    bandwidth >= 50_000L && wifiInfo?.signalStrength == SignalStrength.EXCELLENT -> NetworkQuality.EXCELLENT
                    bandwidth >= 20_000L && wifiInfo?.signalStrength != SignalStrength.POOR -> NetworkQuality.GOOD
                    bandwidth >= 5_000L -> NetworkQuality.FAIR
                    bandwidth > 0L -> NetworkQuality.POOR
                    else -> NetworkQuality.OFFLINE
                }
            }

            NetworkType.MOBILE -> {
                // Cellular quality based on generation and bandwidth
                val cellularType = getCellularNetworkType()

                when {
                    cellularType == CellularNetworkType.CELLULAR_5G && bandwidth >= 30_000L -> NetworkQuality.EXCELLENT
                    cellularType == CellularNetworkType.CELLULAR_4G && bandwidth >= 15_000L -> NetworkQuality.GOOD
                    cellularType == CellularNetworkType.CELLULAR_4G && bandwidth >= 5_000L -> NetworkQuality.FAIR
                    cellularType == CellularNetworkType.CELLULAR_3G && bandwidth >= 1_000L -> NetworkQuality.FAIR
                    bandwidth > 0L -> NetworkQuality.POOR
                    else -> NetworkQuality.OFFLINE
                }
            }

            NetworkType.ETHERNET -> {
                // Ethernet typically excellent
                when {
                    bandwidth >= 50_000L -> NetworkQuality.EXCELLENT
                    bandwidth >= 10_000L -> NetworkQuality.GOOD
                    bandwidth > 0L -> NetworkQuality.FAIR
                    else -> NetworkQuality.OFFLINE
                }
            }

            NetworkType.NONE -> NetworkQuality.OFFLINE
            else -> NetworkQuality.UNKNOWN
        }
    }

    /**
     * Checks if device has actual internet connectivity beyond just network connection.
     * @return [Boolean] true if can reach internet, false if only local network connectivity
     * Performs lightweight connectivity test to validate real internet access.
     * Used to prevent false positive cases when network connected but no internet.
     * Includes timeout to avoid blocking UI thread during network issues.
     */
    suspend fun hasActualInternetConnectivity(): Boolean {
        return try {
            withTimeout(5000) { // 5 second timeout
                Log.d("InternetCheck", "Performing lightweight internet check...")
                var isInternetAvailable = performConnectivityTest()
                isInternetAvailable
            }
        } catch (e: Exception) {
            Log.e("InternetCheck", "Internet connectivity check failed: ${e.message}")
            false // Assume no internet if check fails
        }
    }

    /**
     * Performs lightweight network request to verify actual internet connectivity.
     */
    private suspend fun performConnectivityTest(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val url =
                    URL("https://www.google.com/generate_204") // Google's connectivity check URL
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "HEAD"
                connection.useCaches = false

                val responseCode = connection.responseCode
                connection.disconnect()

                responseCode == 204 // Google returns 204 for successful connectivity check
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Provides intelligent suggestions with actual internet connectivity validation.
     * @return [NetworkSuggestions] accounting for real internet access, not just network connection
     * Downgrades all suggestions when network connected but no internet available.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    suspend fun getNetworkSuggestionsWithInternetCheck(): NetworkSuggestions {
        val hasInternet = hasActualInternetConnectivity()

        return if (hasInternet) {
            getNetworkSuggestions()
        } else {
            Log.w("NetworkSuggestions", "Network connected but no internet access detected")
            getOfflineSuggestions()
        }
    }

    /**
     * Returns conservative offline suggestions when network appears connected but no internet.
     */
    private fun getOfflineSuggestions(): NetworkSuggestions {
        return NetworkSuggestions(
            canStreamHDVideo = false,
            canMakeVideoCalls = false,
            shouldDeferLargeDownloads = true,
            shouldDeferLargeUploads = true,
            suggestedImageQuality = ImageQuality.LOW,
            suggestedVideoQuality = VideoQuality.AUDIO_ONLY,
            batteryImpact = BatteryImpact.LOW, // No actual network usage
            dataCostImpact = DataCostImpact.FREE, // No data being used
            maxSuggestedFileSize = 0L, // No files should be transferred
            batchOperations = true
        )
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
     * Thread-safe network monitoring with synchronized access.
     * Multiple calls will reuse the same monitoring setup.
     */
    @Synchronized
    fun observeNetworkChanges(): Flow<NetworkState> {
        if (!isMonitoring) {
            startNetworkMonitoring()
        }

        return _networkState.asSharedFlow()
    }

    @Synchronized
    private fun startNetworkMonitoring() {
        if (isMonitoring) {
            return
        }

        // Create NetworkCallback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                _networkState.tryEmit(getCurrentNetworkState())
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                _networkState.tryEmit(getCurrentNetworkState())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, capabilities)
                _networkState.tryEmit(getCurrentNetworkState())
            }
        }

        // Create BroadcastReceiver
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e("NETWORK_MONITOR", "📻 BroadcastReceiver: ${intent?.action} (Receiver ID: ${this.hashCode()})")
                _networkState.tryEmit(getCurrentNetworkState())
            }
        }

        // Register NetworkCallback
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        } catch (e: Exception) {
            Log.e("NETWORK_MONITOR", "❌ NetworkCallback registration failed: ${e.message}")
        }

        // Register BroadcastReceiver
        try {
            val intentFilter = IntentFilter().apply {
                addAction(ConnectivityManager.CONNECTIVITY_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            }

            context.registerReceiver(broadcastReceiver, intentFilter)
        } catch (e: Exception) {
            Log.e("NETWORK_MONITOR", "❌ BroadcastReceiver registration failed: ${e.message}")
        }

        // Mark as monitoring and emit initial state
        isMonitoring = true
        _networkState.tryEmit(getCurrentNetworkState())
    }

    /**
     * Thread-safe cleanup of network monitoring.
     */
    @Synchronized
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        // Unregister NetworkCallback
        networkCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.e("NETWORK_MONITOR", "❌ NetworkCallback unregister failed: ${e.message}")
            }
        }

        // Unregister BroadcastReceiver
        broadcastReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.e("NETWORK_MONITOR", "⚠️ BroadcastReceiver already unregistered")
            } catch (e: Exception) {
                Log.e("NETWORK_MONITOR", "❌ BroadcastReceiver unregister failed: ${e.message}")
            }
        }

        // Clean up references
        networkCallback = null
        broadcastReceiver = null
        isMonitoring = false
    }

    /**
     * Debug method to check current monitoring state.
     */
    fun getMonitoringStatus(): String {
        return "Monitoring: $isMonitoring, NetworkCallback: ${networkCallback != null}, BroadcastReceiver: ${broadcastReceiver != null}"
    }

}
