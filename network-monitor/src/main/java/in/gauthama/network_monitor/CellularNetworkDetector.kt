package `in`.gauthama.network_monitor

import android.Manifest
import android.content.Context
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.models.CellularNetworkType

class CellularNetworkDetector(private val context: Context) {


    private val telephonyManager: TelephonyManager by lazy {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
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