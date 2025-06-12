package `in`.gauthama.netty

import android.Manifest
import android.Manifest.permission.READ_PHONE_STATE
import android.app.Activity
import android.content.Context
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
import `in`.gauthama.netty.ui.theme.NettyTheme
import `in`.gauthama.network_monitor.NetworkStateMonitor

class MainActivity : ComponentActivity() {
    @RequiresPermission(Manifest.permission.READ_PHONE_STATE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NettyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val networkStateMonitor = NetworkStateMonitor(this)
        Log.e("NetworkStateMonitor", "is metered: ${networkStateMonitor.isMeteredConnection()}")
        Log.e("NetworkStateMonitor", "Current Network Type: ${networkStateMonitor.getCurrentNetworkType()}")
        Log.e("NetworkStateMonitor", "Bandwidth Estimate: ${networkStateMonitor.getBandwidthEstimate()}")
        if(hasCellularPermission())
            Log.e("NetworkStateMonitor", "Cellular Network Type: ${networkStateMonitor.getCellularNetworkType()}")
        //Log.
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