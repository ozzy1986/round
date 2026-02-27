package com.raund.app.sync

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.raund.app.data.local.SyncPrefs
import com.raund.app.data.repository.ProfileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Registers a network callback. When connectivity is restored (onAvailable), schedules
 * a sync after [SyncPrefs.CONNECTIVITY_SYNC_DELAY_MS]. If connectivity is restored again
 * before that delay, the timer resets. So sync runs only after 1 minute of stable connection.
 */
@Composable
fun SyncOnConnectivityEffect(repository: ProfileRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectivityManager = remember {
        context.getSystemService(ConnectivityManager::class.java)
    }
    val pendingSyncJob = remember { mutableStateOf<Job?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(repository, connectivityManager) {
        if (connectivityManager == null) return@DisposableEffect onDispose { }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val cm = connectivityManager ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val caps = cm.getNetworkCapabilities(network) ?: return
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return
                }
                mainHandler.post {
                    pendingSyncJob.value?.cancel()
                    pendingSyncJob.value = scope.launch {
                        delay(SyncPrefs.CONNECTIVITY_SYNC_DELAY_MS)
                        if (isActive) repository.requestSync()
                        pendingSyncJob.value = null
                    }
                }
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        onDispose {
            pendingSyncJob.value?.cancel()
            pendingSyncJob.value = null
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
