package com.example.data.model

enum class ServerType(val displayName: String, val defaultPort: Int, val isCloud: Boolean = false) {
    OLLAMA("Ollama", 11434),
    LM_STUDIO("LM Studio", 1234),
    OPENAI_COMPATIBLE("OpenAI-Compatible", 443),
    OPENROUTER("OpenRouter", 443, true)
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    CHECKING,
    ERROR
}

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    STREAMING,
    COMPLETE,
    ERROR
}

data class Server(
    val id: String,
    val name: String,
    val type: ServerType,
    val host: String,
    val port: Int,
    val apiKey: String? = null,
    val pathPrefix: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long = 0L,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED
) {
    val baseUrl: String
        get() = when (type) {
            ServerType.OPENROUTER -> "https://openrouter.ai/api/v1"
            ServerType.OLLAMA, ServerType.LM_STUDIO, ServerType.OPENAI_COMPATIBLE -> {
                val cleanHost = host.trim().removeSuffix("/")
                if (cleanHost.startsWith("http://") || cleanHost.startsWith("https://")) {
                    cleanHost
                } else {
                    val scheme = if (port == 443) "https" else "http"
                    if (port > 0) "$scheme://$cleanHost:$port" else "$scheme://$cleanHost"
                }
            }
        }

    val displayAddress: String
        get() = when (type) {
            ServerType.OPENROUTER -> "openrouter.ai"
            else -> {
                val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").removeSuffix("/")
                if (port > 0 && !cleanHost.contains(":")) "$cleanHost:$port" else cleanHost
            }
        }
}

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val personaId: String? = null,
    val serverId: String? = null,
    val modelId: String? = null,
    val messageCount: Int = 0,
    val lastMessagePreview: String? = null,
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 2048,
    val isTemporary: Boolean = false,
    val folder: String? = null
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoningContent: String? = null,
    val modelId: String? = null,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val createdAt: Long = System.currentTimeMillis(),
    val tokenCount: Int? = null,
    val tokensPerSecond: Double? = null,
    val errorMessage: String? = null
)

data class Persona(
    val id: String,
    val name: String,
    val emoji: String,
    val systemPrompt: String,
    val description: String? = null,
    val category: String = "General",
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedPrompt(
    val id: String,
    val title: String,
    val content: String,
    val folder: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val contextLength: Int? = null,
    val serverId: String? = null,
    val isReasoningModel: Boolean = false,
    val isOffline: Boolean = false,
    val accelerator: Accelerator? = null
)

enum class Accelerator(val label: String, val displayName: String) {
    NPU("NPU", "NPU (Neural Engine)"),
    GPU("GPU", "GPU (Vulkan / OpenCL)"),
    CPU("CPU", "CPU (Multi-Thread)")
}

enum class OfflineModelStatus {
    AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class OfflineModel(
    val id: String,
    val name: String,
    val modelId: String,
    val modelFile: String,
    val downloadUrl: String,
    val sizeInBytes: Long,
    val downloadedBytes: Long = 0L,
    val status: OfflineModelStatus = OfflineModelStatus.AVAILABLE,
    val filePath: String? = null,
    val supportedAccelerators: List<Accelerator> = listOf(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU),
    val selectedAccelerator: Accelerator = Accelerator.NPU,
    val isReasoningModel: Boolean = false,
    val description: String = "",
    val isImported: Boolean = false,
    val downloadSpeed: String? = null,
    val remainingTime: String? = null,
    val lastError: String? = null,
    val licenseUrl: String = "https://ai.google.dev/gemma/terms",
    val contextLength: String = "4096"
) {
    val progress: Float
        get() = if (sizeInBytes > 0) (downloadedBytes.toFloat() / sizeInBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSize: String
        get() = when {
            sizeInBytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", sizeInBytes.toDouble() / (1024 * 1024 * 1024))
            sizeInBytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", sizeInBytes.toDouble() / (1024 * 1024))
            else -> String.format(java.util.Locale.US, "%d KB", sizeInBytes / 1024)
        }
}
