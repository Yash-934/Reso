package com.example.ui.chat

import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.OfflineModel
import com.example.ui.chat.components.AttachmentActionsSheet
import com.example.ui.chat.components.ChatInputBar
import com.example.ui.chat.components.ChatMessageItem
import com.example.ui.chat.components.ChatSettingsSheet
import com.example.ui.chat.components.ChatTopBar
import com.example.ui.chat.components.EditMessageDialog
import com.example.ui.chat.components.ExportConversationDialog
import com.example.ui.chat.components.ModelPickerSheet
import com.example.ui.chat.components.MoveToFolderDialog
import com.example.ui.chat.components.PersonaPickerSheet
import com.example.ui.chat.components.RenameConversationDialog
import com.example.ui.chat.components.SavedPromptsSheet
import com.example.ui.chat.components.VoiceModeDialog
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
    val context = LocalContext.current

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
    val personas by viewModel.personas.collectAsState()
    val savedPrompts by viewModel.savedPrompts.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()

    val isWebSearchEnabled by viewModel.isWebSearchEnabled.collectAsState()
    val isMathToolEnabled by viewModel.isMathToolEnabled.collectAsState()
    val attachedImageUri by viewModel.attachedImageUri.collectAsState()
    val attachedDocument by viewModel.attachedDocument.collectAsState()

    val offlineModels = remember(rawOfflineModels, liveProgressMap) {
        rawOfflineModels.map { liveProgressMap[it.id] ?: it }
    }

    var showModelSheet by remember { mutableStateOf(false) }
    var showPersonaSheet by remember { mutableStateOf(false) }
    var showPromptsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showAttachmentActionsSheet by remember { mutableStateOf(false) }
    var showVoiceModeDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var inputText by remember { mutableStateOf("") }

    // In-chat search state
    var isSearchActive by remember { mutableStateOf(false) }
    var chatSearchQuery by remember { mutableStateOf("") }
    var searchMatchIndices by remember { mutableStateOf(listOf<Int>()) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    // Calculate search match indices
    LaunchedEffect(chatSearchQuery, messages) {
        if (chatSearchQuery.isBlank()) {
            searchMatchIndices = emptyList()
            currentMatchIndex = 0
        } else {
            val q = chatSearchQuery.trim().lowercase()
            val indices = messages.mapIndexedNotNull { index, msg ->
                if (msg.content.lowercase().contains(q) || msg.reasoningContent?.lowercase()?.contains(q) == true) {
                    index
                } else null
            }
            searchMatchIndices = indices
            currentMatchIndex = 0
            if (indices.isNotEmpty()) {
                listState.animateScrollToItem(indices.first())
            }
        }
    }

    // Scroll to bottom automatically on new messages or during streaming
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty() && !isSearchActive) {
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
            modifier = modifier.fillMaxSize().testTag("chat_screen"),
            topBar = {
                ChatTopBar(
                    activeServer = activeServer,
                    selectedModel = selectedModel,
                    activePersona = activePersona,
                    isTemporary = isTemporary,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onModelClick = { showModelSheet = true },
                    onPersonaClick = { showPersonaSheet = true },
                    onSettingsClick = { showSettingsSheet = true },
                    onLocalServerClick = onNavigateToLocalServer,
                    onNewChatClick = { viewModel.startNewChat() },
                    onToggleIncognito = { viewModel.toggleIncognitoMode() },
                    onRenameConversation = { showRenameDialog = true },
                    onMoveToFolder = { showMoveToFolderDialog = true },
                    onExportConversation = { showExportDialog = true },
                    onShareConversation = {
                        val shareText = viewModel.getShareableConversationText()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, currentConversation?.title ?: "Chat Conversation")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Conversation"))
                    },
                    onOpenSavedPrompts = { showPromptsSheet = true },
                    onClearConversation = { showClearConfirmation = true },
                    isSearchActive = isSearchActive,
                    searchQuery = chatSearchQuery,
                    onSearchQueryChanged = { chatSearchQuery = it },
                    onToggleSearch = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) chatSearchQuery = ""
                    },
                    searchMatchCount = searchMatchIndices.size,
                    currentMatchIndex = currentMatchIndex,
                    onNextMatch = {
                        if (searchMatchIndices.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % searchMatchIndices.size
                            scope.launch {
                                listState.animateScrollToItem(searchMatchIndices[currentMatchIndex])
                            }
                        }
                    },
                    onPrevMatch = {
                        if (searchMatchIndices.isNotEmpty()) {
                            currentMatchIndex = if (currentMatchIndex - 1 < 0) searchMatchIndices.size - 1 else currentMatchIndex - 1
                            scope.launch {
                                listState.animateScrollToItem(searchMatchIndices[currentMatchIndex])
                            }
                        }
                    }
                )
            },
            bottomBar = {
                ChatInputBar(
                    isStreaming = isStreaming,
                    onSendMessage = { prompt ->
                        viewModel.sendMessage(prompt)
                    },
                    onStopStreaming = {
                        viewModel.stopStreaming()
                    },
                    onOpenSavedPrompts = {
                        showPromptsSheet = true
                    },
                    onOpenAttachmentActions = {
                        showAttachmentActionsSheet = true
                    },
                    onVoiceModeClick = {
                        showVoiceModeDialog = true
                    },
                    inputTextState = inputText,
                    onInputTextChanged = { inputText = it },
                    selectedModel = selectedModel,
                    onOpenModelPicker = { showModelSheet = true },
                    activePersona = activePersona,
                    onOpenPersonaPicker = { showPersonaSheet = true },
                    attachedImageUri = attachedImageUri,
                    onRemoveImage = { viewModel.setAttachedImage(null) },
                    attachedDocumentName = attachedDocument?.first,
                    onRemoveDocument = { viewModel.clearAttachments() },
                    isWebSearchEnabled = isWebSearchEnabled,
                    isMathToolEnabled = isMathToolEnabled,
                    modifier = Modifier.imePadding()
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (messages.isEmpty()) {
                    // Empty state hero
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (activePersona != null) {
                                    Text(
                                        text = activePersona?.emoji ?: "⚡",
                                        fontSize = 32.sp
                                    )
                                } else {
                                    ResoLogo(size = 40.dp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = activePersona?.name ?: "LocalMind AI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = activePersona?.description ?: "Powered by local on-device NPU/GPU & remote Ollama/OpenAI servers with deep reasoning support.",
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

    if (showPersonaSheet) {
        PersonaPickerSheet(
            personas = personas,
            activePersona = activePersona,
            onPersonaSelected = { persona ->
                viewModel.selectPersona(persona)
            },
            onManagePersonas = {
                onNavigateToPersonas()
            },
            onDismiss = { showPersonaSheet = false }
        )
    }

    if (showAttachmentActionsSheet) {
        AttachmentActionsSheet(
            isWebSearchEnabled = isWebSearchEnabled,
            onToggleWebSearch = { viewModel.toggleWebSearch(it) },
            isMathToolEnabled = isMathToolEnabled,
            onToggleMathTool = { viewModel.toggleMathTool(it) },
            onImageSelected = { uri -> viewModel.setAttachedImage(uri) },
            onDocumentSelected = { name, content -> viewModel.setAttachedDocument(name, content) },
            onOpenSavedPrompts = { showPromptsSheet = true },
            onOpenPersonaPicker = { showPersonaSheet = true },
            onOpenChatSettings = { showSettingsSheet = true },
            onDismiss = { showAttachmentActionsSheet = false }
        )
    }

    if (showVoiceModeDialog) {
        VoiceModeDialog(
            selectedModel = selectedModel,
            activePersona = activePersona,
            isStreaming = isStreaming,
            isTtsSpeaking = isTtsSpeaking,
            lastAiResponse = messages.lastOrNull { it.role == com.example.data.model.MessageRole.ASSISTANT }?.content,
            onSendMessage = { text -> viewModel.sendMessage(text) },
            onStopStreaming = { viewModel.stopStreaming() },
            onStopTts = { viewModel.stopSpeaking() },
            onDismiss = { showVoiceModeDialog = false }
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

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Conversation?") },
            text = { Text("This will remove all messages from this conversation. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCurrentConversation()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    editingMessage?.let { msgToEdit ->
        EditMessageDialog(
            initialText = msgToEdit.content,
            onDismiss = { editingMessage = null },
            onSave = { newContent ->
                viewModel.editMessage(msgToEdit.id, newContent)
                editingMessage = null
            }
        )
    }
}
