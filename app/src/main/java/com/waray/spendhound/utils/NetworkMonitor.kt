package com.waray.spendhound.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wasOffline = false

    fun start(context: Context) {
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            
            if (connectivityManager == null) {
                _isOnline.value = true // Assume online if we can't check
                return
            }

            // Initial check
            _isOnline.value = checkCurrentNetwork()
            wasOffline = !_isOnline.value

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                    wasOffline = false
                }

                override fun onLost(network: Network) {
                    _isOnline.value = false
                    wasOffline = true
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _isOnline.value = hasInternet
                    if (hasInternet) wasOffline = false else wasOffline = true
                }
            }

            try {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
            } catch (e: Exception) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback!!)
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkMonitor", "Error starting monitor: ${e.message}")
            _isOnline.value = true
        }
    }

    private fun checkCurrentNetwork(): Boolean {
        val activeNetwork = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun stop() {
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        networkCallback = null
        connectivityManager = null
    }
}
