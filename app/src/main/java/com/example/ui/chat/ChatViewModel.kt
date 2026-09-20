package com.example.ui.chat

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ResoDatabase
import com.example.data.model.Accelerator
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.ModelInfo
import com.example.data.model.OfflineModel
import com.example.data.model.Persona
import com.example.data.model.SavedPrompt
import com.example.data.model.Server
import com.example.data.repository.ResoRepository
import com.example.server.LocalApiServer
import com.example.server.ServerLogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ResoDatabase.getDatabase(application)
    val repository = ResoRepository(database)
    val localApiServer = LocalApiServer(repository)
    val offlineModelManager = com.example.data.offline.OfflineModelManager(application, database.offlineModelDao(), viewModelScope)

    val servers: StateFlow<List<Server>> = repository.servers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val offlineModels: StateFlow<List<OfflineModel>> = offlineModelManager.allModels.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloadedOfflineModels: StateFlow<List<OfflineModel>> = offlineModelManager.downloadedModels.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val downloadProgress: StateFlow<Map<String, OfflineModel>> = offlineModelManager.downloadProgress

    val conversations: StateFlow<List<Conversation>> = repository.conversations.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val personas: StateFlow<List<Persona>> = repository.personas.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val savedPrompts: StateFlow<List<SavedPrompt>> = repository.savedPrompts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _activeServer = MutableStateFlow<Server?>(null)
    val activeServer: StateFlow<Server?> = _activeServer.asStateFlow()

    private val _activePersona = MutableStateFlow<Persona?>(null)
    val activePersona: StateFlow<Persona?> = _activePersona.asStateFlow()

    private val _selectedModel = MutableStateFlow<String>("llama3.2:latest")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isTemporaryChat = MutableStateFlow(false)
    val isTemporaryChat: StateFlow<Boolean> = _isTemporaryChat.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private var streamJob: Job? = null
    private var messagesJob: Job? = null

    // TTS
    private var tts: TextToSpeech? = null
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }

        // Initialize active server and persona when database populates
        viewModelScope.launch {
            servers.collectLatest { serverList ->
                if (_activeServer.value == null && serverList.isNotEmpty()) {
                    val defaultServer = serverList.firstOrNull { it.isDefault } ?: serverList.first()
                    _activeServer.value = defaultServer
                    loadModelsForServer(defaultServer)
                }
            }
        }

        viewModelScope.launch {
            personas.collectLatest { personaList ->
                if (_activePersona.value == null && personaList.isNotEmpty()) {
                    _activePersona.value = personaList.first()
                }
            }
        }

        // Automatically load the latest conversation on startup if available
        viewModelScope.launch {
            conversations.collectLatest { convList ->
                if (_currentConversation.value == null && convList.isNotEmpty()) {
                    val firstConv = convList.firstOrNull { !it.isTemporary } ?: convList.first()
                    selectConversation(firstConv)
                }
            }
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectServer(server: Server) {
        _activeServer.value = server
        loadModelsForServer(server)
    }

    fun selectModel(modelId: String) {
        _selectedModel.value = modelId
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(modelId = modelId)
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun selectPersona(persona: Persona) {
        _activePersona.value = persona
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(
                personaId = persona.id,
                systemPrompt = persona.systemPrompt
            )
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun setTemporaryChat(isTemp: Boolean) {
        _isTemporaryChat.value = isTemp
        startNewChat(isTemporary = isTemp)
    }

    fun loadModelsForServer(server: Server) {
        viewModelScope.launch {
            val models = repository.fetchModels(server)
            _availableModels.value = models
            if (models.isNotEmpty() && models.none { it.id == _selectedModel.value }) {
                _selectedModel.value = models.first().id
            }
        }
    }

    fun selectConversation(conversation: Conversation) {
        offlineModelManager.resetChatConversation()
        _currentConversation.value = conversation
        _isTemporaryChat.value = conversation.isTemporary
        conversation.modelId?.let { _selectedModel.value = it }

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(conversation.id).collectLatest { msgs ->
                // Avoid replacing during active live streaming
                if (!_isStreaming.value) {
                    _messages.value = msgs
                }
            }
        }
    }

    fun startNewChat(isTemporary: Boolean = _isTemporaryChat.value) {
        streamJob?.cancel()
        messagesJob?.cancel()
        _isStreaming.value = false
        stopSpeaking()
        offlineModelManager.resetChatConversation()

        viewModelScope.launch {
            val conv = repository.createConversation(
                title = "New Chat",
                persona = _activePersona.value,
                server = _activeServer.value,
                modelId = _selectedModel.value,
                isTemporary = isTemporary
            )
            _currentConversation.value = conv
            _messages.value = emptyList()

            if (!isTemporary) {
                messagesJob = launch {
                    repository.getMessages(conv.id).collectLatest { msgs ->
                        if (!_isStreaming.value) {
                            _messages.value = msgs
                        }
                    }
                }
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_currentConversation.value?.id == id) {
                startNewChat()
            }
        }
    }

    fun togglePinConversation(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(id, !isPinned)
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return

        viewModelScope.launch {
            var conv = _currentConversation.value
            if (conv == null) {
                conv = repository.createConversation(
                    title = trimmed.take(30),
                    persona = _activePersona.value,
                    server = _activeServer.value,
                    modelId = _selectedModel.value,
                    isTemporary = _isTemporaryChat.value
                )
                _currentConversation.value = conv

                if (!conv.isTemporary) {
                    messagesJob?.cancel()
                    messagesJob = launch {
                        repository.getMessages(conv.id).collectLatest { msgs ->
                            if (!_isStreaming.value) {
                                _messages.value = msgs
                            }
                        }
                    }
                }
            } else if (conv.messageCount == 0 && !conv.isTemporary) {
                val updated = conv.copy(title = trimmed.take(30))
                _currentConversation.value = updated
                repository.updateConversation(updated)
            }

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = MessageRole.USER,
                content = trimmed,
                status = MessageStatus.COMPLETE
            )

            // CRITICAL: Always immediately update in-memory state so Compose displays the message right away!
            _messages.value = _messages.value + userMsg

            if (!conv.isTemporary) {
                repository.saveMessage(userMsg)
            }

            // Create Assistant placeholder
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                conversationId = conv.id,
                role = MessageRole.ASSISTANT,
                content = "",
                modelId = _selectedModel.value,
                status = MessageStatus.STREAMING
            )

            // CRITICAL: Always immediately add assistant placeholder to in-memory messages
            _messages.value = _messages.value + assistantMsg

            triggerAssistantResponse(conv, _messages.value, assistantMsgId)
        }
    }

    private fun triggerAssistantResponse(
        conv: Conversation,
        currentMsgs: List<ChatMessage>,
        assistantMsgId: String = UUID.randomUUID().toString()
    ) {
        _isStreaming.value = true

        val server = _activeServer.value ?: Server(
            id = "temp-ollama",
            name = "Local Ollama",
            type = com.example.data.model.ServerType.OLLAMA,
            host = "10.0.2.2",
            port = 11434
        )

        var accumulatedText = ""
        var accumulatedReasoning: String? = null
        var lastTokensPerSec = 0.0
        var tokenCount = 0

        val initialAssistantMsg = _messages.value.firstOrNull { it.id == assistantMsgId } ?: ChatMessage(
            id = assistantMsgId,
            conversationId = conv.id,
            role = MessageRole.ASSISTANT,
            content = "",
            modelId = _selectedModel.value,
            status = MessageStatus.STREAMING
        )

        streamJob = viewModelScope.launch {
            try {
                val currentSelected = _selectedModel.value
                val isOffline = currentSelected.startsWith("offline:") || offlineModels.value.any { it.id == currentSelected }
                val offlineModel = if (isOffline) {
                    val cleanId = currentSelected.removePrefix("offline:")
                    offlineModels.value.firstOrNull { it.id == cleanId }
                        ?: downloadedOfflineModels.value.firstOrNull { it.id == cleanId }
                } else null

                if (offlineModel != null) {
                    offlineModelManager.streamChat(
                        model = offlineModel,
                        messages = currentMsgs,
                        systemPrompt = conv.systemPrompt ?: _activePersona.value?.systemPrompt,
                        temperature = conv.temperature,
                        topP = conv.topP,
                        maxTokens = conv.maxTokens
                    ).collect { chunk ->
                        if (chunk.reasoningText != null) {
                            accumulatedReasoning = (accumulatedReasoning ?: "") + chunk.reasoningText
                        }
                        if (chunk.text.isNotEmpty()) {
                            accumulatedText += chunk.text
                        }
                        tokenCount = chunk.tokensGenerated
                        lastTokensPerSec = chunk.tokensPerSecond

                        val updatedAssistantMsg = initialAssistantMsg.copy(
                            content = accumulatedText,
                            reasoningContent = accumulatedReasoning,
                            status = if (chunk.isDone) MessageStatus.COMPLETE else MessageStatus.STREAMING,
                            tokenCount = tokenCount,
                            tokensPerSecond = if (lastTokensPerSec > 0) lastTokensPerSec else null
                        )

                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsgId) updatedAssistantMsg else it
                        }
                    }
                } else {
                    repository.streamChat(
                        server = server,
                        modelId = _selectedModel.value,
                        messages = currentMsgs,
                        systemPrompt = conv.systemPrompt ?: _activePersona.value?.systemPrompt,
                        temperature = conv.temperature,
                        topP = conv.topP,
                        maxTokens = conv.maxTokens
                    ).collect { chunk ->
                        if (chunk.reasoningText != null) {
                            accumulatedReasoning = (accumulatedReasoning ?: "") + chunk.reasoningText
                        }
                        if (chunk.text.isNotEmpty()) {
                            accumulatedText += chunk.text
                        }
                        tokenCount = chunk.tokensGenerated
                        lastTokensPerSec = chunk.tokensPerSecond

                        val updatedAssistantMsg = initialAssistantMsg.copy(
                            content = accumulatedText,
                            reasoningContent = accumulatedReasoning,
                            status = if (chunk.isDone) MessageStatus.COMPLETE else MessageStatus.STREAMING,
                            tokenCount = tokenCount,
                            tokensPerSecond = if (lastTokensPerSec > 0) lastTokensPerSec else null
                        )

                        // Update in-memory message state on every chunk for instant, fluid UI response
                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsgId) updatedAssistantMsg else it
                        }
                    }
                }

                // On stream completion, persist final assistant message
                val finalMsg = initialAssistantMsg.copy(
                    content = accumulatedText,
                    reasoningContent = accumulatedReasoning,
                    status = MessageStatus.COMPLETE,
                    tokenCount = tokenCount,
                    tokensPerSecond = if (lastTokensPerSec > 0) lastTokensPerSec else null
                )
                if (!conv.isTemporary) {
                    repository.saveMessage(finalMsg)
                }
            } catch (e: Exception) {
                val errorMsg = initialAssistantMsg.copy(
                    content = accumulatedText.ifEmpty { "Error: ${e.message}" },
                    reasoningContent = accumulatedReasoning,
                    status = MessageStatus.ERROR,
                    errorMessage = e.message
                )
                _messages.value = _messages.value.map {
                    if (it.id == assistantMsgId) errorMsg else it
                }
                if (!conv.isTemporary) {
                    repository.saveMessage(errorMsg)
                }
            } finally {
                _isStreaming.value = false
                // Update conversation preview and count
                val lastPreview = accumulatedText.take(60)
                val updatedConv = conv.copy(
                    messageCount = conv.messageCount + 2,
                    lastMessagePreview = lastPreview,
                    updatedAt = System.currentTimeMillis()
                )
                _currentConversation.value = updatedConv
                if (!conv.isTemporary) {
                    repository.updateConversation(updatedConv)
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        offlineModelManager.cancelInference()
        _isStreaming.value = false
    }

    fun selectOfflineModel(model: OfflineModel) {
        val key = "offline:${model.id}"
        _selectedModel.value = key
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(modelId = key)
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun startDownloadOfflineModel(model: OfflineModel) = offlineModelManager.startDownload(model)

    fun cancelDownloadOfflineModel(modelId: String) = offlineModelManager.cancelDownload(modelId)

    fun deleteOfflineModel(model: OfflineModel) = offlineModelManager.deleteModel(model)

    fun updateOfflineAccelerator(modelId: String, accelerator: Accelerator) =
        offlineModelManager.updateAccelerator(modelId, accelerator)

    fun importLocalModel(
        uri: android.net.Uri,
        onProgress: (Float) -> Unit,
        onSuccess: (OfflineModel) -> Unit,
        onError: (String) -> Unit
    ) = offlineModelManager.importLocalFile(uri, onProgress, onSuccess, onError)

    fun importFromHuggingFace(
        urlOrId: String,
        onSuccess: (OfflineModel) -> Unit,
        onError: (String) -> Unit
    ) = offlineModelManager.importFromHuggingFaceUrl(urlOrId, onSuccess, onError)

    fun getStorageInfo(): com.example.data.offline.StorageInfo = offlineModelManager.getStorageInfo()

    fun getHfToken(): String? = offlineModelManager.getHfToken()

    fun setHfToken(token: String) = offlineModelManager.setHfToken(token)

    val benchmarkResult = MutableStateFlow<com.example.data.offline.BenchmarkResult?>(null)
    val isBenchmarking = MutableStateFlow(false)

    fun runBenchmark(model: OfflineModel, onComplete: (com.example.data.offline.BenchmarkResult) -> Unit = {}) {
        viewModelScope.launch {
            isBenchmarking.value = true
            try {
                val result = offlineModelManager.runBenchmark(model)
                benchmarkResult.value = result
                onComplete(result)
            } finally {
                isBenchmarking.value = false
            }
        }
    }

    fun syncDefaultModels() {
        offlineModelManager.syncDefaultModels()
    }

    fun regenerateLastMessage() {
        if (_isStreaming.value) return
        val conv = _currentConversation.value ?: return
        val currentMsgs = _messages.value
        val lastAssistant = currentMsgs.lastOrNull { it.role == MessageRole.ASSISTANT }
        val lastUser = currentMsgs.lastOrNull { it.role == MessageRole.USER } ?: return

        viewModelScope.launch {
            if (lastAssistant != null) {
                if (!conv.isTemporary) {
                    repository.deleteMessage(lastAssistant.id)
                }
                _messages.value = _messages.value.filter { it.id != lastAssistant.id }
            }

            // Create fresh assistant message placeholder
            val assistantMsgId = UUID.randomUUID().toString()
            val newAssistantMsg = ChatMessage(
                id = assistantMsgId,
                conversationId = conv.id,
                role = MessageRole.ASSISTANT,
                content = "",
                modelId = _selectedModel.value,
                status = MessageStatus.STREAMING
            )
            _messages.value = _messages.value + newAssistantMsg

            triggerAssistantResponse(conv, _messages.value, assistantMsgId)
        }
    }

    fun toggleIncognitoMode() {
        val newIncognito = !_isTemporaryChat.value
        _isTemporaryChat.value = newIncognito
        startNewChat(isTemporary = newIncognito)
    }

    fun renameConversation(newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(title = trimmed, updatedAt = System.currentTimeMillis())
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun moveToFolder(folder: String?) {
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(folder = folder?.trim()?.ifEmpty { null }, updatedAt = System.currentTimeMillis())
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun clearCurrentConversation() {
        val conv = _currentConversation.value ?: return
        viewModelScope.launch {
            if (!conv.isTemporary) {
                repository.clearConversationMessages(conv.id)
            }
            _messages.value = emptyList()
            _currentConversation.value = conv.copy(messageCount = 0, lastMessagePreview = null)
        }
    }

    fun branchConversation(fromMessageId: String) {
        val conv = _currentConversation.value ?: return
        val currentMsgs = _messages.value
        val index = currentMsgs.indexOfFirst { it.id == fromMessageId }
        if (index == -1) return
        val messagesUpTo = currentMsgs.subList(0, index + 1)

        viewModelScope.launch {
            val branchedConv = repository.branchConversation(
                originalConv = conv,
                messagesUpTo = messagesUpTo,
                newTitle = "Branch: ${conv.title}"
            )
            _currentConversation.value = branchedConv
            _messages.value = messagesUpTo
        }
    }

    fun deleteMessage(messageId: String) {
        val conv = _currentConversation.value ?: return
        viewModelScope.launch {
            if (!conv.isTemporary) {
                repository.deleteMessage(messageId)
            }
            _messages.value = _messages.value.filter { it.id != messageId }
        }
    }

    fun editUserMessage(messageId: String, newContent: String) {
        val currentMsgs = _messages.value
        val index = currentMsgs.indexOfFirst { it.id == messageId }
        if (index == -1) return

        // Delete this message and any subsequent messages
        val messagesToDelete = currentMsgs.subList(index, currentMsgs.size)
        viewModelScope.launch {
            if (_currentConversation.value?.isTemporary != true) {
                messagesToDelete.forEach { repository.deleteMessage(it.id) }
            }
            _messages.value = currentMsgs.subList(0, index)
            sendMessage(newContent)
        }
    }

    fun saveMessageAsPrompt(message: ChatMessage) {
        viewModelScope.launch {
            val title = message.content.lines().firstOrNull()?.take(40) ?: "Saved Message"
            val prompt = SavedPrompt(
                id = UUID.randomUUID().toString(),
                title = title,
                content = message.content,
                folder = "Bookmarked"
            )
            repository.addSavedPrompt(prompt)
        }
    }

    fun updateConversationSettings(temp: Float, topP: Float, maxTokens: Int, systemPrompt: String?) {
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(
                temperature = temp,
                topP = topP,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt
            )
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun speakText(text: String) {
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ResoTTS")
        _isTtsSpeaking.value = true
    }

    fun stopSpeaking() {
        tts?.stop()
        _isTtsSpeaking.value = false
    }

    // Local API Server operations
    fun startLocalApiServer(): Boolean {
        return localApiServer.startServer(
            activeServerProvider = { _activeServer.value },
            activeModelProvider = { _selectedModel.value }
        )
    }

    fun stopLocalApiServer() {
        localApiServer.stopServer()
    }

    fun setServerPort(port: Int) {
        localApiServer.setPort(port)
    }

    fun setServerRequireAuth(required: Boolean) {
        localApiServer.setRequireAuth(required)
    }

    fun regenerateApiKey() {
        localApiServer.regenerateApiKey()
    }

    fun clearServerLogs() {
        localApiServer.clearLogs()
    }

    override fun onCleared() {
        super.onCleared()
        localApiServer.stopServer()
        tts?.stop()
        tts?.shutdown()
    }
}
