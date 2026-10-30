package com.smartgnss.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Phone internet reachability, for the NTRIP screen. */
enum class NetStatus(val label: String) {
    OFFLINE("No internet"),
    WIFI("Wi-Fi"),
    CELLULAR("Mobile data"),
    OTHER("Online"),
}

/** Live internet status that recomposes as the device goes on/offline. */
@Composable
fun rememberNetStatus(): NetStatus {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(currentNet(ctx)) }

    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { status = currentNet(ctx) }
            override fun onLost(network: Network) { status = currentNet(ctx) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                status = currentNet(ctx)
            }
        }
        try { cm?.registerDefaultNetworkCallback(cb) } catch (_: Exception) {}
        onDispose { try { cm?.unregisterNetworkCallback(cb) } catch (_: Exception) {} }
    }
    return status
}

private fun currentNet(ctx: Context): NetStatus {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetStatus.OFFLINE
    val net = cm.activeNetwork ?: return NetStatus.OFFLINE
    val caps = cm.getNetworkCapabilities(net) ?: return NetStatus.OFFLINE
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetStatus.OFFLINE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetStatus.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetStatus.CELLULAR
        else -> NetStatus.OTHER
    }
}
