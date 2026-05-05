package com.myyinshu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myyinshu.data.AppSettings
import com.myyinshu.data.CommonPhrasesRepository
import com.myyinshu.data.HotWordsRepository
import com.myyinshu.state.CommunicationState
import com.myyinshu.voice.VoiceRecognitionEngine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationScreen(
    settings: AppSettings,
    phrasesRepo: CommonPhrasesRepository,
    hotWordsRepo: HotWordsRepository,
    voiceEngine: VoiceRecognitionEngine,
    communicationState: CommunicationState,
    hasPermission: Boolean,
    onNavigateToSettings: () -> Unit,
) {
    val phrases by phrasesRepo.phrases.collectAsState(initial = emptyList())
    val hotWords by hotWordsRepo.hotWords.collectAsState(initial = emptyList())
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val listeningColor = Color(0xFF4CAF50)
    val idleColor = Color(0xFF9E9E9E)
    val stopButtonColor = Color(0xFFD32F2F)

    // Attach/detach engine callbacks based on Composable lifecycle
    DisposableEffect(voiceEngine) {
        communicationState.attach(voiceEngine)
        onDispose { communicationState.detach() }
    }

    // Sync hot words to voice engine to improve recognition accuracy
    LaunchedEffect(hotWords.map { it.word }) {
        try {
            communicationState.setHotWords(hotWords.map { it.word })
        } catch (_: Exception) {
            // Ignore - hot words are best-effort
        }
    }

    val recognizedText = communicationState.textSegments.joinToString("\n")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知音", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            // Text display area with auto-scroll
            val listState = rememberLazyListState()

            LaunchedEffect(communicationState.textSegments.size, communicationState.partialText) {
                if (listState.canScrollForward) {
                    listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (recognizedText.isEmpty() && communicationState.partialText == null) {
                    Text(
                        text = "对方说的话会显示在这里",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(communicationState.textSegments) { text ->
                            Text(
                                text = text,
                                fontSize = settings.fontSize.value.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = settings.fontSize.value.sp.times(1.5f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            communicationState.partialText?.let { partial ->
                                Text(
                                    text = partial,
                                    fontSize = settings.fontSize.value.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = settings.fontSize.value.sp.times(1.5f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }

                // Clear button when text is present
                if (recognizedText.isNotEmpty() || communicationState.partialText != null) {
                    TextButton(
                        onClick = {
                            communicationState.clear()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "清空",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!communicationState.modelReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (communicationState.isListening) listeningColor else idleColor,
                                shape = androidx.compose.foundation.shape.CircleShape,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (!communicationState.modelReady) communicationState.modelProgress else (if (communicationState.isListening) "正在听..." else "准备就绪"),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error message (auto-dismiss after 5s)
            communicationState.errorMessage?.let { err ->
                Text(
                    text = err,
                    color = stopButtonColor,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                LaunchedEffect(err) {
                    kotlinx.coroutines.delay(5000)
                    communicationState.errorMessage = null
                }
            }

            // Main listen button
            Button(
                onClick = {
                    if (!communicationState.modelReady) return@Button
                    if (!hasPermission) {
                        communicationState.errorMessage = "请在设置中授予录音权限"
                        return@Button
                    }
                    if (communicationState.isListening) {
                        voiceEngine.stopListening()
                        communicationState.partialText = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else {
                        communicationState.partialText = null
                        communicationState.errorMessage = null
                        voiceEngine.startListening()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isWideScreen) 100.dp else 80.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (communicationState.isListening) stopButtonColor else MaterialTheme.colorScheme.primary,
                ),
                enabled = communicationState.modelReady,
            ) {
                Text(
                    text = if (!communicationState.modelReady) "加载中..." else (if (communicationState.isListening) "停止" else "开始聆听"),
                    fontSize = if (isWideScreen) 28.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick phrases button
            if (phrases.isNotEmpty()) {
                var showPhrasesSheet by remember { mutableStateOf(false) }

                Button(
                    onClick = { showPhrasesSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("常用语", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }

                // Bottom sheet for common phrases
                if (showPhrasesSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showPhrasesSheet = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        // Sheet header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "常用语",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { showPhrasesSheet = false }) {
                                Text("关闭", fontSize = 18.sp)
                            }
                        }
                        HorizontalDivider()

                        // Phrase grid (2 columns, large buttons)
                        val displayPhrases = phrases.take(20)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.6f).dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(displayPhrases) { phrase ->
                                OutlinedButton(
                                    onClick = {
                                        communicationState.addText(phrase.text)
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch {
                                            phrasesRepo.movePhraseToFront(phrase.id)
                                        }
                                        showPhrasesSheet = false
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                ) {
                                    Text(
                                        phrase.text,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
