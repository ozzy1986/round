package com.raund.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raund.app.sync.SyncOnConnectivityEffect
import com.raund.app.ui.ProfileListScreen
import com.raund.app.ui.ProfileEditorScreen
import com.raund.app.ui.TimerScreen
import com.raund.app.ui.theme.RaundTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TIMER_PROFILE_ID = "open_timer_profile_id"
    }

    private val pendingOpenTimerId = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.applyLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOpenTimerId.value = intent?.getStringExtra(EXTRA_OPEN_TIMER_PROFILE_ID)
        val app = application as RaundApplication
        setContent {
            RaundTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    var runSync by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { runSync = true }
                    if (runSync) SyncOnConnectivityEffect(app.profileRepository)
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val navController = rememberNavController()
                        val openTimerId = remember { pendingOpenTimerId }
                        LaunchedEffect(openTimerId.value) {
                            openTimerId.value?.let { id ->
                                navController.navigate("timer/$id") {
                                    popUpTo("profiles") { inclusive = false }
                                }
                                openTimerId.value = null
                            }
                        }
                    NavHost(navController = navController, startDestination = "profiles") {
                        composable("profiles") {
                            ProfileListScreen(
                                repository = app.profileRepository,
                                onProfileClick = { id -> navController.navigate("editor/$id") },
                                onAddProfile = { navController.navigate("editor/new") },
                                onStartTimer = { id -> navController.navigate("timer/$id") }
                            )
                        }
                        composable("editor/{profileId}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("profileId") ?: "new"
                            ProfileEditorScreen(
                                repository = app.profileRepository,
                                profileId = if (id == "new") null else id,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("timer/{profileId}") { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId") ?: return@composable
                            TimerScreen(
                                repository = app.profileRepository,
                                profileId = profileId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(EXTRA_OPEN_TIMER_PROFILE_ID)?.let { id ->
            pendingOpenTimerId.value = id
        }
    }
}
