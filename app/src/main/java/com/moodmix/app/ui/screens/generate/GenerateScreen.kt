package com.moodmix.app.ui.screens.generate

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moodmix.app.data.storage.ChatSession
import kotlinx.coroutines.launch

@Composable
fun GenerateScreen(
    initialPrompt: String = "",
    viewModel: GenerateViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()
    var chatToDelete by remember { mutableStateOf<ChatSession?>(null) }

    val messages = ui.current.messages

    LaunchedEffect(initialPrompt) { viewModel.initPrompt(initialPrompt) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(ui.currentId, messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        modifier = Modifier.clipToBounds(),
        drawerState = drawerState,
        // Swipe-to-open is off so it can't fight the tab pager. The menu button opens it; swipe-to-close works.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            HistoryDrawer(
                sessions = ui.sessions,
                currentId = ui.currentId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onSelect = { id ->
                    viewModel.selectChat(id)
                    scope.launch { drawerState.close() }
                },
                onDelete = { chatToDelete = it }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .glowBackground()
                .statusBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Chat history")
                }
                Text(
                    "MoodMix Create",
                    style = MaterialTheme.typography.headlineSmall.copy(brush = BrandBrush),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.newChat() }) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
                }
            }

            // Messages
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (messages.isEmpty()) {
                    EmptyChatState(onSuggestion = viewModel::onInputChanged)
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(messages, key = { it.id }) { m ->
                            if (m.isUser) {
                                UserBubble(m)
                            } else {
                                AssistantMessage(
                                    message = m,
                                    statusText = if (m.id == ui.generatingMessageId) ui.statusText else "",
                                    onExport = { viewModel.export(m) }
                                )
                            }
                        }
                    }
                }
            }

            // Composer
            ChatComposer(
                input = ui.input,
                onInputChange = viewModel::onInputChanged,
                duration = ui.duration,
                onDurationChange = viewModel::onDurationChanged,
                generatingHere = ui.isGeneratingHere,
                busyElsewhere = ui.isGenerating && !ui.isGeneratingHere,
                onSend = {
                    focusManager.clearFocus()
                    viewModel.send()
                },
                onStop = viewModel::stop,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .imePadding()
            )
        }
    }

    chatToDelete?.let { chat ->
        val tracks = chat.messages.count { it.audioFileName != null }
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete this chat?") },
            text = {
                Text(
                    if (tracks > 0)
                        "This also permanently deletes $tracks generated track${if (tracks == 1) "" else "s"} from your phone, including any copies saved to Music/MoodMix."
                    else
                        "This chat will be permanently deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(chat.id)
                    chatToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { chatToDelete = null }) { Text("Cancel") } }
        )
    }
}