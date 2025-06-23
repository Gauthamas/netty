package `in`.gauthama.network_monitor

import android.Manifest
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.models.BatteryImpact
import `in`.gauthama.network_monitor.models.DataCostImpact
import `in`.gauthama.network_monitor.models.ImageQuality
import `in`.gauthama.network_monitor.models.NetworkQuality
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.NetworkType
import `in`.gauthama.network_monitor.models.SuggestionsConfig
import `in`.gauthama.network_monitor.models.UserMode
import `in`.gauthama.network_monitor.models.VideoQuality

class SuggestionsEngine(
    private val config: SuggestionsConfig = SuggestionsConfig()
) {

    /**
     * Whether to treat metered connections as unrestricted
     */
    private val treatMeteredAsUnlimited: Boolean
        get() = config.ignoreMeteredRestrictions || config.userMode == UserMode.UNRESTRICTED

    /**
     * Provides intelligent suggestions for app behavior based on current network conditions.
     * @return [NetworkSuggestions] with actionable guidance for optimal user experience
     * Combines network type, bandwidth estimates, signal quality, and metered status for decisions.
     * Helps developers make smart choices about data usage, quality settings, and operation timing.
     * Optimizes for user experience while respecting data costs and battery life.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkSuggestions(
        enhancedBandwidth: Long,
        isMetered: Boolean,
        networkQuality: NetworkQuality,
        networkType: NetworkType
    ): NetworkSuggestions {
        // Adjust metered status based on configuration
        val effectiveMetered = isMetered && !treatMeteredAsUnlimited

        return NetworkSuggestions(
            canStreamHDVideo = canHandleHDVideo(enhancedBandwidth, networkQuality, effectiveMetered),
            canMakeVideoCalls = canHandleVideoCalls(enhancedBandwidth, networkQuality),
            shouldDeferLargeDownloads = shouldDeferLargeOperations(
                effectiveMetered,
                networkQuality,
                enhancedBandwidth
            ),
            shouldDeferLargeUploads = shouldDeferLargeOperations(
                effectiveMetered,
                networkQuality,
                enhancedBandwidth,
                isUpload = true
            ),
            suggestedImageQuality = getSuggestedImageQuality(
                enhancedBandwidth,
                effectiveMetered,
                networkQuality
            ),
            suggestedVideoQuality = getSuggestedVideoQuality(
                enhancedBandwidth,
                effectiveMetered,
                networkQuality
            ),
            batteryImpact = assessBatteryImpact(networkType, networkQuality),
            dataCostImpact = assessDataCostImpact(effectiveMetered, networkType),
            maxSuggestedFileSize = getMaxSuggestedFileSize(
                effectiveMetered,
                enhancedBandwidth,
                networkQuality
            ),
            batchOperations = shouldBatchOperations(effectiveMetered, networkQuality)
        )
    }

    private fun canHandleHDVideo(
        bandwidth: Long,
        quality: NetworkQuality,
        isMetered: Boolean
    ): Boolean {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                // Only care about bandwidth, ignore metered status
                bandwidth >= 5_000L && quality != NetworkQuality.POOR
            }
            UserMode.BALANCED -> {
                // Current behavior - no HD on metered unless bandwidth is great
                bandwidth >= 5_000L &&
                        quality != NetworkQuality.POOR &&
                        (!isMetered || bandwidth >= 10_000L)
            }
            UserMode.CONSERVATIVE -> {
                // HD only on unmetered with excellent conditions
                bandwidth >= 8_000L &&
                        quality == NetworkQuality.EXCELLENT &&
                        !isMetered
            }
        }
    }

    private fun canHandleVideoCalls(bandwidth: Long, quality: NetworkQuality): Boolean {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                bandwidth >= 500L // Lower threshold
            }
            UserMode.BALANCED -> {
                bandwidth >= 1_000L && quality != NetworkQuality.POOR
            }
            UserMode.CONSERVATIVE -> {
                bandwidth >= 2_000L && quality >= NetworkQuality.GOOD
            }
        }
    }

    private fun shouldDeferLargeOperations(
        isMetered: Boolean,
        quality: NetworkQuality,
        bandwidth: Long,
        isUpload: Boolean = false
    ): Boolean {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                // Only defer on very poor networks
                quality == NetworkQuality.POOR && bandwidth < 1_000L
            }
            UserMode.BALANCED -> {
                when {
                    quality == NetworkQuality.POOR -> true
                    isMetered && bandwidth < 10_000L -> true
                    isUpload && isMetered && bandwidth < 5_000L -> true
                    else -> false
                }
            }
            UserMode.CONSERVATIVE -> {
                // Defer unless on good unmetered connection
                when {
                    quality != NetworkQuality.EXCELLENT -> true
                    isMetered -> true
                    bandwidth < 20_000L -> true
                    else -> false
                }
            }
        }
    }

    private fun getSuggestedImageQuality(
        bandwidth: Long,
        isMetered: Boolean,
        quality: NetworkQuality
    ): ImageQuality {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                when {
                    quality == NetworkQuality.POOR -> ImageQuality.MEDIUM
                    bandwidth >= 5_000L -> ImageQuality.HIGH
                    else -> ImageQuality.MEDIUM
                }
            }
            UserMode.BALANCED -> {
                when {
                    quality == NetworkQuality.POOR -> ImageQuality.LOW
                    !isMetered && quality == NetworkQuality.EXCELLENT -> ImageQuality.HIGH
                    !isMetered && bandwidth >= 10_000L -> ImageQuality.HIGH
                    bandwidth >= 2_000L -> ImageQuality.MEDIUM
                    else -> ImageQuality.LOW
                }
            }
            UserMode.CONSERVATIVE -> {
                when {
                    !isMetered && quality == NetworkQuality.EXCELLENT && bandwidth >= 20_000L -> ImageQuality.MEDIUM
                    else -> ImageQuality.LOW
                }
            }
        }
    }

    private fun getSuggestedVideoQuality(
        bandwidth: Long,
        isMetered: Boolean,
        quality: NetworkQuality
    ): VideoQuality {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                when {
                    bandwidth >= 8_000L -> VideoQuality.HD_1080P
                    bandwidth >= 5_000L -> VideoQuality.HD_720P
                    bandwidth >= 2_000L -> VideoQuality.SD_480P
                    else -> VideoQuality.AUDIO_ONLY
                }
            }
            UserMode.BALANCED -> {
                when {
                    quality == NetworkQuality.POOR || bandwidth < 500L -> VideoQuality.AUDIO_ONLY
                    isMetered && bandwidth < 3_000L -> VideoQuality.SD_480P
                    !isMetered && bandwidth >= 8_000L && quality == NetworkQuality.EXCELLENT -> VideoQuality.HD_1080P
                    !isMetered && bandwidth >= 5_000L -> VideoQuality.HD_720P
                    else -> VideoQuality.SD_480P
                }
            }
            UserMode.CONSERVATIVE -> {
                when {
                    bandwidth < 1_000L -> VideoQuality.AUDIO_ONLY
                    !isMetered && bandwidth >= 10_000L -> VideoQuality.SD_480P
                    else -> VideoQuality.AUDIO_ONLY
                }
            }
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
            config.ignoreMeteredRestrictions -> DataCostImpact.FREE // Treat as unlimited
            else -> DataCostImpact.MODERATE
        }
    }

    private fun getMaxSuggestedFileSize(
        isMetered: Boolean,
        bandwidth: Long,
        quality: NetworkQuality
    ): Long {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> {
                when {
                    bandwidth >= 20_000L -> 500_000_000L // 500 MB
                    bandwidth >= 10_000L -> 200_000_000L // 200 MB
                    bandwidth >= 5_000L -> 100_000_000L  // 100 MB
                    else -> 50_000_000L                  // 50 MB
                }
            }
            UserMode.BALANCED -> {
                when {
                    quality == NetworkQuality.POOR -> 1_000_000L // 1 MB
                    isMetered -> when {
                        bandwidth >= 10_000L -> 10_000_000L // 10 MB
                        bandwidth >= 5_000L -> 5_000_000L   // 5 MB
                        else -> 2_000_000L                  // 2 MB
                    }
                    else -> when {
                        bandwidth >= 20_000L -> 100_000_000L // 100 MB
                        bandwidth >= 10_000L -> 50_000_000L  // 50 MB
                        else -> 20_000_000L                  // 20 MB
                    }
                }
            }
            UserMode.CONSERVATIVE -> {
                when {
                    isMetered -> 500_000L // 500 KB
                    bandwidth >= 20_000L && quality == NetworkQuality.EXCELLENT -> 10_000_000L // 10 MB
                    else -> 2_000_000L // 2 MB
                }
            }
        }
    }

    private fun shouldBatchOperations(isMetered: Boolean, quality: NetworkQuality): Boolean {
        return when (config.userMode) {
            UserMode.UNRESTRICTED -> false // Don't batch, send immediately
            UserMode.BALANCED -> isMetered || quality == NetworkQuality.POOR
            UserMode.CONSERVATIVE -> true // Always batch to minimize network usage
        }
    }

    fun getOfflineSuggestions(): NetworkSuggestions {
        return NetworkSuggestions(
            canStreamHDVideo = false,
            canMakeVideoCalls = false,
            shouldDeferLargeDownloads = true,
            shouldDeferLargeUploads = true,
            suggestedImageQuality = ImageQuality.LOW,
            suggestedVideoQuality = VideoQuality.AUDIO_ONLY,
            batteryImpact = BatteryImpact.LOW,
            dataCostImpact = DataCostImpact.FREE,
            maxSuggestedFileSize = 0L,
            batchOperations = true
        )
    }
}