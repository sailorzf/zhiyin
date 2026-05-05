package com.myyinshu.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.myyinshu.data.AppSettings
import com.myyinshu.data.CommonPhrasesRepository
import com.myyinshu.data.HotWordsRepository
import com.myyinshu.data.SettingsRepository
import com.myyinshu.data.FontSize
import com.myyinshu.data.ThemeMode
import com.myyinshu.data.AppBackground
import com.myyinshu.ui.theme.*
import com.myyinshu.ui.screens.*
import com.myyinshu.voice.*
import com.myyinshu.state.CommunicationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var phrasesRepo: CommonPhrasesRepository
    private lateinit var hotWordsRepo: HotWordsRepository
    private lateinit var communicationState: CommunicationState

    // Track engine outside Compose for lifecycle management
    @Volatile
    private var currentEngine: VoiceRecognitionEngine? = null
    private val engineFlow = MutableStateFlow<VoiceRecognitionEngine?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepo = SettingsRepository(this)
        phrasesRepo = CommonPhrasesRepository(this)
        hotWordsRepo = HotWordsRepository(this)
        communicationState = CommunicationState()

        lifecycleScope.launch {
            CommonPhrasesRepository.initialize(this@MainActivity, phrasesRepo)
        }
        lifecycleScope.launch {
            HotWordsRepository.initialize(this@MainActivity, hotWordsRepo)
        }

        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = AppSettings())
            val isSystemDark = isSystemDark()

            val isDark = when (settings.themeMode) {
                ThemeMode.DAY -> false
                ThemeMode.NIGHT -> true
                ThemeMode.AUTO -> {
                    settings.background == AppBackground.BLACK ||
                    settings.background == AppBackground.DARK_BLUE ||
                    isSystemDark
                }
            }

            val colorScheme = when (settings.background) {
                AppBackground.WHITE -> if (isDark) DarkColorScheme else LightColorScheme
                AppBackground.BLACK -> DarkColorScheme
                AppBackground.YELLOW -> YellowColorScheme
                AppBackground.DARK_BLUE -> DarkBlueColorScheme
            }

            val fontSizeMultiplier = when (settings.fontSize) {
                FontSize.LARGE -> 1.0f
                FontSize.EXTRA_LARGE -> 1.25f
                FontSize.SUPER_LARGE -> 1.5f
            }

            val typography = buildTypography(fontSizeMultiplier)

            val currentVoiceEngine by engineFlow.collectAsState(initial = null)

            // Initialize or switch voice engine based on settings
            LaunchedEffect(settings.engineType, settings.hybridMode, settings.xunfeiAppId, settings.xunfeiApiKey, settings.xunfeiApiSecret) {
                currentEngine?.shutdown()
                val offlineType = VoiceEngineType.fromCode(settings.engineType)
                val hybridEngine = VoiceEngineFactory.createHybridEngine(
                    offlineType,
                    this@MainActivity,
                    settings.xunfeiAppId,
                    settings.xunfeiApiKey,
                    settings.xunfeiApiSecret,
                )
                hybridEngine.hybridMode = HybridMode.fromCode(settings.hybridMode)
                currentEngine = hybridEngine
                engineFlow.update { currentEngine }
            }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = typography,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val hasPermission = remember { mutableStateOf(checkPermission()) }

                    if (!hasPermission.value) {
                        val launcher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { granted ->
                            hasPermission.value = granted
                        }
                        LaunchedEffect(Unit) {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }

                    currentVoiceEngine?.let { engine ->
                        AppNavigation(
                            navController = navController,
                            settings = settings,
                            settingsRepo = settingsRepo,
                            phrasesRepo = phrasesRepo,
                            hotWordsRepo = hotWordsRepo,
                            voiceEngine = engine,
                            communicationState = communicationState,
                            hasPermission = hasPermission.value,
                        )
                    }
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isSystemDark(): Boolean {
        return (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    override fun onPause() {
        super.onPause()
        currentEngine?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentEngine?.shutdown()
    }
}
