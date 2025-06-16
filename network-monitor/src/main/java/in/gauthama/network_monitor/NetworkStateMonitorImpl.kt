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
import `in`.gauthama.network_monitor.WiFiNetworkEstimator.WiFiInfo
import `in`.gauthama.network_monitor.models.CellularNetworkType
import `in`.gauthama.network_monitor.models.NetworkQuality
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.SignalStrength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NetworkStateMonitorImpl(private val context: Context) : NetworkStateMonitor {

    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private val wifiNetworkEstimator = WiFiNetworkEstimator(context)
    private val cellularNetworkEstimator = CellularNetworkEstimator(context)
    private val suggestionsEngine = SuggestionsEngine()
    private val connectivityValidator = ConnectivityValidator()

    @Volatile
    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    private var debounceJob: Job? = null
    private val debounceDelayMs = 1000L

    private val _networkState = MutableSharedFlow<NetworkState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun getCurrentNetworkType(): NetworkType {
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


    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun getCellularNetworkType(): CellularNetworkType {
        return cellularNetworkEstimator.getCellularNetworkType()
    }

    override fun getWifiInfo(): WiFiInfo? {
        return wifiNetworkEstimator.getWiFiInfo()
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun getEnhancedBandwidthEstimate(): Long {
        return when (getCurrentNetworkType()) {
            NetworkType.WIFI -> {
                wifiNetworkEstimator.estimateBandwidth() ?: getBandwidthEstimate()
            }

            NetworkType.MOBILE -> {
                cellularNetworkEstimator.estimateBandwidth()
            }

            else -> 0L
        }
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun getNetworkSuggestions(): NetworkSuggestions {
        val networkType = getCurrentNetworkType()
        val bandwidth = getEnhancedBandwidthEstimate()
        val quality = assessNetworkQuality(networkType, bandwidth)
        val isMetered = isMeteredConnection()
        return suggestionsEngine.getNetworkSuggestions(bandwidth, isMetered, quality, networkType)

    }


    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun getBandwidthEstimate(): Long {
        if (!hasNetworkStatePermission()) {
            return 0L // Permission not granted
        }

        val activeNetwork = connectivityManager.activeNetwork ?: return 0L
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return 0L

        // LINK_DOWNSTREAM_BANDWIDTH_KBPS provides an estimate of the downstream bandwidth in Kbps
        return capabilities.linkDownstreamBandwidthKbps.toLong()
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


    override suspend fun hasInternetConnectivity(): Boolean {
        return connectivityValidator.hasActualInternetConnectivity()
    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override suspend fun getNetworkSuggestionsWithInternetCheck(): NetworkSuggestions {
        val hasInternet = connectivityValidator.hasActualInternetConnectivity()

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
        return suggestionsEngine.getOfflineSuggestions()
    }


    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override fun isMeteredConnection(): Boolean {
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
    override fun observeNetworkChanges(): Flow<NetworkState> {
        if (!isMonitoring) {
            startNetworkMonitoring()
        }

        return _networkState.asSharedFlow()
    }

    private fun checkAndEmitDebouncedNetworkState(){
        debounceJob?.cancel()
        debounceJob = CoroutineScope(Dispatchers.Default).launch {
            delay(debounceDelayMs)
            if(isActive)
                checkAndEmitNetworkState()
        }


    }

    private fun checkAndEmitNetworkState() {
        var currentState = getCurrentNetworkState()
        if(currentState!=_networkState.replayCache.firstOrNull())
            _networkState.tryEmit(currentState)
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
                checkAndEmitDebouncedNetworkState()
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                checkAndEmitDebouncedNetworkState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, capabilities)
                checkAndEmitDebouncedNetworkState()
            }
        }

        // Create BroadcastReceiver
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.e(
                    "NETWORK_MONITOR",
                    "📻 BroadcastReceiver: ${intent?.action} (Receiver ID: ${this.hashCode()})"
                )
                checkAndEmitDebouncedNetworkState()
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
    override fun stopMonitoring() {
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
        debounceJob?.cancel()
    }

    /**
     * Debug method to check current monitoring state.
     */
    override fun getMonitoringStatus(): String {
        return "Monitoring: $isMonitoring, NetworkCallback: ${networkCallback != null}, BroadcastReceiver: ${broadcastReceiver != null}"
    }

    /**
     * Checks for the `ACCESS_NETWORK_STATE` permission.
     * @return True if the permission is granted, false otherwise.
     */
    private fun hasNetworkStatePermission(): Boolean {
        return context.checkCallingOrSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
    }

}
