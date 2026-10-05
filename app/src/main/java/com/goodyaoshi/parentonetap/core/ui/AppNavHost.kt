package com.goodyaoshi.parentonetap.core.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.goodyaoshi.parentonetap.call.ContactsScreen
import com.goodyaoshi.parentonetap.config.SettingsScreen
import com.goodyaoshi.parentonetap.core.perm.PermissionGuideScreen
import com.goodyaoshi.parentonetap.taxi.TaxiChooseScreen

private object Routes {
    const val HOME = "home"
    const val CONTACTS = "contacts"
    const val SETTINGS = "settings"
    const val TAXI = "taxi"
    const val PERMISSION_GUIDE = "permission_guide"
}

@Composable
fun ParentOneTapNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenTaxi = { navController.navigate(Routes.TAXI) }
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TAXI) {
            TaxiChooseScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onReenterPermissionGuide = { navController.navigate(Routes.PERMISSION_GUIDE) }
            )
        }
        composable(Routes.PERMISSION_GUIDE) {
            // 重新进入权限引导（设置页入口），授权完成后回设置页
            PermissionGuideScreen(onGranted = { navController.popBackStack() })
        }
    }
}
