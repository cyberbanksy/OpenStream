package com.orgista.openstream.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HeaderDeviceStatus(
    val time: String,
    val wifiConnected: Boolean,
    val online: Boolean,
    val batteryPercent: Int,
)

private fun currentTime(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

private fun batteryPercent(intent: Intent?): Int {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
}

private fun wifiConnected(manager: ConnectivityManager): Boolean {
    return manager.allNetworks.any { network ->
        manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

private fun networkOnline(manager: ConnectivityManager): Boolean {
    return manager.allNetworks.any { network ->
        val capabilities = manager.getNetworkCapabilities(network) ?: return@any false
        val hasPhysicalTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
        hasPhysicalTransport &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/** Mirrors the small, useful device-status cluster from OpenPanel's header. */
@Composable
fun rememberHeaderDeviceStatus(): HeaderDeviceStatus {
    val context = LocalContext.current
    val connectivity = remember {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    var time by remember { mutableStateOf(currentTime()) }
    var battery by remember {
        mutableIntStateOf(batteryPercent(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))))
    }
    var wifi by remember { mutableStateOf(wifiConnected(connectivity)) }
    var online by remember { mutableStateOf(networkOnline(connectivity)) }

    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(30_000)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivingContext: Context?, intent: Intent?) {
                battery = batteryPercent(intent)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    DisposableEffect(connectivity) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifi = wifiConnected(connectivity)
                online = networkOnline(connectivity)
            }

            override fun onLost(network: Network) {
                wifi = wifiConnected(connectivity)
                online = networkOnline(connectivity)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                wifi = wifiConnected(connectivity)
                online = networkOnline(connectivity)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        onDispose { connectivity.unregisterNetworkCallback(callback) }
    }

    return HeaderDeviceStatus(time, wifi, online, battery)
}
