package `in`.gauthama.netty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.gauthama.network_monitor.NetworkStateMonitor
import `in`.gauthama.network_monitor.models.BatteryImpact
import `in`.gauthama.network_monitor.models.DataCostImpact
import `in`.gauthama.network_monitor.models.ImageQuality
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import `in`.gauthama.network_monitor.models.NetworkState
import `in`.gauthama.network_monitor.models.VideoQuality

@Composable
fun HeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🧪 Network Monitor Test",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun NetworkInfoCard(networkStateMonitor: NetworkStateMonitor, networkState: NetworkState?,  hasInternet: Boolean?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📊 Current Network State",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            networkState?.let { state ->
                InfoRow("Network Type", state.type.toString())
                InfoRow("Is Metered", if (state.isMetered) "Yes" else "No")
                // Internet connectivity status
                val internetStatus = when (hasInternet) {
                    true -> "✅ Internet Available"
                    false -> "❌ No Internet (Connected to network only)"
                    null -> "🔍 Checking..."
                }
                InfoRow("Internet Status", internetStatus)

                if (hasInternet == false) {
                    Text(
                        "⚠️ Network connected but no internet access detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }



                InfoRow("System Bandwidth", "${state.downloadBandwidthKbps ?: "Unknown"} Kbps")


                InfoRow("Enhanced Bandwidth", "${networkStateMonitor.getEnhancedBandwidthEstimate()} Kbps")


                // WiFi details
                networkStateMonitor.getWifiInfo()?.let { wifi ->
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("WiFi Details:", style = MaterialTheme.typography.bodyMedium)
                    InfoRow("SSID", wifi.ssid)
                    InfoRow("Signal", wifi.signalStrength.displayName)
                    InfoRow("Standard", wifi.standard.displayName)
                    InfoRow("Frequency", wifi.frequency.displayName)
                }
            }
        }
    }
}

@Composable
fun StreamingRecommendationsCard(recommendations: NetworkSuggestions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📺 Streaming Capabilities",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            RecommendationRow(
                icon = "📺",
                title = "HD Video Streaming",
                recommendation = if (recommendations.canStreamHDVideo) "✅ Recommended" else "❌ Not Recommended",
                isPositive = recommendations.canStreamHDVideo
            )

            RecommendationRow(
                icon = "📞",
                title = "Video Calls",
                recommendation = if (recommendations.canMakeVideoCalls) "✅ Should Work Well" else "❌ May Have Issues",
                isPositive = recommendations.canMakeVideoCalls
            )

            RecommendationRow(
                icon = "🎬",
                title = "Recommended Video Quality",
                recommendation = recommendations.suggestedVideoQuality.toString(),
                isPositive = recommendations.suggestedVideoQuality != VideoQuality.AUDIO_ONLY
            )
        }
    }
}

@Composable
fun QualityRecommendationsCard(recommendations: NetworkSuggestions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🎨 Quality Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            RecommendationRow(
                icon = "📸",
                title = "Image Upload Quality",
                recommendation = recommendations.suggestedImageQuality.toString(),
                isPositive = recommendations.suggestedImageQuality != ImageQuality.LOW
            )

            val compressionAdvice = when (recommendations.suggestedImageQuality) {
                ImageQuality.HIGH -> "Upload original quality (90%)"
                ImageQuality.MEDIUM -> "Compress to 60% quality"
                ImageQuality.LOW -> "Heavy compression (30% quality)"
            }

            Text(
                "💡 $compressionAdvice",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 24.dp)
            )
        }
    }
}

@Composable
fun FileOperationsCard(recommendations: NetworkSuggestions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "📁 File Operations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            RecommendationRow(
                icon = "⬇️",
                title = "Large Downloads",
                recommendation = if (recommendations.shouldDeferLargeDownloads) "⏳ Defer for later" else "✅ Proceed now",
                isPositive = !recommendations.shouldDeferLargeDownloads
            )

            RecommendationRow(
                icon = "⬆️",
                title = "Large Uploads",
                recommendation = if (recommendations.shouldDeferLargeUploads) "⏳ Wait for WiFi" else "✅ Upload now",
                isPositive = !recommendations.shouldDeferLargeUploads
            )

            val maxSizeMB = recommendations.maxSuggestedFileSize / 1_000_000
            RecommendationRow(
                icon = "📦",
                title = "Max Recommended File Size",
                recommendation = "${maxSizeMB}MB",
                isPositive = maxSizeMB >= 10
            )

            RecommendationRow(
                icon = "🔄",
                title = "Batch Operations",
                recommendation = if (recommendations.batchOperations) "✅ Group requests" else "Individual requests OK",
                isPositive = true
            )
        }
    }
}

@Composable
fun ImpactAssessmentCard(recommendations: NetworkSuggestions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "⚡ Impact Assessment",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val batteryColor = when (recommendations.batteryImpact) {
                BatteryImpact.SEVERE -> Color.Red
                BatteryImpact.HIGH -> Color(0xFFFF9800)
                BatteryImpact.MODERATE -> Color(0xFFFFEB3B)
                else -> Color.Green
            }

            RecommendationRow(
                icon = "🔋",
                title = "Battery Impact",
                recommendation = recommendations.batteryImpact.toString(),
                isPositive = recommendations.batteryImpact != BatteryImpact.SEVERE,
                customColor = batteryColor
            )

            RecommendationRow(
                icon = "💰",
                title = "Data Cost Impact",
                recommendation = recommendations.dataCostImpact.toString(),
                isPositive = recommendations.dataCostImpact == DataCostImpact.FREE
            )
        }
    }
}

@Composable
fun PracticalScenariosCard(recommendations: NetworkSuggestions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔧 Practical Scenarios",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Photo Upload Scenario
            val photoAction = if (recommendations.shouldDeferLargeUploads) {
                "📤 Queue photo for WiFi upload"
            } else {
                val quality = when (recommendations.suggestedImageQuality) {
                    ImageQuality.HIGH -> "📤 Upload at 90% quality"
                    ImageQuality.MEDIUM -> "📤 Upload compressed (60%)"
                    ImageQuality.LOW -> "📤 Upload heavily compressed (30%)"
                }
                quality
            }

            ScenarioRow("📸 Photo Upload", photoAction)

            // Video Streaming Scenario
            val videoAction = when (recommendations.suggestedVideoQuality) {
                VideoQuality.HD_1080P -> "🎬 Stream in 1080p HD"
                VideoQuality.HD_720P -> "🎬 Stream in 720p"
                VideoQuality.SD_480P -> "🎬 Stream in 480p SD"
                VideoQuality.AUDIO_ONLY -> "🎵 Audio only mode"
            }

            ScenarioRow("🎬 Video Streaming", videoAction)

            // App Sync Scenario
            val syncAction = if (recommendations.batchOperations) {
                "🔄 Batch sync operations"
            } else {
                "🔄 Sync as needed"
            }

            ScenarioRow("📱 App Sync", syncAction)
        }
    }
}


// Helper Composables
@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RecommendationRow(
    icon: String,
    title: String,
    recommendation: String,
    isPositive: Boolean,
    customColor: Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = customColor ?: if (isPositive) Color.Green else Color.Red
            )
        }
    }
}

@Composable
fun ScenarioRow(scenario: String, action: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(scenario, style = MaterialTheme.typography.bodyMedium)
        Text(
            action,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
