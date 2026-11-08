package org.syalosovetskyi.onemoney.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.syalosovetskyi.onemoney.ui.main.MainScreen
import org.syalosovetskyi.onemoney.ui.transactions.AddTransactionScreen

sealed class Screen(val route: String) {
    object Main  : Screen("main")
    object AddTx : Screen("add_transaction")
}

@Composable
fun onemoneyNavGraph(navController: NavHostController = rememberNavController(), requestedScreen: String? = null, screenRequestId: Long = 0L) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {

        composable(Screen.Main.route) {
            MainScreen(
                requestedScreen = requestedScreen,
                screenRequestId = screenRequestId,
                onAddTransaction = { navController.navigate(Screen.AddTx.route) }
            )
        }

        composable(Screen.AddTx.route) {
            AddTransactionScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
