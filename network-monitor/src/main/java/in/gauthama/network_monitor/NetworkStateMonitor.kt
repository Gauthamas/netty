package `in`.gauthama.network_monitor

import `in`.gauthama.network_monitor.models.NetworkState
import `in`.gauthama.network_monitor.models.NetworkType
import `in`.gauthama.network_monitor.models.CellularNetworkType
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.WiFiNetworkEstimator.WiFiInfo
import kotlinx.coroutines.flow.Flow
import android.Manifest
import androidx.annotation.RequiresPermission

/**
 * Interface for monitoring network state changes, identifying network types,
 * checking metered status, and getting intelligent bandwidth estimates.
 *
 * Provides comprehensive network intelligence including smart suggestions
 * for optimal app behavior based on current network conditions.
 *
 * Requires the `android.permission.ACCESS_NETWORK_STATE` permission in `AndroidManifest.xml`.
 * Some methods require additional permissions as documented.
 */
interface NetworkStateMonitor {

    /**
     * Retrieves the current network type.
     *
     * @return The [NetworkType] (WIFI, MOBILE, NONE, ETHERNET) of the currently active network.
     * Returns [NetworkType.NONE] if no active network or permission is not granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getCurrentNetworkType(): NetworkType

    /**
     * Retrieves the current cellular network generation (2G/3G/4G/5G) from the device's telephony service.
     * @return [CellularNetworkType] indicating cellular generation or UNKNOWN if unavailable/no permission
     * @throws SecurityException if READ_PHONE_STATE permission not granted (handled internally)
     * Delegates to internal CellularNetworkEstimator for detailed network type classification.
     * Note: Returns UNKNOWN when not connected to cellular or permission denied.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getCellularNetworkType(): CellularNetworkType

    /**
     * Retrieves comprehensive WiFi network information including standard, frequency, and signal quality.
     * @return [WiFiInfo] with detailed network data, or null if WiFi unavailable/disconnected
     * Requires ACCESS_FINE_LOCATION permission on Android 6+ to access SSID information.
     * Delegates to internal WiFiNetworkEstimator for complete WiFi analysis and capabilities assessment.
     * Returns null when WiFi disabled, not connected, or location permission missing.
     */
    fun getWifiInfo(): WiFiInfo?

    /**
     * Gets enhanced bandwidth estimate using cellular/WiFi intelligence instead of unreliable system values.
     * @return [Long] realistic bandwidth estimate in Kbps based on network type and signal quality
     * Delegates to specialized detectors for accurate network-specific bandwidth calculations.
     * Provides fallback estimates when detailed network information unavailable due to permissions.
     * Returns 0 for offline connections or when no network detection possible.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getEnhancedBandwidthEstimate(): Long

    /**
     * Provides intelligent suggestions for app behavior based on current network conditions.
     * @return [NetworkSuggestions] with actionable guidance for optimal user experience
     * Combines network type, bandwidth estimates, signal quality, and metered status for decisions.
     * Helps developers make smart choices about data usage, quality settings, and operation timing.
     * Optimizes for user experience while respecting data costs and battery life.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkSuggestions(): NetworkSuggestions

    /**
     * Retrieves the estimated download bandwidth of the current active network.
     * This is an estimate provided by the system and may not reflect actual real-time bandwidth.
     *
     * @return The estimated download bandwidth in kilobits per second (Kbps), or 0 if
     * no active network, capabilities are not available, or permission is not granted.
     * Returns Long as per user's request, but underlying API returns Int.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun getBandwidthEstimate(): Long

    /**
     * Provides intelligent suggestions with actual internet connectivity validation.
     * @return [NetworkSuggestions] accounting for real internet access, not just network connection
     * Downgrades all suggestions when network connected but no internet available.
     * Prevents false positive recommendations on captive portals or disconnected hotspots.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    suspend fun getNetworkSuggestionsWithInternetCheck(): NetworkSuggestions

    /**
     * Checks if the currently active network connection is metered.
     * Metered connections typically incur data charges (e.g., mobile data).
     *
     * @return True if the active network is metered, false otherwise (e.g., Wi-Fi),
     * or if there's no active network or permission is not granted.
     */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isMeteredConnection(): Boolean

    /**
     * Observes real-time changes in network state with enhanced monitoring capabilities.
     *
     * Uses hybrid approach combining NetworkCallback and BroadcastReceiver for maximum
     * reliability across all Android devices and versions.
     *
     * @return [Flow<NetworkState>] emitting network state changes
     * Multiple collectors will share the same underlying monitoring for efficiency.
     * Flow includes network type, metered status, and system bandwidth estimates.
     */
    fun observeNetworkChanges(): Flow<NetworkState>

    /**
     * Stops network monitoring and cleans up all registered callbacks and receivers.
     *
     * Call this method to release resources when network monitoring is no longer needed,
     * typically in Activity.onDestroy() or similar lifecycle cleanup methods.
     *
     * Thread-safe operation that can be called multiple times safely.
     */
    fun stopMonitoring()

    /**
     * Debug method to check current monitoring state and resource status.
     *
     * @return String describing current monitoring status including:
     * - Whether monitoring is active
     * - NetworkCallback registration status
     * - BroadcastReceiver registration status
     *
     * Useful for troubleshooting network monitoring issues during development.
     */
    fun getMonitoringStatus(): String

    /**
     * Validates actual internet connectivity beyond basic network connection status.
     *
     * @return [Boolean] true if internet accessible, false if only local network available
     *
     * Distinguishes between network connection (WiFi/cellular connected) and internet access.
     * Returns false for scenarios like captive portals, hotspots with no data, or ISP outages.
     * Uses lightweight connectivity test with timeout to avoid blocking the calling thread.
     * Essential for accurate network recommendations in real-world conditions.
     */
    suspend fun hasInternetConnectivity(): Boolean

}