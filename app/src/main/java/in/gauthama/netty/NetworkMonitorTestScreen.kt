package `in`.gauthama.netty

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.gauthama.network_monitor.NetworkStateMonitor
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.NetworkState

@Composable
fun NetworkMonitorTestScreen(
    networkStateMonitor: NetworkStateMonitor,
    modifier: Modifier = Modifier
) {
    var networkState by remember { mutableStateOf<NetworkState?>(null) }
    var recommendations by remember { mutableStateOf<NetworkSuggestions?>(null) }
    var lastUpdated by remember { mutableStateOf("") }
    var hasInternet by remember { mutableStateOf<Boolean?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }

    // Observe network changes
    LaunchedEffect(networkStateMonitor) {
        networkStateMonitor.observeNetworkChanges().collect{ state ->
            networkState = state
            try {
                // Check actual internet connectivity
                hasInternet = networkStateMonitor.hasActualInternetConnectivity()

                // Get recommendations with internet validation
                recommendations = networkStateMonitor.getNetworkSuggestionsWithInternetCheck()
                permissionError = null
            } catch (e: SecurityException) {
                Log.e("UI_ERROR", "Permission error: ${e.message}")
                permissionError = "READ_PHONE_STATE permission required for cellular recommendations"
                recommendations = null
            } catch (e: Exception) {
                Log.e("UI_ERROR", "Error getting recommendations: ${e.message}")
                permissionError = "Error: ${e.message}"
                recommendations = null
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HeaderCard()
        }

        item {
            NetworkInfoCard(networkStateMonitor = networkStateMonitor, networkState = networkState, hasInternet)
        }

        recommendations?.let { rec ->
            item {
                StreamingRecommendationsCard(rec)
            }

            item {
                QualityRecommendationsCard(rec)
            }

            item {
                FileOperationsCard(rec)
            }

            item {
                ImpactAssessmentCard(rec)
            }

            item {
                PracticalScenariosCard(rec)
            }
        }
    }
}