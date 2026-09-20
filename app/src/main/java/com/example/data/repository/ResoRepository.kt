package com.example.data.repository

import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationEntity
import com.example.data.local.PersonaEntity
import com.example.data.local.ResoDatabase
import com.example.data.local.SavedPromptEntity
import com.example.data.local.ServerEntity
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.ModelInfo
import com.example.data.model.Persona
import com.example.data.model.SavedPrompt
import com.example.data.model.Server
import com.example.data.remote.LlmApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ResoRepository(
    private val database: ResoDatabase,
    private val apiClient: LlmApiClient = LlmApiClient()
) {
    val servers: Flow<List<Server>> = database.serverDao().getAllServers().map { list ->
        list.map { it.toDomain() }
    }

    val conversations: Flow<List<Conversation>> = database.conversationDao().getConversations().map { list ->
        list.map { it.toDomain() }
    }

    val personas: Flow<List<Persona>> = database.personaDao().getAllPersonas().map { list ->
        list.map { it.toDomain() }
    }

    val savedPrompts: Flow<List<SavedPrompt>> = database.savedPromptDao().getAllSavedPrompts().map { list ->
        list.map { it.toDomain() }
    }

    val offlineModels: Flow<List<com.example.data.model.OfflineModel>> = database.offlineModelDao().getAllOfflineModels().map { list ->
        list.map { it.toDomain() }
    }

    val downloadedOfflineModels: Flow<List<com.example.data.model.OfflineModel>> = database.offlineModelDao().getDownloadedModels().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getOfflineModel(id: String): com.example.data.model.OfflineModel? = withContext(Dispatchers.IO) {
        database.offlineModelDao().getModelById(id)?.toDomain()
    }

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> =
        database.chatMessageDao().getMessagesForConversation(conversationId).map { list ->
            list.map { it.toDomain() }
        }

    fun searchConversations(query: String): Flow<List<Conversation>> =
        database.conversationDao().searchConversations(query).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        database.conversationDao().getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(
        title: String = "New Chat",
        persona: Persona? = null,
        server: Server? = null,
        modelId: String? = null,
        isTemporary: Boolean = false
    ): Conversation = withContext(Dispatchers.IO) {
        val resolvedServer = server ?: database.serverDao().getDefaultServer()?.toDomain()
        val defaultModel = modelId ?: if (resolvedServer?.type == com.example.data.model.ServerType.OLLAMA) "llama3.2:latest" else "meta-llama/llama-3.2-3b-instruct"
        val conv = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            personaId = persona?.id,
            serverId = resolvedServer?.id,
            modelId = defaultModel,
            systemPrompt = persona?.systemPrompt,
            isTemporary = isTemporary
        )
        if (!isTemporary) {
            database.conversationDao().insertConversation(ConversationEntity.fromDomain(conv))
        }
        conv
    }

    suspend fun updateConversation(conv: Conversation) = withContext(Dispatchers.IO) {
        if (!conv.isTemporary) {
            database.conversationDao().updateConversation(ConversationEntity.fromDomain(conv))
        }
    }

    suspend fun setPinned(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        database.conversationDao().setPinned(id, isPinned)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        database.chatMessageDao().deleteMessagesForConversation(id)
        database.conversationDao().deleteConversation(id)
    }

    suspend fun saveMessage(msg: ChatMessage) = withContext(Dispatchers.IO) {
        database.chatMessageDao().insertMessage(ChatMessageEntity.fromDomain(msg))
    }

    suspend fun updateMessage(msg: ChatMessage) = withContext(Dispatchers.IO) {
        database.chatMessageDao().updateMessage(ChatMessageEntity.fromDomain(msg))
    }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        database.chatMessageDao().deleteMessage(id)
    }

    suspend fun clearConversationMessages(conversationId: String) = withContext(Dispatchers.IO) {
        database.chatMessageDao().deleteMessagesForConversation(conversationId)
        database.conversationDao().getConversationById(conversationId)?.let { entity ->
            database.conversationDao().updateConversation(
                entity.copy(messageCount = 0, lastMessagePreview = null, updatedAt = System.currentTimeMillis())
            )
        }
    }

    suspend fun branchConversation(
        originalConv: Conversation,
        messagesUpTo: List<ChatMessage>,
        newTitle: String
    ): Conversation = withContext(Dispatchers.IO) {
        val newConv = Conversation(
            id = UUID.randomUUID().toString(),
            title = newTitle,
            personaId = originalConv.personaId,
            serverId = originalConv.serverId,
            modelId = originalConv.modelId,
            systemPrompt = originalConv.systemPrompt,
            temperature = originalConv.temperature,
            topP = originalConv.topP,
            maxTokens = originalConv.maxTokens,
            messageCount = messagesUpTo.size,
            lastMessagePreview = messagesUpTo.lastOrNull()?.content?.take(60),
            isTemporary = originalConv.isTemporary,
            folder = originalConv.folder
        )
        if (!newConv.isTemporary) {
            database.conversationDao().insertConversation(ConversationEntity.fromDomain(newConv))
            messagesUpTo.forEach { msg ->
                val cloned = msg.copy(
                    id = UUID.randomUUID().toString(),
                    conversationId = newConv.id
                )
                database.chatMessageDao().insertMessage(ChatMessageEntity.fromDomain(cloned))
            }
        }
        newConv
    }

    suspend fun testServerConnection(server: Server): ConnectionStatus = withContext(Dispatchers.IO) {
        database.serverDao().updateServer(ServerEntity.fromDomain(server.copy(status = ConnectionStatus.CHECKING)))
        val result = apiClient.testConnection(server)
        val newStatus = if (result.isSuccess) ConnectionStatus.CONNECTED else ConnectionStatus.ERROR
        val updated = server.copy(
            status = newStatus,
            lastConnectedAt = if (newStatus == ConnectionStatus.CONNECTED) System.currentTimeMillis() else server.lastConnectedAt
        )
        database.serverDao().updateServer(ServerEntity.fromDomain(updated))
        newStatus
    }

    suspend fun fetchModels(server: Server): List<ModelInfo> = withContext(Dispatchers.IO) {
        val result = apiClient.fetchModels(server)
        result.getOrElse {
            // Provide default model presets if offline
            listOf(
                ModelInfo("llama3.2:latest", "Llama 3.2 3B", "Local Ollama standard", 8192, server.id),
                ModelInfo("deepseek-r1:8b", "DeepSeek R1 8B", "Reasoning model with thinking tokens", 16384, server.id, isReasoningModel = true),
                ModelInfo("qwen2.5-coder:7b", "Qwen 2.5 Coder 7B", "Coding specialist", 32768, server.id),
                ModelInfo("mistral:7b", "Mistral 7B", "General purpose instruction tuned", 8192, server.id)
            )
        }
    }

    suspend fun addServer(server: Server) = withContext(Dispatchers.IO) {
        database.serverDao().insertServer(ServerEntity.fromDomain(server))
    }

    suspend fun updateServer(server: Server) = withContext(Dispatchers.IO) {
        database.serverDao().updateServer(ServerEntity.fromDomain(server))
    }

    suspend fun setDefaultServer(id: String) = withContext(Dispatchers.IO) {
        database.serverDao().setDefaultServer(id)
    }

    suspend fun deleteServer(id: String) = withContext(Dispatchers.IO) {
        database.serverDao().deleteServer(id)
    }

    suspend fun addPersona(persona: Persona) = withContext(Dispatchers.IO) {
        database.personaDao().insertPersona(PersonaEntity.fromDomain(persona))
    }

    suspend fun deleteCustomPersona(id: String) = withContext(Dispatchers.IO) {
        database.personaDao().deleteCustomPersona(id)
    }

    suspend fun addSavedPrompt(prompt: SavedPrompt) = withContext(Dispatchers.IO) {
        database.savedPromptDao().insertPrompt(SavedPromptEntity.fromDomain(prompt))
    }

    suspend fun deleteSavedPrompt(id: String) = withContext(Dispatchers.IO) {
        database.savedPromptDao().deletePrompt(id)
    }

    fun streamChat(
        server: Server,
        modelId: String,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        maxTokens: Int = 2048
    ) = apiClient.streamChat(
        server = server,
        modelId = modelId,
        messages = messages,
        systemPrompt = systemPrompt,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens
    )
}
