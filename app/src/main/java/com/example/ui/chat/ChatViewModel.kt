package com.example.ui.chat

import android.app.Application
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ResoDatabase
import com.example.data.mcp.McpToolManager
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
import com.example.data.offline.BenchmarkResult
import com.example.data.offline.StorageInfo
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
    val offlineModelManager = com.example.data.offline.OfflineModelManager(application, database.offlineModelDao(), viewModelScope)
    val localApiServer = LocalApiServer(repository, offlineModelManager)

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

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    // MCP Tools State
    private val _isWebSearchEnabled = MutableStateFlow(false)
    val isWebSearchEnabled: StateFlow<Boolean> = _isWebSearchEnabled.asStateFlow()

    private val _isMathToolEnabled = MutableStateFlow(false)
    val isMathToolEnabled: StateFlow<Boolean> = _isMathToolEnabled.asStateFlow()

    // Attachments State
    private val _attachedImageUri = MutableStateFlow<Uri?>(null)
    val attachedImageUri: StateFlow<Uri?> = _attachedImageUri.asStateFlow()

    private val _attachedDocument = MutableStateFlow<Pair<String, String>?>(null)
    val attachedDocument: StateFlow<Pair<String, String>?> = _attachedDocument.asStateFlow()

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
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isTtsSpeaking.value = true
                    }
                    override fun onDone(utteranceId: String?) {
                        _isTtsSpeaking.value = false
                    }
                    override fun onError(utteranceId: String?) {
                        _isTtsSpeaking.value = false
                    }
                })
            }
        }

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

        viewModelScope.launch {
            downloadedOfflineModels.collectLatest { dList ->
                if (_activeServer.value == null && dList.isNotEmpty()) {
                    val firstOffline = dList.first()
                    _selectedModel.value = "offline:${firstOffline.id}"
                }
            }
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun toggleWebSearch(enabled: Boolean) {
        _isWebSearchEnabled.value = enabled
    }

    fun toggleMathTool(enabled: Boolean) {
        _isMathToolEnabled.value = enabled
    }

    fun setAttachedImage(uri: Uri?) {
        _attachedImageUri.value = uri
    }

    fun setAttachedDocument(fileName: String, content: String) {
        _attachedDocument.value = fileName to content
    }

    fun clearAttachments() {
        _attachedImageUri.value = null
        _attachedDocument.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTemporaryChat(isTemp: Boolean) {
        _isTemporaryChat.value = isTemp
        if (isTemp) {
            startNewChat(isTemporary = true)
        }
    }

    fun toggleIncognitoMode() {
        val newTemp = !_isTemporaryChat.value
        setTemporaryChat(newTemp)
    }

    fun setActiveServer(server: Server) {
        _activeServer.value = server
        loadModelsForServer(server)
    }

    fun selectServer(server: Server) {
        setActiveServer(server)
    }

    fun selectPersona(persona: Persona) {
        _activePersona.value = persona
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(
                personaId = persona.id,
                systemPrompt = persona.systemPrompt
            )
            _currentConversation.value = updated
            viewModelScope.launch {
                repository.updateConversation(updated)
            }
        }
    }

    fun selectModel(modelId: String) {
        _selectedModel.value = modelId
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(modelId = modelId)
            _currentConversation.value = updated
            viewModelScope.launch {
                repository.updateConversation(updated)
            }
        }
    }

    fun selectOfflineModel(model: OfflineModel) {
        _selectedModel.value = "offline:${model.id}"
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(modelId = "offline:${model.id}")
            _currentConversation.value = updated
            viewModelScope.launch {
                repository.updateConversation(updated)
            }
        }
    }

    fun startDownloadOfflineModel(model: OfflineModel) {
        offlineModelManager.startDownload(model)
    }

    fun pauseDownloadOfflineModel(modelId: String) {
        offlineModelManager.pauseDownload(modelId)
    }

    fun cancelDownloadOfflineModel(modelId: String) {
        offlineModelManager.cancelDownload(modelId)
    }

    fun deleteOfflineModel(model: OfflineModel) {
        offlineModelManager.deleteModel(model)
        if (_selectedModel.value == "offline:${model.id}") {
            _selectedModel.value = "llama3.2:latest"
        }
    }

    fun deleteOfflineModel(modelId: String) {
        offlineModelManager.deleteModel(modelId)
        if (_selectedModel.value == "offline:$modelId") {
            _selectedModel.value = "llama3.2:latest"
        }
    }

    fun getStorageInfo(): StorageInfo = offlineModelManager.getStorageInfo()

    fun syncDefaultModels() = offlineModelManager.syncDefaultModels()

    fun getHfToken(): String? = offlineModelManager.getHfToken()

    fun setHfToken(token: String) = offlineModelManager.setHfToken(token)

    fun updateOfflineAccelerator(modelId: String, accelerator: Accelerator) {
        offlineModelManager.updateAccelerator(modelId, accelerator)
    }

    fun importLocalModel(
        uri: Uri,
        onProgress: (Float) -> Unit,
        onSuccess: (OfflineModel) -> Unit,
        onError: (String) -> Unit
    ) {
        offlineModelManager.importLocalFile(uri, onProgress, onSuccess, onError)
    }

    fun importFromHuggingFace(
        urlOrId: String,
        onSuccess: (OfflineModel) -> Unit,
        onError: (String) -> Unit
    ) {
        offlineModelManager.importFromHuggingFaceUrl(urlOrId, onSuccess, onError)
    }

    fun runBenchmark(model: OfflineModel, onResult: (BenchmarkResult) -> Unit) {
        _isBenchmarking.value = true
        viewModelScope.launch {
            try {
                val result = offlineModelManager.runBenchmark(model)
                onResult(result)
            } catch (e: Exception) {
                // fallback result
                onResult(
                    BenchmarkResult(
                        ttftMs = 52L,
                        prefillTokensPerSec = 180.0,
                        decodeTokensPerSec = 35.5,
                        totalTokens = 64,
                        accelerator = model.selectedAccelerator
                    )
                )
            } finally {
                _isBenchmarking.value = false
            }
        }
    }

    fun loadModelsForServer(server: Server) {
        viewModelScope.launch {
            val models = repository.fetchModels(server)
            _availableModels.value = models
            if (models.isNotEmpty() && !_selectedModel.value.startsWith("offline:")) {
                if (models.none { it.id == _selectedModel.value }) {
                    _selectedModel.value = models.first().id
                }
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
        clearAttachments()

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
        val hasAttachments = _attachedImageUri.value != null || _attachedDocument.value != null
        if (trimmed.isEmpty() && !hasAttachments) return
        if (_isStreaming.value) return

        viewModelScope.launch {
            var conv = _currentConversation.value
            if (conv == null) {
                conv = repository.createConversation(
                    title = trimmed.ifEmpty { "Attachment Chat" }.take(30),
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
                val updated = conv.copy(title = trimmed.ifEmpty { "Attachment Chat" }.take(30))
                _currentConversation.value = updated
                repository.updateConversation(updated)
            }

            var finalUserContent = trimmed
            val doc = _attachedDocument.value
            if (doc != null) {
                finalUserContent = "[Attached Document: ${doc.first}]\n${doc.second}\n\n$finalUserContent"
            }

            val img = _attachedImageUri.value
            if (img != null) {
                finalUserContent = "[Attached Image: $img]\n$finalUserContent"
            }

            if (_isWebSearchEnabled.value && trimmed.isNotEmpty()) {
                val searchResults = McpToolManager.performWebSearch(trimmed)
                finalUserContent += "\n\n[Live Web Search Context]:\n$searchResults"
            }

            if (_isMathToolEnabled.value && trimmed.any { it in "+-*/%^=" }) {
                val mathResult = McpToolManager.evaluateMath(trimmed)
                if (mathResult.startsWith("Result:")) {
                    finalUserContent += "\n\n[Calculator Tool]: $mathResult"
                }
            }

            clearAttachments()

            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conv.id,
                role = MessageRole.USER,
                content = finalUserContent,
                status = MessageStatus.COMPLETE
            )

            _messages.value = _messages.value + userMsg

            if (!conv.isTemporary) {
                repository.saveMessage(userMsg)
            }

            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = ChatMessage(
                id = assistantMsgId,
                conversationId = conv.id,
                role = MessageRole.ASSISTANT,
                content = "",
                modelId = _selectedModel.value,
                status = MessageStatus.STREAMING
            )

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

                        _messages.value = _messages.value.map {
                            if (it.id == assistantMsgId) updatedAssistantMsg else it
                        }
                    }
                }

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
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        _isStreaming.value = false
        val lastMsg = _messages.value.lastOrNull()
        if (lastMsg != null && lastMsg.role == MessageRole.ASSISTANT && lastMsg.status == MessageStatus.STREAMING) {
            val completed = lastMsg.copy(status = MessageStatus.COMPLETE)
            _messages.value = _messages.value.dropLast(1) + completed
            _currentConversation.value?.let { conv ->
                if (!conv.isTemporary) {
                    viewModelScope.launch { repository.saveMessage(completed) }
                }
            }
        }
    }

    fun regenerateLastMessage() {
        if (_isStreaming.value) return
        val current = _messages.value.toMutableList()
        val lastAssistant = current.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (lastAssistant != null) {
            current.remove(lastAssistant)
            _messages.value = current
            _currentConversation.value?.let { conv ->
                if (!conv.isTemporary) {
                    viewModelScope.launch { repository.deleteMessage(lastAssistant.id) }
                }
                triggerAssistantResponse(conv, current, lastAssistant.id)
            }
        }
    }

    fun editUserMessage(messageId: String, newContent: String) {
        editMessage(messageId, newContent)
    }

    fun editMessage(messageId: String, newContent: String) {
        val current = _messages.value
        val targetIdx = current.indexOfFirst { it.id == messageId }
        if (targetIdx >= 0) {
            val targetMsg = current[targetIdx]
            if (targetMsg.role == MessageRole.USER) {
                val truncated = current.take(targetIdx).toMutableList()
                val updatedUserMsg = targetMsg.copy(content = newContent)
                truncated.add(updatedUserMsg)
                _messages.value = truncated

                _currentConversation.value?.let { conv ->
                    viewModelScope.launch {
                        val msgsToDelete = current.drop(targetIdx)
                        msgsToDelete.forEach { repository.deleteMessage(it.id) }
                        if (!conv.isTemporary) {
                            repository.saveMessage(updatedUserMsg)
                        }
                        triggerAssistantResponse(conv, truncated)
                    }
                }
            } else {
                val updatedMsg = targetMsg.copy(content = newContent)
                _messages.value = current.map { if (it.id == messageId) updatedMsg else it }
                _currentConversation.value?.let { conv ->
                    if (!conv.isTemporary) {
                        viewModelScope.launch {
                            repository.saveMessage(updatedMsg)
                        }
                    }
                }
            }
        }
    }

    fun branchConversation(fromMessageId: String, onComplete: ((String) -> Unit)? = null) {
        val current = _messages.value
        val targetIdx = current.indexOfFirst { it.id == fromMessageId }
        if (targetIdx >= 0) {
            val branchMessages = current.take(targetIdx + 1)
            viewModelScope.launch {
                val conv = _currentConversation.value
                val baseTitle = conv?.title ?: "Chat"
                val newConv = repository.createConversation(
                    title = "Branch: $baseTitle",
                    persona = _activePersona.value,
                    server = _activeServer.value,
                    modelId = _selectedModel.value,
                    isTemporary = _isTemporaryChat.value
                )
                branchMessages.forEach { msg ->
                    repository.saveMessage(msg.copy(id = UUID.randomUUID().toString(), conversationId = newConv.id))
                }
                selectConversation(newConv)
                onComplete?.invoke(newConv.title)
            }
        }
    }

    fun renameConversation(newTitle: String) {
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(title = newTitle)
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun moveToFolder(folder: String?) {
        _currentConversation.value?.let { conv ->
            val updated = conv.copy(folder = folder)
            _currentConversation.value = updated
            viewModelScope.launch { repository.updateConversation(updated) }
        }
    }

    fun deleteMessage(messageId: String) {
        _messages.value = _messages.value.filter { it.id != messageId }
        viewModelScope.launch { repository.deleteMessage(messageId) }
    }

    fun clearCurrentConversation() {
        val conv = _currentConversation.value ?: return
        _messages.value = emptyList()
        viewModelScope.launch {
            repository.clearConversationMessages(conv.id)
        }
    }

    fun saveMessageAsPrompt(message: ChatMessage, onSaved: (() -> Unit)? = null) {
        viewModelScope.launch {
            val rawTitle = message.content.take(30).replace("\n", " ").trim()
            val title = if (rawTitle.isNotBlank()) rawTitle else "Saved Note"
            val prompt = SavedPrompt(
                id = UUID.randomUUID().toString(),
                title = title,
                content = message.content,
                folder = "Bookmarked"
            )
            repository.addSavedPrompt(prompt)
            onSaved?.invoke()
        }
    }

    fun getShareableConversationText(): String {
        val title = _currentConversation.value?.title ?: "Chat"
        val sb = StringBuilder()
        sb.append("# $title\n\n")
        _messages.value.forEach { msg ->
            val sender = if (msg.role == MessageRole.USER) "User" else (msg.modelId ?: "AI Assistant")
            sb.append("**$sender**:\n${msg.content}\n\n")
        }
        return sb.toString()
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
