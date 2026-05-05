package com.myyinshu.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.myyinshu.data.AppSettings
import com.myyinshu.data.CommonPhrasesRepository
import com.myyinshu.data.HotWordsRepository
import com.myyinshu.data.SettingsRepository
import com.myyinshu.state.CommunicationState
import com.myyinshu.voice.VoiceRecognitionEngine

sealed class Screen(val route: String) {
    object Communication : Screen("communication")
    object Settings : Screen("settings")
    object CommonPhrases : Screen("common_phrases")
    object HotWords : Screen("hot_words")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    settings: AppSettings,
    settingsRepo: SettingsRepository,
    phrasesRepo: CommonPhrasesRepository,
    hotWordsRepo: HotWordsRepository,
    voiceEngine: VoiceRecognitionEngine,
    communicationState: CommunicationState,
    hasPermission: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Communication.route,
    ) {
        composable(Screen.Communication.route) {
            CommunicationScreen(
                settings = settings,
                phrasesRepo = phrasesRepo,
                hotWordsRepo = hotWordsRepo,
                voiceEngine = voiceEngine,
                communicationState = communicationState,
                hasPermission = hasPermission,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                settings = settings,
                settingsRepo = settingsRepo,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPhrases = { navController.navigate(Screen.CommonPhrases.route) },
                onNavigateToHotWords = { navController.navigate(Screen.HotWords.route) },
            )
        }
        composable(Screen.CommonPhrases.route) {
            CommonPhrasesScreen(
                phrasesRepo = phrasesRepo,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(Screen.HotWords.route) {
            HotWordsScreen(
                hotWordsRepo = hotWordsRepo,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
