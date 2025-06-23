package `in`.gauthama.network_monitor.models

/**
 * User mode for network suggestions
 */
enum class UserMode {
    /**
     * Best quality, ignore costs/battery. For users with unlimited data and power users.
     */
    UNRESTRICTED,

    /**
     * Smart defaults that balance quality with cost/battery. Default mode for most users.
     */
    BALANCED,

    /**
     * Save data/battery/money. For users with limited data plans or battery concerns.
     */
    CONSERVATIVE
}

/**
 * Configuration for the suggestions engine
 */
data class SuggestionsConfig(
    val userMode: UserMode = UserMode.BALANCED,
    val ignoreMeteredRestrictions: Boolean = false
)
