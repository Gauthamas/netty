package `in`.gauthama.netty

import android.Manifest
import android.Manifest.permission.READ_PHONE_STATE
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import `in`.gauthama.netty.ui.theme.NettyTheme
import `in`.gauthama.network_monitor.NetworkStateMonitor
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var networkStateMonitor: NetworkStateMonitor
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        networkStateMonitor = NetworkStateMonitor(this)
        setContent {
            NettyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NetworkMonitorTestScreen(
                        networkStateMonitor = networkStateMonitor,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        Log.e("NetworkStateMonitor", "is metered: ${networkStateMonitor.isMeteredConnection()}")
        Log.e("NetworkStateMonitor", "Current Network Type: ${networkStateMonitor.getCurrentNetworkType()}")
        Log.e("NetworkStateMonitor", "Bandwidth Estimate: ${networkStateMonitor.getBandwidthEstimate()}")
        Log.e("NetworkStateMonitor", "Wifi Info: ${networkStateMonitor.getWifiInfo()}")
        if(hasCellularPermission())
            Log.e("NetworkStateMonitor", "Cellular Network Type: ${networkStateMonitor.getCellularNetworkType()}")


        Log.e("NetworkStateMonitor", "Enhanced Bandwidth Estimate: ${networkStateMonitor.getEnhancedBandwidthEstimate()}")

        val rec = networkStateMonitor.getNetworkSuggestions()
        testHDVideoRecommendation(rec, networkStateMonitor)

        lifecycleScope.launch {
            networkStateMonitor.observeNetworkChanges().collect { networkState ->
                Log.e("NetworkStateMonitor", "=== NETWORK CHANGED ===")
                Log.e("NetworkStateMonitor", "Type: ${networkState.type}")
                Log.e("NetworkStateMonitor", "Is Metered: ${networkState.isMetered}")
                Log.e("NetworkStateMonitor", "Download Bandwidth: ${networkState.downloadBandwidthKbps} Kbps")
                Log.e("NetworkStateMonitor", "Upload Bandwidth: ${networkState.uploadBandwidthKbps} Kbps")
                Log.e("NetworkStateMonitor", "=======================")
            }
        }
    // e("NetworkStateMonitor", "Current Network State: ${networkStateMonitor.getCurrentNetworkState()}");
    }

    fun requestCellularPermissionIfNeeded(activity: Activity): Boolean {
        return if (hasCellularPermission()) {
            true
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(READ_PHONE_STATE),
                100
            )
            false
        }
    }

    override fun onStart() {
        super.onStart()
        requestCellularPermissionIfNeeded(this);
    }

    fun hasCellularPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        networkStateMonitor.stopMonitoring()

    }

    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    private fun testHDVideoRecommendation(rec: NetworkSuggestions, networkStateMonitor: NetworkStateMonitor) {
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "📺 === HD VIDEO STREAMING TEST ===")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "Recommendation: ${if (rec.canStreamHDVideo) "✅ CAN STREAM HD" else "❌ CANNOT STREAM HD"}")

        val bandwidth = networkStateMonitor.getEnhancedBandwidthEstimate()
        val isMetered = networkStateMonitor.isMeteredConnection()

        Log.e("Networkstatemonitor TEST_HD_VIDEO", "Logic Check:")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "  - Bandwidth ≥ 5000 Kbps? ${bandwidth >= 5000L} (actual: $bandwidth)")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "  - Not metered? ${!isMetered} (metered: $isMetered)")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "  - Network quality good? (checking...)")

        val expectedResult = bandwidth >= 5_000L && !isMetered
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "Expected Result: $expectedResult")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "Actual Result: ${rec.canStreamHDVideo}")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "Test Status: ${if (expectedResult == rec.canStreamHDVideo) "✅ PASSED" else "❌ FAILED"}")
        Log.e("Networkstatemonitor TEST_HD_VIDEO", "===============================")
    }



}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NettyTheme {
        Greeting("Android")
    }
}