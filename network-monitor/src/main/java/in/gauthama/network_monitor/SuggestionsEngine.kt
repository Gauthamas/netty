package `in`.gauthama.network_monitor

import android.Manifest
import androidx.annotation.RequiresPermission
import `in`.gauthama.network_monitor.models.BatteryImpact
import `in`.gauthama.network_monitor.models.DataCostImpact
import `in`.gauthama.network_monitor.models.ImageQuality
import `in`.gauthama.network_monitor.models.NetworkQuality
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.NetworkType
import `in`.gauthama.network_monitor.models.VideoQuality

class SuggestionsEngine {
    /**
     * Provides intelligent suggestions for app behavior based on current network conditions.
     * @return [NetworkSuggestions] with actionable guidance for optimal user experience
     * Combines network type, bandwidth estimates, signal quality, and metered status for decisions.
     * Helps developers make smart choices about data usage, quality settings, and operation timing.
     * Optimizes for user experience while respecting data costs and battery life.
     */
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    fun getNetworkSuggestions(enhancedBandwidth: Long, isMetered: Boolean,
                              networkQuality: NetworkQuality, networkType: NetworkType): NetworkSuggestions {
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
            batteryImpact = assessBatteryImpact(networkType, networkQuality),
            dataCostImpact = assessDataCostImpact(isMetered, networkType),
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

    fun getOfflineSuggestions(): NetworkSuggestions {
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

}