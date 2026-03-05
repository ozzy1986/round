package com.raund.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.raund.app.ui.AppLoadingScreen
import com.raund.app.ui.ConsentDialog
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raund.app.sync.SyncOnConnectivityEffect
import com.raund.app.ui.ProfileListScreen
import com.raund.app.ui.ProfileEditorScreen
import com.raund.app.ui.TimerScreen
import com.raund.app.ui.theme.RaundTheme
import com.raund.app.viewmodel.ProfileListViewModel
import com.raund.app.viewmodel.ProfileEditorViewModel
import com.raund.app.viewmodel.TimerViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TIMER_PROFILE_ID = "open_timer_profile_id"
    }

    private val pendingOpenTimerId = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    private fun setupComposeContent(app: RaundApplication) {
        setContent {
            RaundTheme {
                val appReady by app.repositoryReadyFlow.collectAsState(initial = app.repositoryReady)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!appReady) {
                        AppLoadingScreen()
                    } else {
                        var showConsentDialog by remember {
                            mutableStateOf(!app.dataConsentPrefs.isConsentGranted())
                        }
                        if (showConsentDialog) {
                            ConsentDialog(
                                onAccept = {
                                    app.dataConsentPrefs.setConsentGranted(true)
                                    showConsentDialog = false
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try { app.profileRepository.requestSync() } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                        SyncOnConnectivityEffect(app.profileRepository)
                        val navController = rememberNavController()
                        val openTimerId = remember { pendingOpenTimerId }
                        LaunchedEffect(openTimerId.value) {
                            openTimerId.value?.let { id ->
                                val currentEntry = navController.currentBackStackEntry
                                val alreadyOnTimer = currentEntry?.destination?.route == "timer/{profileId}" &&
                                        currentEntry.arguments?.getString("profileId") == id
                                if (!alreadyOnTimer) {
                                    navController.navigate("timer/$id") {
                                        popUpTo("profiles") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                                openTimerId.value = null
                            }
                        }
                        NavHost(navController = navController, startDestination = "profiles") {
                            composable("profiles") {
                                val listViewModel: ProfileListViewModel = viewModel()
                                ProfileListScreen(
                                    viewModel = listViewModel,
                                    onProfileClick = { id -> navController.navigate("editor/$id") },
                                    onAddProfile = { navController.navigate("editor/new") },
                                    onStartTimer = { id -> navController.navigate("timer/$id") }
                                )
                            }
                            composable("editor/{profileId}") { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("profileId") ?: "new"
                                val appContext = LocalContext.current.applicationContext
                                val editorViewModel: ProfileEditorViewModel = viewModel(
                                    factory = object : ViewModelProvider.Factory {
                                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                            @Suppress("UNCHECKED_CAST")
                                            return ProfileEditorViewModel(
                                                if (id == "new") null else id,
                                                appContext as Application
                                            ) as T
                                        }
                                    }
                                )
                                ProfileEditorScreen(
                                    viewModel = editorViewModel,
                                    profileId = if (id == "new") null else id,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("timer/{profileId}") { backStackEntry ->
                                val profileId = backStackEntry.arguments?.getString("profileId") ?: return@composable
                                val appContext = LocalContext.current.applicationContext
                                val timerViewModel: TimerViewModel = viewModel(
                                    factory = object : ViewModelProvider.Factory {
                                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                            @Suppress("UNCHECKED_CAST")
                                            return TimerViewModel(profileId, appContext as Application) as T
                                        }
                                    }
                                )
                                TimerScreen(
                                    viewModel = timerViewModel,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as RaundApplication
        installSplashScreen().setKeepOnScreenCondition { false }
        super.onCreate(savedInstanceState)
        pendingOpenTimerId.value = intent?.getStringExtra(EXTRA_OPEN_TIMER_PROFILE_ID)
        val placeholder = FrameLayout(this)
        placeholder.setBackgroundColor(android.graphics.Color.parseColor("#1A0F1E"))
        setContentView(placeholder)
        placeholder.post {
            setupComposeContent(app)
            reportFullyDrawn()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("RaundLifecycle", "MainActivity.onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.i("RaundLifecycle", "MainActivity.onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("RaundLifecycle", "MainActivity.onDestroy")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_TIMER_PROFILE_ID)?.let { id ->
            pendingOpenTimerId.value = id
        }
    }
}
