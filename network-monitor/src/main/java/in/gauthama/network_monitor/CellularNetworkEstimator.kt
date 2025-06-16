package `in`.gauthama.network_monitor

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.models.CellularNetworkType

class CellularNetworkEstimator(private val context: Context) {


    private val telephonyManager: TelephonyManager by lazy {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }


    /**
     * Estimates realistic cellular bandwidth based on network generation and current signal strength.
     * @return [Long] estimated bandwidth in Kbps adjusted for signal quality and network congestion
     * Applies dynamic signal multiplier based on actual cellular signal strength measurements.
     * Returns conservative fallback estimate when READ_PHONE_STATE permission not available.
     * Accounts for real-world congestion factors varying by network generation.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun estimateBandwidth(): Long {
        val cellularType = getCellularNetworkType()
        return when (cellularType) {
            CellularNetworkType.UNKNOWN -> 1000L //conservative fallback without permission
            else -> estimateCellularBandwidth(cellularType)
        }
    }

    /**
     * Estimates realistic cellular bandwidth based on network generation and current signal strength.
     * @param cellularType [CellularNetworkType] indicating 2G/3G/4G/5G technology generation
     * @return [Long] estimated bandwidth in Kbps adjusted for signal quality and network congestion
     * Applies dynamic signal multiplier (4 bars = 85%, 1 bar = 20%) to base technology speeds.
     * Accounts for real-world congestion factors varying by network generation and usage patterns.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun estimateCellularBandwidth(cellularType: CellularNetworkType): Long {
        // Base speeds by technology
        val baseSpeed = when (cellularType) {
            CellularNetworkType.CELLULAR_5G -> 100_000L   // 5G theoretical
            CellularNetworkType.CELLULAR_4G -> 50_000L    // 4G theoretical
            CellularNetworkType.CELLULAR_3G -> 14_400L    // 3G theoretical
            CellularNetworkType.CELLULAR_2G -> 384L       // 2G theoretical
            else -> 10_000L
        }

        // Get actual signal strength from telephony manager
        val signalQuality = getCurrentCellularSignalQuality()

        // Apply signal-based reduction
        val signalMultiplier = when (signalQuality) {
            4 -> 0.85  // Excellent signal (4 bars)
            3 -> 0.65  // Good signal (3 bars)
            2 -> 0.40  // Fair signal (2 bars)
            1 -> 0.20  // Poor signal (1 bar)
            else -> 0.10 // Very poor/no signal
        }

        // Network congestion factor (varies by time/location)
        val congestionFactor = when (cellularType) {
            CellularNetworkType.CELLULAR_5G -> 0.70  // Less congested
            CellularNetworkType.CELLULAR_4G -> 0.50  // Moderate congestion
            CellularNetworkType.CELLULAR_3G -> 0.40  // High congestion
            else -> 0.30
        }

        return (baseSpeed * signalMultiplier * congestionFactor).toLong()
    }

    /**
     * Gets actual cellular signal strength level as standardized quality indicator (0-4 scale).
     * @return [Int] signal level where 0=no signal, 1=poor, 2=fair, 3=good, 4=excellent signal
     * Requires READ_PHONE_STATE permission to access TelephonyManager signal strength data.
     * Uses SignalStrength.level on API 29+ or backwards-compatible calculation on older versions.
     * Returns 2 (fair signal) as fallback when permission denied or SignalStrength unavailable.
     */


    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun getCurrentCellularSignalQuality(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+ (Android 10+) - Use new method
                    telephonyManager.signalStrength?.level ?: 2
                } else {
                    // API 28 and below - Use fallback method
                    getCurrentCellularSignalQualityLegacy()
                }

    }


    /**
     * Gets estimated cellular quality based on network type without requiring signal strength APIs.
     * @return [Int] estimated quality where 0=no signal, 1=poor, 2=fair, 3=good, 4=excellent
     * Uses cellular network generation as proxy for connection quality (5G=excellent, 2G=poor).
     * Backwards compatible approach that works on all Android versions without signal strength APIs.
     * Less accurate than signal-based calculation but provides reasonable quality estimates.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun getCurrentCellularSignalQualityLegacy(): Int {
        return when (getCellularNetworkType()) {
            CellularNetworkType.CELLULAR_5G -> 4      // 5G typically excellent
            CellularNetworkType.CELLULAR_4G -> 3      // 4G typically good
            CellularNetworkType.CELLULAR_3G -> 2      // 3G typically fair
            CellularNetworkType.CELLULAR_2G -> 1      // 2G typically poor
            else -> 0                                 // Unknown = assume no signal

        }
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

}