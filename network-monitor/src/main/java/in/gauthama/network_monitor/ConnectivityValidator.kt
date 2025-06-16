package `in`.gauthama.network_monitor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL

class ConnectivityValidator {
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
}