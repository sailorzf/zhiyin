package com.myyinshu.voice

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Monitors network connectivity to determine if online services are available.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    var isOnline: Boolean = checkCurrentNetwork()
        private set

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            isOnline = true
            onNetworkChanged?.invoke(true)
        }

        override fun onLost(network: Network) {
            isOnline = checkCurrentNetwork()
            onNetworkChanged?.invoke(isOnline)
        }
    }

    var onNetworkChanged: ((Boolean) -> Unit)? = null

    init {
        registerCallback()
    }

    fun checkCurrentNetwork(): Boolean {
        val network = connectivityManager?.activeNetwork ?: return false
        val caps = connectivityManager?.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectivityManager?.registerDefaultNetworkCallback(
                callback,
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
        } else {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}
