package com.myyinshu.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.myyinshu.data.HotWord
import com.myyinshu.data.HotWordsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotWordsScreen(
    hotWordsRepo: HotWordsRepository,
    onNavigateBack: () -> Unit,
) {
    val words by hotWordsRepo.hotWords.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<HotWord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("热词管理", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加",
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
            Text(
                text = "热词用于提升语音识别准确率，不会影响快捷输入",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            if (words.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "还没有热词，点击右上角 + 添加",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(words, key = { _, word -> word.id }) { index, word ->
                        HotWordItem(
                            word = word,
                            index = index,
                            totalCount = words.size,
                            onEdit = { editingWord = word },
                            onDelete = { scope.launch { hotWordsRepo.deleteWord(word.id) } },
                            onMoveUp = {
                                if (index > 0) scope.launch { hotWordsRepo.reorderWord(index, index - 1) }
                            },
                            onMoveDown = {
                                if (index < words.size - 1) scope.launch { hotWordsRepo.reorderWord(index, index + 1) }
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(48.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        HotWordInputDialog(
            title = "添加热词",
            onDismiss = { showAddDialog = false },
            onConfirm = { text ->
                scope.launch { hotWordsRepo.addWord(text) }
                showAddDialog = false
            },
        )
    }

    editingWord?.let { word ->
        HotWordInputDialog(
            title = "编辑热词",
            initialValue = word.word,
            onDismiss = { editingWord = null },
            onConfirm = { text ->
                scope.launch { hotWordsRepo.updateWord(word.id, text) }
                editingWord = null
            },
        )
    }
}

@Composable
private fun HotWordItem(
    word: HotWord,
    index: Int,
    totalCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.width(24.dp),
            )

            Text(
                text = word.word,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("↑", fontSize = 16.sp)
                }
                TextButton(
                    onClick = onMoveDown,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text("↓", fontSize = 16.sp)
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = errorColor)
            }
        }
    }
}

@Composable
private fun HotWordInputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入热词内容") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 20.sp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", fontSize = 18.sp)
                    }
                    Button(
                        onClick = { if (input.isNotBlank()) onConfirm(input.trim()) },
                        enabled = input.isNotBlank(),
                    ) {
                        Text("确定", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
