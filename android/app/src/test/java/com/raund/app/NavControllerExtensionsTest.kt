package com.raund.app

import android.content.Context
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class NavControllerExtensionsTest {

    @Test
    fun `navigateToProfiles keeps profiles visible after repeated timer back actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val navController = NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = PROFILES_ROUTE) {
                composable(PROFILES_ROUTE) {}
                composable("editor/{profileId}") {}
                composable("timer/{profileId}") {}
            }
        }

        navController.navigate("timer/test-profile")

        navController.navigateToProfiles()
        navController.navigateToProfiles()

        assertEquals(PROFILES_ROUTE, navController.currentDestination?.route)
    }
}
