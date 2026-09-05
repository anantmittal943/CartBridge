package com.anantmittal.cartbridge.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.anantmittal.cartbridge.presentation.home_screen.HomeScreenRoot
import com.anantmittal.cartbridge.presentation.home_screen.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {

    val navController = rememberNavController()

    MaterialTheme {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentGraphRoute = navBackStackEntry?.destination?.parent?.route

        Surface {
            NavHost(
                navController = navController,
                startDestination = Route.DefaultNavGraph
            ) {
                navigation<Route.DefaultNavGraph>(
                    startDestination = Route.Home
                ) {
                    composable<Route.Home> {
                        val viewModel = koinViewModel<HomeViewModel>()
                        HomeScreenRoot(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private inline fun <reified T : ViewModel> NavBackStackEntry.sharedKoinViewModel(
    navController: NavController
): T {
    val navGraphRoute = destination.parent?.route ?: return koinViewModel<T>()
    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }
    return koinViewModel(
        viewModelStoreOwner = parentEntry
    )
}