package `in`.gauthama.network_monitor

import android.content.Context

/**
 * Factory for creating NetworkStateMonitor instances.
 *
 * Provides clean instantiation while hiding implementation details.
 * Ensures proper dependency injection and lifecycle management.
 */
object NetworkStateMonitorFactory {

    /**
     * Creates a new NetworkStateMonitor instance.
     *
     * @param context Application or activity context for network operations
     * @return NetworkStateMonitor implementation ready for use
     */
    fun create(context: Context): NetworkStateMonitor {
        return NetworkStateMonitorImpl(context)
    }
}