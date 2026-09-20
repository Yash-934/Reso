package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.Persona
import com.example.data.model.SavedPrompt
import com.example.data.model.Server
import com.example.data.model.ServerType

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val host: String,
    val port: Int,
    val apiKey: String?,
    val pathPrefix: String?,
    val isDefault: Boolean,
    val createdAt: Long,
    val lastConnectedAt: Long,
    val status: String
) {
    fun toDomain(): Server = Server(
        id = id,
        name = name,
        type = try { ServerType.valueOf(type) } catch (e: Exception) { ServerType.OLLAMA },
        host = host,
        port = port,
        apiKey = apiKey,
        pathPrefix = pathPrefix,
        isDefault = isDefault,
        createdAt = createdAt,
        lastConnectedAt = lastConnectedAt,
        status = try { ConnectionStatus.valueOf(status) } catch (e: Exception) { ConnectionStatus.DISCONNECTED }
    )

    companion object {
        fun fromDomain(server: Server): ServerEntity = ServerEntity(
            id = server.id,
            name = server.name,
            type = server.type.name,
            host = server.host,
            port = server.port,
            apiKey = server.apiKey,
            pathPrefix = server.pathPrefix,
            isDefault = server.isDefault,
            createdAt = server.createdAt,
            lastConnectedAt = server.lastConnectedAt,
            status = server.status.name
        )
    }
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean,
    val personaId: String?,
    val serverId: String?,
    val modelId: String?,
    val messageCount: Int,
    val lastMessagePreview: String?,
    val systemPrompt: String?,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val isTemporary: Boolean,
    val folder: String?
) {
    fun toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
        personaId = personaId,
        serverId = serverId,
        modelId = modelId,
        messageCount = messageCount,
        lastMessagePreview = lastMessagePreview,
        systemPrompt = systemPrompt,
        temperature = temperature,
        topP = topP,
        maxTokens = maxTokens,
        isTemporary = isTemporary,
        folder = folder
    )

    companion object {
        fun fromDomain(c: Conversation): ConversationEntity = ConversationEntity(
            id = c.id,
            title = c.title,
            createdAt = c.createdAt,
            updatedAt = c.updatedAt,
            isPinned = c.isPinned,
            personaId = c.personaId,
            serverId = c.serverId,
            modelId = c.modelId,
            messageCount = c.messageCount,
            lastMessagePreview = c.lastMessagePreview,
            systemPrompt = c.systemPrompt,
            temperature = c.temperature,
            topP = c.topP,
            maxTokens = c.maxTokens,
            isTemporary = c.isTemporary,
            folder = c.folder
        )
    }
}

@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoningContent: String?,
    val modelId: String?,
    val status: String,
    val createdAt: Long,
    val tokenCount: Int?,
    val tokensPerSecond: Double?,
    val errorMessage: String?
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = try { MessageRole.valueOf(role) } catch (e: Exception) { MessageRole.USER },
        content = content,
        reasoningContent = reasoningContent,
        modelId = modelId,
        status = try { MessageStatus.valueOf(status) } catch (e: Exception) { MessageStatus.COMPLETE },
        createdAt = createdAt,
        tokenCount = tokenCount,
        tokensPerSecond = tokensPerSecond,
        errorMessage = errorMessage
    )

    companion object {
        fun fromDomain(m: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = m.id,
            conversationId = m.conversationId,
            role = m.role.name,
            content = m.content,
            reasoningContent = m.reasoningContent,
            modelId = m.modelId,
            status = m.status.name,
            createdAt = m.createdAt,
            tokenCount = m.tokenCount,
            tokensPerSecond = m.tokensPerSecond,
            errorMessage = m.errorMessage
        )
    }
}

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val description: String?,
    val category: String,
    val isBuiltIn: Boolean,
    val createdAt: Long
) {
    fun toDomain(): Persona = Persona(
        id = id,
        name = name,
        emoji = emoji,
        systemPrompt = systemPrompt,
        description = description,
        category = category,
        isBuiltIn = isBuiltIn,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(p: Persona): PersonaEntity = PersonaEntity(
            id = p.id,
            name = p.name,
            emoji = p.emoji,
            systemPrompt = p.systemPrompt,
            description = p.description,
            category = p.category,
            isBuiltIn = p.isBuiltIn,
            createdAt = p.createdAt
        )
    }
}

@Entity(tableName = "saved_prompts")
data class SavedPromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val folder: String?,
    val createdAt: Long
) {
    fun toDomain(): SavedPrompt = SavedPrompt(
        id = id,
        title = title,
        content = content,
        folder = folder,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(p: SavedPrompt): SavedPromptEntity = SavedPromptEntity(
            id = p.id,
            title = p.title,
            content = p.content,
            folder = p.folder,
            createdAt = p.createdAt
        )
    }
}

@Entity(tableName = "offline_models")
data class OfflineModelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val modelId: String,
    val modelFile: String,
    val downloadUrl: String,
    val sizeInBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val filePath: String?,
    val supportedAccelerators: String,
    val selectedAccelerator: String,
    val isReasoningModel: Boolean,
    val description: String,
    val isImported: Boolean,
    val licenseUrl: String = "https://ai.google.dev/gemma/terms",
    val contextLength: String = "4096"
) {
    fun toDomain(): com.example.data.model.OfflineModel {
        val acceleratorsList = supportedAccelerators.split(",")
            .mapNotNull {
                try { com.example.data.model.Accelerator.valueOf(it.trim().uppercase()) } catch (e: Exception) { null }
            }.ifEmpty { listOf(com.example.data.model.Accelerator.NPU, com.example.data.model.Accelerator.GPU, com.example.data.model.Accelerator.CPU) }

        val selAcc = try {
            com.example.data.model.Accelerator.valueOf(selectedAccelerator.trim().uppercase())
        } catch (e: Exception) {
            acceleratorsList.firstOrNull() ?: com.example.data.model.Accelerator.NPU
        }

        val modelStatus = try {
            com.example.data.model.OfflineModelStatus.valueOf(status.trim().uppercase())
        } catch (e: Exception) {
            com.example.data.model.OfflineModelStatus.AVAILABLE
        }

        return com.example.data.model.OfflineModel(
            id = id,
            name = name,
            modelId = modelId,
            modelFile = modelFile,
            downloadUrl = downloadUrl,
            sizeInBytes = sizeInBytes,
            downloadedBytes = downloadedBytes,
            status = modelStatus,
            filePath = filePath,
            supportedAccelerators = acceleratorsList,
            selectedAccelerator = selAcc,
            isReasoningModel = isReasoningModel,
            description = description,
            isImported = isImported,
            licenseUrl = licenseUrl,
            contextLength = contextLength
        )
    }

    companion object {
        fun fromDomain(m: com.example.data.model.OfflineModel): OfflineModelEntity = OfflineModelEntity(
            id = m.id,
            name = m.name,
            modelId = m.modelId,
            modelFile = m.modelFile,
            downloadUrl = m.downloadUrl,
            sizeInBytes = m.sizeInBytes,
            downloadedBytes = m.downloadedBytes,
            status = m.status.name,
            filePath = m.filePath,
            supportedAccelerators = m.supportedAccelerators.joinToString(",") { it.name },
            selectedAccelerator = m.selectedAccelerator.name,
            isReasoningModel = m.isReasoningModel,
            description = m.description,
            isImported = m.isImported,
            licenseUrl = m.licenseUrl,
            contextLength = m.contextLength
        )
    }
}
