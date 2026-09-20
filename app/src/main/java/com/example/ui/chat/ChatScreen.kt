package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.OfflineModel
import com.example.ui.chat.components.ChatInputBar
import com.example.ui.chat.components.ChatMessageItem
import com.example.ui.chat.components.ChatSettingsSheet
import com.example.ui.chat.components.ChatTopBar
import com.example.ui.chat.components.EditMessageDialog
import com.example.ui.chat.components.ExportConversationDialog
import com.example.ui.chat.components.ModelPickerSheet
import com.example.ui.chat.components.MoveToFolderDialog
import com.example.ui.chat.components.RenameConversationDialog
import com.example.ui.chat.components.SavedPromptsSheet
import com.example.ui.components.ResoLogo
import com.example.ui.sidebar.SidebarDrawer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToServers: () -> Unit,
    onNavigateToLocalServer: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToOfflineModels: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentConversation by viewModel.currentConversation.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val activePersona by viewModel.activePersona.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val rawOfflineModels by viewModel.offlineModels.collectAsState()
    val liveProgressMap by viewModel.downloadProgress.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val isTemporary by viewModel.isTemporaryChat.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val savedPrompts by viewModel.savedPrompts.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val offlineModels = remember(rawOfflineModels, liveProgressMap) {
        rawOfflineModels.map { liveProgressMap[it.id] ?: it }
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var showPromptsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var inputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Scroll to bottom automatically on new messages or during streaming
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawer(
                conversations = conversations,
                currentConversationId = currentConversation?.id,
                activeServer = activeServer,
                isTemporary = isTemporary,
                isDarkTheme = isDarkTheme,
                onSelectConversation = { conv ->
                    viewModel.selectConversation(conv)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onToggleTemporary = { isTemp ->
                    viewModel.setTemporaryChat(isTemp)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { id ->
                    viewModel.deleteConversation(id)
                },
                onTogglePin = { id, isPinned ->
                    viewModel.togglePinConversation(id, isPinned)
                },
                onOpenServers = {
                    scope.launch { drawerState.close() }
                    onNavigateToServers()
                },
                onOpenLocalServer = {
                    scope.launch { drawerState.close() }
                    onNavigateToLocalServer()
                },
                onOpenOfflineModels = {
                    scope.launch { drawerState.close() }
                    onNavigateToOfflineModels()
                },
                onOpenPersonas = {
                    scope.launch { drawerState.close() }
                    onNavigateToPersonas()
                },
                onOpenSavedPrompts = {
                    scope.launch { drawerState.close() }
                    showPromptsSheet = true
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onNavigateToSettings()
                },
                onToggleTheme = {
                    viewModel.toggleTheme()
                }
            )
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .testTag("chat_screen"),
            topBar = {
                ChatTopBar(
                    activeServer = activeServer,
                    selectedModel = selectedModel,
                    activePersona = activePersona,
                    isTemporary = isTemporary,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onModelClick = { showModelSheet = true },
                    onPersonaClick = onNavigateToPersonas,
                    onSettingsClick = { showSettingsSheet = true },
                    onLocalServerClick = onNavigateToLocalServer,
                    onNewChatClick = { viewModel.startNewChat() },
                    onToggleIncognito = { viewModel.toggleIncognitoMode() },
                    onRenameConversation = { showRenameDialog = true },
                    onMoveToFolder = { showMoveToFolderDialog = true },
                    onExportConversation = { showExportDialog = true },
                    onShareConversation = { showExportDialog = true },
                    onClearConversation = { viewModel.clearCurrentConversation() }
                )
            },
            bottomBar = {
                ChatInputBar(
                    isStreaming = isStreaming,
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onStopStreaming = { viewModel.stopStreaming() },
                    onOpenSavedPrompts = { showPromptsSheet = true },
                    inputTextState = inputText,
                    onInputTextChanged = { inputText = it }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (messages.isEmpty()) {
                    // Empty state greeting & suggestions
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (activePersona != null) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activePersona?.emoji ?: "🤖",
                                    fontSize = 28.sp
                                )
                            }
                        } else {
                            ResoLogo(size = 58.dp, cornerRadius = 16.dp)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = activePersona?.name ?: "Reso Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = activePersona?.description ?: "Powered by local & cloud models with deep reasoning support.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        Text(
                            text = "SUGGESTED PROMPTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val suggestions = listOf(
                            "Explain quantum computing simply",
                            "Write a Kotlin function to debounce clicks",
                            "Review my code for edge cases",
                            "Draft a professional status update"
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            suggestions.forEach { prompt ->
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            viewModel.sendMessage(prompt)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = prompt,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            ChatMessageItem(
                                message = msg,
                                isStreaming = isStreaming,
                                isSpeakingThis = isTtsSpeaking,
                                onSpeak = { text -> viewModel.speakText(text) },
                                onStopSpeak = { viewModel.stopSpeaking() },
                                onRegenerate = { viewModel.regenerateLastMessage() },
                                onBranch = { viewModel.branchConversation(msg.id) },
                                onEdit = { editingMessage = msg },
                                onDelete = { viewModel.deleteMessage(msg.id) },
                                onBookmark = { viewModel.saveMessageAsPrompt(msg) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showModelSheet) {
        ModelPickerSheet(
            activeServer = activeServer,
            availableModels = availableModels,
            offlineModels = offlineModels,
            selectedModel = selectedModel,
            onModelSelected = { viewModel.selectModel(it) },
            onSelectOfflineModel = { viewModel.selectOfflineModel(it) },
            onDownloadOfflineModel = { viewModel.startDownloadOfflineModel(it) },
            onOpenOfflineHub = {
                showModelSheet = false
                onNavigateToOfflineModels()
            },
            onOpenServers = onNavigateToServers,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showPromptsSheet) {
        SavedPromptsSheet(
            prompts = savedPrompts,
            onSelectPrompt = { promptText ->
                inputText = promptText
            },
            onAddPrompt = { newPrompt ->
                scope.launch { viewModel.repository.addSavedPrompt(newPrompt) }
            },
            onDeletePrompt = { id ->
                scope.launch { viewModel.repository.deleteSavedPrompt(id) }
            },
            onDismiss = { showPromptsSheet = false }
        )
    }

    if (showSettingsSheet) {
        val conv = currentConversation
        ChatSettingsSheet(
            initialTemp = conv?.temperature ?: 0.7f,
            initialTopP = conv?.topP ?: 0.9f,
            initialMaxTokens = conv?.maxTokens ?: 2048,
            initialSystemPrompt = conv?.systemPrompt ?: activePersona?.systemPrompt,
            onSaveSettings = { temp, topP, maxTokens, prompt ->
                viewModel.updateConversationSettings(temp, topP, maxTokens, prompt)
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showRenameDialog) {
        RenameConversationDialog(
            currentTitle = currentConversation?.title ?: "New Chat",
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle ->
                viewModel.renameConversation(newTitle)
            }
        )
    }

    if (showMoveToFolderDialog) {
        MoveToFolderDialog(
            currentFolder = currentConversation?.folder,
            onDismiss = { showMoveToFolderDialog = false },
            onSelectFolder = { folder ->
                viewModel.moveToFolder(folder)
            }
        )
    }

    if (showExportDialog) {
        ExportConversationDialog(
            conversation = currentConversation,
            messages = messages,
            onDismiss = { showExportDialog = false }
        )
    }

    editingMessage?.let { msgToEdit ->
        EditMessageDialog(
            initialText = msgToEdit.content,
            onDismiss = { editingMessage = null },
            onSave = { newContent ->
                viewModel.editUserMessage(msgToEdit.id, newContent)
                editingMessage = null
            }
        )
    }
}
