package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Persona
import com.example.data.model.SavedPrompt
import com.example.data.model.Server
import com.example.data.model.ServerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ServerEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        PersonaEntity::class,
        SavedPromptEntity::class,
        OfflineModelEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ResoDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun savedPromptDao(): SavedPromptDao
    abstract fun offlineModelDao(): OfflineModelDao

    companion object {
        @Volatile
        private var INSTANCE: ResoDatabase? = null

        fun getDatabase(context: Context): ResoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ResoDatabase::class.java,
                    "reso_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaults(database)
                    }
                }
            }
        }

        suspend fun populateDefaults(database: ResoDatabase) {
            // Default servers
            val defaultServers = listOf(
                ServerEntity.fromDomain(
                    Server(
                        id = "default-ollama",
                        name = "Local Ollama",
                        type = ServerType.OLLAMA,
                        host = "10.0.2.2",
                        port = 11434,
                        isDefault = true
                    )
                ),
                ServerEntity.fromDomain(
                    Server(
                        id = "default-lmstudio",
                        name = "LM Studio",
                        type = ServerType.LM_STUDIO,
                        host = "10.0.2.2",
                        port = 1234,
                        isDefault = false
                    )
                ),
                ServerEntity.fromDomain(
                    Server(
                        id = "default-openrouter",
                        name = "OpenRouter",
                        type = ServerType.OPENROUTER,
                        host = "openrouter.ai",
                        port = 443,
                        isDefault = false
                    )
                )
            )
            database.serverDao().insertServers(defaultServers)

            // Builtin personas
            val defaultPersonas = listOf(
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-general",
                        name = "General Assistant",
                        emoji = "🤖",
                        systemPrompt = "You are a helpful, knowledgeable AI assistant. Provide clear, accurate, and concise responses. Use markdown formatting for structured responses.",
                        description = "Helpful, knowledgeable assistant",
                        category = "General",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-code",
                        name = "Code Assistant",
                        emoji = "🧑‍💻",
                        systemPrompt = "You are an expert software engineer. Help with coding questions, debugging, code reviews, and architecture decisions. Always provide code examples with proper syntax highlighting. Explain your reasoning.",
                        description = "Expert software engineer",
                        category = "Coding",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-math",
                        name = "Math Tutor",
                        emoji = "📐",
                        systemPrompt = "You are a patient and thorough math tutor. Explain concepts step by step, starting from fundamentals. Show your work clearly.",
                        description = "Patient, step-by-step math tutor",
                        category = "Education",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-story",
                        name = "Story Writer",
                        emoji = "✍️",
                        systemPrompt = "You are a creative fiction writer. Help craft engaging stories, develop characters, build worlds, and write dialogue.",
                        description = "Creative fiction writer",
                        category = "Creative",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-tutor",
                        name = "General Tutor",
                        emoji = "📚",
                        systemPrompt = "You are an educational tutor skilled in all subjects. Explain complex topics in simple terms. Use analogies, examples, and visual descriptions.",
                        description = "Skilled tutor for all subjects",
                        category = "Education",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-editor",
                        name = "Writing Editor",
                        emoji = "✏️",
                        systemPrompt = "You are a professional writing editor. Help improve text clarity, grammar, style, and structure. Provide specific suggestions with explanations.",
                        description = "Professional writing editor",
                        category = "Creative",
                        isBuiltIn = true
                    )
                ),
                PersonaEntity.fromDomain(
                    Persona(
                        id = "builtin-summarizer",
                        name = "Summarizer",
                        emoji = "📋",
                        systemPrompt = "You are a concise summarizer. When given text, provide clear, accurate summaries that capture the key points using bullet points.",
                        description = "Concise, accurate summarizer",
                        category = "General",
                        isBuiltIn = true
                    )
                )
            )
            database.personaDao().insertPersonas(defaultPersonas)

            // Saved Prompts
            val defaultPrompts = listOf(
                SavedPromptEntity.fromDomain(
                    SavedPrompt(
                        id = "prompt-summarize",
                        title = "Summarize Key Points",
                        content = "Please summarize the main points of the following text with concise bullet points and actionable takeaways:\n\n",
                        folder = "Productivity"
                    )
                ),
                SavedPromptEntity.fromDomain(
                    SavedPrompt(
                        id = "prompt-code-review",
                        title = "Code Review",
                        content = "Review the following code for readability, edge cases, security, and performance optimizations:\n\n",
                        folder = "Development"
                    )
                ),
                SavedPromptEntity.fromDomain(
                    SavedPrompt(
                        id = "prompt-eli5",
                        title = "Explain Like I'm 5",
                        content = "Explain the following concept in simple terms, using an everyday analogy that anyone can understand:\n\n",
                        folder = "Learning"
                    )
                ),
                SavedPromptEntity.fromDomain(
                    SavedPrompt(
                        id = "prompt-email",
                        title = "Draft Professional Email",
                        content = "Write a clear, concise, and professional email based on the following key points:\n\n",
                        folder = "Productivity"
                    )
                )
            )
            database.savedPromptDao().insertPrompts(defaultPrompts)

            // Offline AI Models (Google AI Edge Gallery 11 Curated Models)
            database.offlineModelDao().insertDefaultModels(getDefaultOfflineModels())
        }

        fun getDefaultOfflineModels(): List<OfflineModelEntity> = listOf(
            OfflineModelEntity(
                id = "gemma-4-e2b-it",
                name = "Gemma-4-E2B-it",
                modelId = "litert-community/gemma-4-E2B-it-litert-lm",
                modelFile = "gemma-4-E2B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm?download=true",
                sizeInBytes = 2588147712L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "A variant of Gemma 4 E2B ready for deployment on Android using LiteRT-LM. It supports multi-modality input, with up to 32K context length.",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "32K"
            ),
            OfflineModelEntity(
                id = "gemma-4-e4b-it",
                name = "Gemma-4-E4B-it",
                modelId = "litert-community/gemma-4-E4B-it-litert-lm",
                modelFile = "gemma-4-E4B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm?download=true",
                sizeInBytes = 3739943936L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "A variant of Gemma 4 E4B ready for deployment on Android using LiteRT-LM. It supports multi-modality input, with up to 32K context length.",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "32K"
            ),
            OfflineModelEntity(
                id = "qwen2.5-1.5b-instruct",
                name = "Qwen2.5-1.5B-Instruct",
                modelId = "Qwen/Qwen2.5-1.5B-Instruct",
                modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                sizeInBytes = 1686705152L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "A variant of Qwen/Qwen2.5-1.5B-Instruct ready for deployment on Android using LiteRT-LM. Highly efficient 8-bit quantized on-device instruction model.",
                isImported = false,
                licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct",
                contextLength = "4096"
            ),
            OfflineModelEntity(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek-R1-Distill-Qwen-1.5B",
                modelId = "deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
                modelFile = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/e34bb88632342d1f9640bad579a45134eb1cf988/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                sizeInBytes = 1805908992L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = true,
                description = "DeepSeek-R1 distilled reasoning model running on-device with LiteRT-LM. Outputs internal thinking process before generating answers.",
                isImported = false,
                licenseUrl = "https://huggingface.co/deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B",
                contextLength = "4096"
            ),
            OfflineModelEntity(
                id = "qwen2.5-0.5b-instruct",
                name = "Qwen2.5-0.5B-Instruct",
                modelId = "Qwen/Qwen2.5-0.5B-Instruct",
                modelFile = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
                sizeInBytes = 546660344L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "Ultra-compact Qwen 2.5 0.5B model (~520MB) designed for lightning-fast on-device text generation with minimal memory footprint.",
                isImported = false,
                licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct",
                contextLength = "4096"
            ),
            OfflineModelEntity(
                id = "magic-touch",
                name = "Magic touch",
                modelId = "google/magic-touch-segmentation",
                modelFile = "interactive_segmentation.task",
                downloadUrl = "https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/latest/interactive_segmentation.task",
                sizeInBytes = 30500000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "GPU",
                isReasoningModel = false,
                description = "Fast interactive visual segmenter from MediaPipe. Identifies segments given image coordinates for an area of interest using a MobileNetV3 architecture.",
                isImported = false,
                licenseUrl = "https://developers.google.com/mediapipe",
                contextLength = "N/A"
            ),
            OfflineModelEntity(
                id = "gemma-3n-e2b-it",
                name = "Gemma-3n-E2B-it",
                modelId = "google/gemma-3n-E2B-it-litert-lm",
                modelFile = "gemma-3n-E2B-it-int4.litertlm",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/ba9ca88da013b537b6ed38108be609b8db1c3a16/gemma-3n-E2B-it-int4.litertlm?download=true",
                sizeInBytes = 2700000000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "Official Google Gemma 3n E2B model compiled for LiteRT-LM. Supports text, vision, and audio input with 4096 context length. (Requires HF Access Token)",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "4096"
            ),
            OfflineModelEntity(
                id = "gemma-3n-e4b-it",
                name = "Gemma-3n-E4B-it",
                modelId = "google/gemma-3n-E4B-it-litert-lm",
                modelFile = "gemma-3n-E4B-it-int4.litertlm",
                downloadUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/d122cf233306869b32943265a6e2e2ecf9a562ca/gemma-3n-E4B-it-int4.litertlm?download=true",
                sizeInBytes = 3700000000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "Official Google Gemma 3n E4B model compiled for LiteRT-LM. High performance vision-language understanding. (Requires HF Access Token)",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "4096"
            ),
            OfflineModelEntity(
                id = "gemma3-1b-it",
                name = "Gemma3-1B-IT",
                modelId = "litert-community/Gemma3-1B-IT",
                modelFile = "gemma3-1b-it-int4.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
                sizeInBytes = 584400000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "NPU,GPU,CPU",
                selectedAccelerator = "NPU",
                isReasoningModel = false,
                description = "4-bit quantized Gemma-3-1B-IT model ready for deployment on Android using LiteRT-LM. (Requires HF Access Token)",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "8192"
            ),
            OfflineModelEntity(
                id = "tinygarden-270m",
                name = "TinyGarden-270M",
                modelId = "google/functiongemma-270m-ft-tiny-garden",
                modelFile = "tiny_garden_q8_ekv1024.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/resolve/c205853ff82da86141a1105faa2344a8b176dfe7/tiny_garden_q8_ekv1024.litertlm?download=true",
                sizeInBytes = 289000000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "CPU,GPU,NPU",
                selectedAccelerator = "CPU",
                isReasoningModel = false,
                description = "Fine-tuned Function Gemma 270M model for smart garden control and automation actions.",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "2048"
            ),
            OfflineModelEntity(
                id = "mobileactions-270m",
                name = "MobileActions-270M",
                modelId = "google/functiongemma-270m-ft-mobile-actions",
                modelFile = "mobile_actions_q8_ekv1024.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/38942192c9b723af836d489074823ff33d4a3e7a/mobile_actions_q8_ekv1024.litertlm?download=true",
                sizeInBytes = 289000000L,
                downloadedBytes = 0L,
                status = "AVAILABLE",
                filePath = null,
                supportedAccelerators = "CPU,GPU,NPU",
                selectedAccelerator = "CPU",
                isReasoningModel = false,
                description = "Fine-tuned Function Gemma 270M model for mobile OS tool calling and action execution.",
                isImported = false,
                licenseUrl = "https://ai.google.dev/gemma/terms",
                contextLength = "2048"
            )
        )
    }
}
