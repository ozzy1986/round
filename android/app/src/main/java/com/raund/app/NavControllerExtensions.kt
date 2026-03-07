package com.raund.app

import androidx.navigation.NavHostController

const val PROFILES_ROUTE = "profiles"

fun NavHostController.navigateToProfiles() {
    navigate(PROFILES_ROUTE) {
        popUpTo(PROFILES_ROUTE) { inclusive = false }
        launchSingleTop = true
    }
}
