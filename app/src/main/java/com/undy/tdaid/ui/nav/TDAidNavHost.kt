package com.undy.tdaid.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.undy.tdaid.ui.screens.alert.TeeTimeAlertScreen
import com.undy.tdaid.ui.screens.bioeditor.BioEditorScreen
import com.undy.tdaid.ui.screens.dashboard.RoundDashboardScreen
import com.undy.tdaid.ui.screens.datasources.DataSourcesScreen
import com.undy.tdaid.ui.screens.fieldnow.NowAnnouncingScreen
import com.undy.tdaid.ui.screens.roster.RosterScreen
import com.undy.tdaid.ui.screens.schedule.FullScheduleScreen
import com.undy.tdaid.ui.screens.tournament.TournamentSearchScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ROSTER = "roster/{division}"
    const val BIO_EDITOR = "bioEditor/{playerId}"
    const val DATA_SOURCES = "dataSources"
    const val FIELD_NOW = "fieldNow"
    const val TEE_ALERT = "teeAlert"
    const val SCHEDULE = "schedule"
    const val TOURNAMENT_SEARCH = "tournamentSearch"

    fun roster(division: String) = "roster/$division"
    fun bioEditor(playerId: String) = "bioEditor/$playerId"
}

@Composable
fun TDAidNavHost() {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            RoundDashboardScreen(
                onEnterFieldMode = { navController.navigate(Routes.FIELD_NOW) },
                onOpenDataSources = { navController.navigate(Routes.DATA_SOURCES) },
                onOpenRoster = { division -> navController.navigate(Routes.roster(division)) },
                onSelectTournament = { navController.navigate(Routes.TOURNAMENT_SEARCH) },
            )
        }
        composable(Routes.ROSTER) { backStackEntry ->
            val division = backStackEntry.arguments?.getString("division") ?: "MPO"
            RosterScreen(
                divisionCode = division,
                onBack = { navController.popBackStack() },
                onEditBio = { playerId -> navController.navigate(Routes.bioEditor(playerId)) },
            )
        }
        composable(Routes.BIO_EDITOR) { backStackEntry ->
            val playerId = backStackEntry.arguments?.getString("playerId") ?: ""
            BioEditorScreen(playerId = playerId, onBack = { navController.popBackStack() })
        }
        composable(Routes.DATA_SOURCES) {
            DataSourcesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.FIELD_NOW) {
            NowAnnouncingScreen(
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE) },
                onOpenAlert = { navController.navigate(Routes.TEE_ALERT) },
            )
        }
        composable(Routes.TEE_ALERT) {
            TeeTimeAlertScreen()
        }
        composable(Routes.SCHEDULE) {
            FullScheduleScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOURNAMENT_SEARCH) {
            TournamentSearchScreen(
                onBack = { navController.popBackStack() },
                onGoToDataSources = { navController.navigate(Routes.DATA_SOURCES) },
            )
        }
    }
}
