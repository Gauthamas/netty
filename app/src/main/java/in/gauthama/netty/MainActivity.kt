package `in`.gauthama.netty

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
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import `in`.gauthama.netty.ui.theme.NettyTheme
import `in`.gauthama.network_monitor.NetworkStateMonitor
import `in`.gauthama.network_monitor.NetworkStateMonitorFactory
import `in`.gauthama.network_monitor.models.NetworkSuggestions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var networkStateMonitor: NetworkStateMonitor
    @RequiresPermission(READ_PHONE_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        networkStateMonitor = NetworkStateMonitorFactory.create(this)
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

        Log.e("NetworkStateMonitorImpl", "is metered: ${networkStateMonitor.isMeteredConnection()}")
        Log.e("NetworkStateMonitorImpl", "Current Network Type: ${networkStateMonitor.getCurrentNetworkType()}")
        Log.e("NetworkStateMonitorImpl", "Bandwidth Estimate: ${networkStateMonitor.getBandwidthEstimate()}")
        Log.e("NetworkStateMonitorImpl", "Wifi Info: ${networkStateMonitor.getWifiInfo()}")
        if(hasCellularPermission())
            Log.e("NetworkStateMonitorImpl", "Cellular Network Type: ${networkStateMonitor.getCellularNetworkType()}")


        Log.e("NetworkStateMonitorImpl", "Enhanced Bandwidth Estimate: ${networkStateMonitor.getEnhancedBandwidthEstimate()}")

        val rec = networkStateMonitor.getNetworkSuggestions()
        testHDVideoRecommendation(rec, networkStateMonitor)

        lifecycleScope.launch {
            networkStateMonitor.observeNetworkChanges().collect { networkState ->
                Log.e("NetworkStateMonitorImpl", "=== NETWORK CHANGED ===")
                Log.e("NetworkStateMonitorImpl", "Type: ${networkState.type}")
                Log.e("NetworkStateMonitorImpl", "Is Metered: ${networkState.isMetered}")
                Log.e("NetworkStateMonitorImpl", "Download Bandwidth: ${networkState.downloadBandwidthKbps} Kbps")
                Log.e("NetworkStateMonitorImpl", "Upload Bandwidth: ${networkState.uploadBandwidthKbps} Kbps")
                Log.e("NetworkStateMonitorImpl", "=======================")
            }
        }
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

    @RequiresPermission(READ_PHONE_STATE)
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
