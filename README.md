## Purpose
1. Provide a single class for all your network monitoring needs
2. Combines apis from connectivity manager, telephony manager and wifi manager
3. Checks for internet connectivity
4. Adds a network intelligence layer to provide actionable suggestions for various operations such as streaming video, uploading photos etc.


## Code Sample Usage
```kotlin

// In your Activity/Fragment
val networkStateMonitor = NetworkStateMonitorFactory.create(this)

lifecycleScope.launch {
    networkStateMonitor.observeNetworkChanges().collect { networkState ->
        Log.e("NetworkStateMonitor", "=== NETWORK CHANGED ===")
        Log.e("NetworkStateMonitor", "Type: ${networkState.type}")
        Log.e("NetworkStateMonitor", "Is Metered: ${networkState.isMetered}")
        Log.e("NetworkStateMonitor", "Download Bandwidth: ${networkState.downloadBandwidthKbps} Kbps")
        Log.e("NetworkStateMonitor", "Upload Bandwidth: ${networkState.uploadBandwidthKbps} Kbps")
        
        val isConnected = networkStateMonitor.hasInternetConnectivity()
        Log.e("NetworkStateMonitor", "Is connected to the internet: $isConnected")
        
        val networkSuggestions = networkStateMonitor.getNetworkSuggestionsWithInternetCheck()
        Log.e("NetworkStateMonitor", "Network Suggestions: $networkSuggestions")
        
        // Access individual suggestion fields:
        Log.e("NetworkStateMonitor", "  Can Stream HD Video: ${networkSuggestions.canStreamHDVideo}")
        Log.e("NetworkStateMonitor", "  Can Make Video Calls: ${networkSuggestions.canMakeVideoCalls}")
        Log.e("NetworkStateMonitor", "  Should Defer Large Uploads: ${networkSuggestions.shouldDeferLargeUploads}")
        Log.e("NetworkStateMonitor", "  Should Defer Large Downloads: ${networkSuggestions.shouldDeferLargeDownloads}")
        Log.e("NetworkStateMonitor", "  Recommended Image Quality: ${networkSuggestions.suggestedImageQuality}")
        Log.e("NetworkStateMonitor", "  Recommended Video Quality: ${networkSuggestions.suggestedVideoQuality}")
        Log.e("NetworkStateMonitor", "  Battery Impact: ${networkSuggestions.batteryImpact}")
        Log.e("NetworkStateMonitor", "  Data Cost Impact: ${networkSuggestions.dataCostImpact}")
    }
}
```
## Demo App

<div align="center">
  <img src="https://github.com/Gauthamas/netty/blob/master/images/Screenshot1.jpeg" width="300">
  <p><em>Network monitoring demo app</em></p>
</div>





  
