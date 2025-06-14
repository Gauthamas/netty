package `in`.gauthama.network_monitor.models

/**
 * Comprehensive network-based suggestions for optimal app behavior.
 * Provides actionable guidance for developers based on current network conditions.
 */
data class NetworkSuggestions(
    val canStreamHDVideo: Boolean,
    val canMakeVideoCalls: Boolean,
    val shouldDeferLargeDownloads: Boolean,
    val shouldDeferLargeUploads: Boolean,
    val suggestedImageQuality: ImageQuality,
    val suggestedVideoQuality: VideoQuality,
    val batteryImpact: BatteryImpact,
    val dataCostImpact: DataCostImpact,
    val maxSuggestedFileSize: Long, // in bytes
    val batchOperations: Boolean
)

enum class ImageQuality { LOW, MEDIUM, HIGH }
enum class VideoQuality { AUDIO_ONLY, SD_480P, HD_720P, HD_1080P }
enum class BatteryImpact { MINIMAL, LOW, MODERATE, HIGH, SEVERE }
enum class DataCostImpact { FREE, LOW, MODERATE, HIGH, EXPENSIVE }

/**
 * Represents the overall quality assessment of the current network connection.
 * Combines network type, signal strength, and bandwidth into actionable quality levels.
 */
enum class NetworkQuality {
    /** No network connection available */
    OFFLINE,

    /** Very poor connection - basic text/email only */
    POOR,

    /** Fair connection - web browsing, low quality media */
    FAIR,

    /** Good connection - standard quality media, video calls */
    GOOD,

    /** Excellent connection - high quality media, HD video */
    EXCELLENT,

    /** Network state cannot be determined */
    UNKNOWN
}