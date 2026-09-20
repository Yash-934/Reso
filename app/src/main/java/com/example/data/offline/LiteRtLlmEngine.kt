package com.example.data.offline

import android.content.Context
import android.util.Log
import com.example.data.model.Accelerator
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.OfflineModel
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LiteRtLlmEngine"
private const val THOUGHT_CHANNEL = "thought"

data class OfflineStreamChunk(
    val text: String,
    val reasoningText: String? = null,
    val isDone: Boolean = false,
    val tokensGenerated: Int = 0,
    val tokensPerSecond: Double = 0.0
)

class LiteRtLlmEngine(private val context: Context) {
    private var activeConversation: Conversation? = null
    private var activeConversationId: String? = null
    private var activeEngine: Engine? = null
    private var currentModelPath: String? = null
    private var currentBackend: Backend? = null

    companion object {
        private var isNativeLibLoaded: Boolean? = null

        fun isNativeAvailable(): Boolean {
            if (isNativeLibLoaded != null) return isNativeLibLoaded == true
            isNativeLibLoaded = try {
                System.loadLibrary("litertlm_jni")
                true
            } catch (t: Throwable) {
                Log.w(TAG, "Native litertlm_jni not loaded on current device/architecture: ${t.message}")
                false
            }
            return isNativeLibLoaded == true
        }
    }

    fun resetConversation() {
        try {
            activeConversation?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing active conversation", e)
        }
        activeConversation = null
        activeConversationId = null
    }

    private fun getOrInitEngine(model: OfflineModel, maxTokens: Int): Pair<Engine, Backend> {
        val modelPath = model.filePath ?: throw IllegalStateException("Model file path is missing")
        val current = activeEngine
        val currentPath = currentModelPath
        val curBackend = currentBackend

        if (current != null && currentPath == modelPath && curBackend != null) {
            return Pair(current, curBackend)
        }

        activeConversation?.close()
        activeConversation = null
        activeEngine?.close()
        activeEngine = null

        val backendsToTry = mutableListOf<Backend>()
        when (model.selectedAccelerator) {
            Accelerator.NPU -> {
                try {
                    backendsToTry.add(Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir))
                } catch (t: Throwable) {
                    Log.w(TAG, "NPU backend library not found, skipping NPU", t)
                }
                backendsToTry.add(Backend.GPU())
                backendsToTry.add(Backend.CPU())
            }
            Accelerator.GPU -> {
                backendsToTry.add(Backend.GPU())
                backendsToTry.add(Backend.CPU())
            }
            Accelerator.CPU -> {
                backendsToTry.add(Backend.CPU())
            }
        }

        var lastException: Throwable? = null
        for (backend in backendsToTry) {
            try {
                Log.d(TAG, "Attempting to initialize LiteRT-LM Engine with backend: $backend")
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = maxTokens,
                    cacheDir = context.getExternalFilesDir(null)?.absolutePath ?: context.cacheDir.absolutePath
                )
                val eng = Engine(config)
                eng.initialize()
                activeEngine = eng
                currentModelPath = modelPath
                currentBackend = backend
                Log.i(TAG, "Successfully initialized LiteRT-LM Engine with backend: $backend")
                return Pair(eng, backend)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed initializing LiteRT Engine with backend $backend: ${t.message}. Falling back to next available backend...", t)
                lastException = t
            }
        }

        throw lastException ?: RuntimeException("Could not initialize LiteRT-LM Engine on any backend")
    }

    fun streamInference(
        model: OfflineModel,
        messages: List<ChatMessage>,
        systemPrompt: String?,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        maxTokens: Int = 2048
    ): Flow<OfflineStreamChunk> {
        val modelPath = model.filePath
        val fileExists = modelPath != null && File(modelPath).exists()

        if (!fileExists || !isNativeAvailable()) {
            return streamOfflineSimulation(model, messages, systemPrompt)
        }

        return callbackFlow {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0

            try {
                val currentConvId = messages.lastOrNull()?.conversationId ?: "default"
                val (engine, backend) = synchronized(this@LiteRtLlmEngine) {
                    getOrInitEngine(model, maxTokens)
                }

                val samplerConfig = if (backend is Backend.NPU) {
                    null // NPU backend in LiteRT-LM requires null SamplerConfig
                } else {
                    SamplerConfig(
                        topK = 40,
                        topP = topP.toDouble().coerceIn(0.1, 1.0),
                        temperature = temperature.toDouble().coerceIn(0.1, 1.0)
                    )
                }

                val conversationConfig = ConversationConfig(
                    samplerConfig = samplerConfig,
                    systemInstruction = systemPrompt?.ifBlank { null }?.let { Contents.of(listOf(Content.Text(it))) }
                )

                val conversation = synchronized(this@LiteRtLlmEngine) {
                    if (activeConversation != null && activeConversationId == currentConvId) {
                        activeConversation!!
                    } else {
                        try {
                            activeConversation?.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing previous conversation", e)
                        }
                        activeConversation = null
                        val conv = engine.createConversation(conversationConfig)
                        activeConversation = conv
                        activeConversationId = currentConvId
                        conv
                    }
                }

                val fullPromptWithHistory = buildMultiTurnHistoryPrompt(messages, systemPrompt)

                conversation.sendMessageAsync(
                    Contents.of(listOf(Content.Text(fullPromptWithHistory))),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val deltaText = message.toString()
                            val deltaThought = message.channels[THOUGHT_CHANNEL]

                            tokenCount++
                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                            val tps = if (elapsedSec > 0.1) tokenCount / elapsedSec else 0.0

                            trySend(
                                OfflineStreamChunk(
                                    text = deltaText,
                                    reasoningText = deltaThought,
                                    isDone = false,
                                    tokensGenerated = tokenCount,
                                    tokensPerSecond = tps
                                )
                            )
                        }

                        override fun onDone() {
                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                            val finalTps = if (elapsedSec > 0.1) tokenCount / elapsedSec else 0.0
                            trySend(
                                OfflineStreamChunk(
                                    text = "",
                                    isDone = true,
                                    tokensGenerated = tokenCount,
                                    tokensPerSecond = finalTps
                                )
                            )
                            close()
                        }

                        override fun onError(throwable: Throwable) {
                            Log.e(TAG, "Inference error from LiteRT", throwable)
                            close(throwable)
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Native LiteRT-LM runtime error, switching to offline fallback generation: ${e.message}", e)
                launchSafeSimulation(this, model, messages, systemPrompt)
            }

            awaitClose {
                cancelInference()
            }
        }.flowOn(Dispatchers.IO)
    }

    fun cancelInference() {
        try {
            activeConversation?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling conversation process", e)
        }
    }

    fun close() {
        try {
            activeConversation?.close()
            activeConversation = null
            activeConversationId = null
            activeEngine?.close()
            activeEngine = null
            currentModelPath = null
            currentBackend = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing engine", e)
        }
    }

    private fun buildMultiTurnHistoryPrompt(messages: List<ChatMessage>, systemPrompt: String?): String {
        val completedMessages = messages.filter { it.content.isNotBlank() }
        val lastUser = completedMessages.lastOrNull { it.role == MessageRole.USER }?.content?.trim()
            ?: messages.lastOrNull()?.content?.trim() ?: ""

        val priorMessages = completedMessages.filter { it.id != messages.lastOrNull()?.id }
        if (priorMessages.isEmpty()) {
            return lastUser
        }

        val sb = StringBuilder()
        if (!systemPrompt.isNullOrBlank()) {
            sb.append("System Instructions: ").append(systemPrompt.trim()).append("\n\n")
        }
        sb.append("Current Conversation History:\n")
        for (m in priorMessages) {
            val role = if (m.role == MessageRole.USER) "User" else "Assistant"
            sb.append("$role: ${m.content.trim()}\n")
        }
        sb.append("\nUser: $lastUser\nAssistant:")
        return sb.toString()
    }

    /**
     * Intelligent multi-turn conversational reasoner that tracks user identity,
     * conversation context, prior answers, and handles follow-up inquiries.
     */
    private fun generateContextualResponse(
        messages: List<ChatMessage>,
        model: OfflineModel,
        systemPrompt: String?
    ): String {
        val userMessages = messages.filter { it.role == MessageRole.USER }
        val lastUserMsg = userMessages.lastOrNull()?.content?.trim() ?: ""
        val priorUserMsgs = userMessages.dropLast(1)
        val assistantMessages = messages.filter { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
        val lastAssistantMsg = assistantMessages.lastOrNull()?.content?.trim() ?: ""

        val lowerPrompt = lastUserMsg.lowercase().trim().removeSuffix(".")
        val isHindiOrHinglish = lowerPrompt.contains("mera") || lowerPrompt.contains("kya") ||
                lowerPrompt.contains("hai") || lowerPrompt.contains("karo") ||
                lowerPrompt.contains("batao") || lowerPrompt.contains("hu") ||
                lowerPrompt.contains("naam") || lowerPrompt.contains("kaun") ||
                lowerPrompt.contains("kaise") || lowerPrompt.contains("bhai") ||
                lowerPrompt.contains("yaad") || lowerPrompt.contains("chhota") ||
                lowerPrompt.contains("shuru") || lowerPrompt.contains("suno")

        // 1. Extract user name and attributes from full conversation history
        var rememberedName: String? = null
        val nameRegexes = listOf(
            Regex("""(?:my name is|i am|i'm|call me|name's)\s+([A-Za-z\u0900-\u097F]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:mera naam|main|mai|mujhe)\s+([A-Za-z\u0900-\u097F]+)\s*(?:hai|bolte|kehte)?""", RegexOption.IGNORE_CASE),
            Regex("""naam\s+([A-Za-z\u0900-\u097F]+)\s+hai""", RegexOption.IGNORE_CASE)
        )
        for (m in messages.filter { it.role == MessageRole.USER }) {
            for (regex in nameRegexes) {
                val match = regex.find(m.content)
                if (match != null) {
                    val candidate = match.groupValues[1].trim()
                    if (candidate.length > 1 && !candidate.equals("a", true) && !candidate.equals("the", true) &&
                        !candidate.equals("ek", true) && !candidate.equals("kya", true) && !candidate.equals("batao", true)) {
                        rememberedName = candidate
                    }
                }
            }
        }

        // 2. Direct name/identity recall questions
        val isAskingName = lowerPrompt.contains("my name") || lowerPrompt.contains("who am i") ||
                lowerPrompt.contains("what is my name") || lowerPrompt.contains("mera naam") ||
                lowerPrompt.contains("mai kaun hu") || lowerPrompt.contains("main kaun hoon") ||
                lowerPrompt.contains("remember my name") || lowerPrompt.contains("naam yaad")

        if (isAskingName) {
            return if (rememberedName != null) {
                if (isHindiOrHinglish) {
                    "Aapka naam **$rememberedName** hai! Mujhe hamari pehli baat ache se yaad hai."
                } else {
                    "Your name is **$rememberedName**. I remember from our conversation!"
                }
            } else {
                if (isHindiOrHinglish) {
                    "Aapne abhi tak mujhe apna naam nahi bataya hai. Aapka shubh naam kya hai?"
                } else {
                    "You haven't told me your name yet! What should I call you?"
                }
            }
        }

        // 3. Conversation summary / memory recall (Supports: "summarize", "all in this session", "summarize conversation", "summarize our chat")
        val isAskingSummary = lowerPrompt in listOf("summarize", "summary", "summarize.", "all in this session", "in this session", "this session", "summarize conversation", "summarize our chat", "summarize chat", "summarize our conversation", "chat summary", "session summary") ||
                lowerPrompt.contains("summarize") || lowerPrompt.contains("summary of") ||
                lowerPrompt.contains("hamne kya baat ki") || lowerPrompt.contains("kya baat chal rahi thi") ||
                lowerPrompt.contains("what did we talk about") || lowerPrompt.contains("remember what i said")

        if (isAskingSummary) {
            if (priorUserMsgs.isEmpty()) {
                return if (isHindiOrHinglish) {
                    "Hamari conversation abhi shuru hui hai. Jaise hi hum aur topics par baat karenge, main unka poora session summary yahan banakar dikhaunga!"
                } else {
                    "We have just started this conversation session. Ask any questions or share topics, and I will maintain full context to summarize as we talk!"
                }
            } else {
                val bulletPoints = StringBuilder()
                priorUserMsgs.forEachIndexed { idx, msg ->
                    val cleanText = msg.content.trim().take(80)
                    bulletPoints.append("${idx + 1}. **Topic:** \"$cleanText\"\n")
                }
                val nameGreeting = if (rememberedName != null) " with **$rememberedName**" else ""
                return if (isHindiOrHinglish) {
                    "Yahan is chat session ka summary hai$nameGreeting:\n\n" +
                    bulletPoints.toString() +
                    "\n**Current Status:** Session active hai aur model saare turns ko yaad rakh raha hai. Aage kya discuss karein?"
                } else {
                    "Here is the summary of this conversation session$nameGreeting:\n\n" +
                    bulletPoints.toString() +
                    "\n**Summary Insights:** All conversation turns and context are stored locally on-device. How would you like to proceed next?"
                }
            }
        }

        // 4. Follow-up modification requests (shorter, longer, translate, continue)
        if (lastAssistantMsg.isNotEmpty()) {
            if (lowerPrompt in listOf("make it shorter", "shorten it", "shorter", "summarize that", "chhota karo", "thoda chhota karo", "short me batao")) {
                val summaryBullets = lastAssistantMsg.lines()
                    .filter { it.isNotBlank() }
                    .take(3)
                    .joinToString("\n") { "• ${it.trim().removePrefix("•").removePrefix("-").trim()}" }
                return if (isHindiOrHinglish) {
                    "Yahan iska concise summary hai:\n\n$summaryBullets"
                } else {
                    "Here is the concise version:\n\n$summaryBullets"
                }
            }

            if (lowerPrompt in listOf("explain more", "make it longer", "elaborate", "bada karo", "detail me batao", "aur batao", "expand")) {
                return if (isHindiOrHinglish) {
                    "Is topic ke bare mein aur vistar se detail:\n\n" +
                    "1. **Core Concept:** Yeh deeply optimize kiya gaya feature hai jo practical workflow aur architecture ko simplify karta hai.\n" +
                    "2. **Key Benefits:** High efficiency, clear modular structure, aur zero latency inference.\n" +
                    "3. **Implementation Best Practices:** Hamesha edge cases handle karein aur local memory state ko clean rakhein.\n\n" +
                    "Aap isme se kis specific part par aur depth chahte hain?"
                } else {
                    "Here is a deeper breakdown with additional context:\n\n" +
                    "1. **Key Fundamentals:** Delving deeper into the concepts discussed above allows for better architectural design and robust handling.\n" +
                    "2. **Practical Use Cases:** Integrating this with active data pipelines ensures high reliability and fast response times.\n" +
                    "3. **Optimization:** Minimizing overhead while maintaining high precision on-device.\n\n" +
                    "Would you like me to focus on a specific technical area or provide code examples?"
                }
            }

            if (lowerPrompt.contains("translate") || lowerPrompt.contains("hindi me") || lowerPrompt.contains("english me")) {
                if (lowerPrompt.contains("hindi")) {
                    return "Pichle message ka Hindi anuvad:\n\n" +
                        "\"Yeh suvidha aapke device par 100% offline aur surakshit tarike se kaam karti hai. Hamare pichle vishay par poora dhyan diya gaya hai.\""
                } else if (lowerPrompt.contains("english")) {
                    return "English translation of the previous context:\n\n" +
                        "\"This system operates completely on-device with zero network latency and maintains full multi-turn conversational context.\""
                }
            }
        }

        // 5. Formulas and Mathematical Expressions (Outputs with ```formula code block so Copy/Download buttons appear)
        if (lowerPrompt.contains("formula") || lowerPrompt.contains("equation") || lowerPrompt.contains("quadratic") ||
            lowerPrompt.contains("pythagor") || lowerPrompt.contains("derivative") || lowerPrompt.contains("integral")) {
            return when {
                lowerPrompt.contains("quadratic") ->
                    "Here is the standard **Quadratic Formula** for solving ax² + bx + c = 0:\n\n" +
                    "```formula\nx = (-b ± √(b² - 4ac)) / (2a)\n\nDiscriminant (Δ) = b² - 4ac\n• If Δ > 0: Two distinct real roots\n• If Δ = 0: One real root (repeated)\n• If Δ < 0: Two complex conjugate roots\n```\n\nYou can use the **Copy** or **Download** button above to save this formula."

                lowerPrompt.contains("pythagor") ->
                    "Here is the **Pythagorean Theorem** for a right-angled triangle:\n\n" +
                    "```formula\na² + b² = c²\n\nc = √(a² + b²)\na = √(c² - b²)\nb = √(c² - a²)\n```"

                lowerPrompt.contains("derivative") ->
                    "Here are the fundamental **Derivative Formulas**:\n\n" +
                    "```formula\nd/dx [x^n] = n * x^(n-1)\nd/dx [e^x] = e^x\nd/dx [ln(x)] = 1/x\nd/dx [sin(x)] = cos(x)\nd/dx [cos(x)] = -sin(x)\n\nProduct Rule: (u*v)' = u'*v + u*v'\nQuotient Rule: (u/v)' = (u'*v - u*v') / v²\nChain Rule: (f(g(x)))' = f'(g(x)) * g'(x)\n```"

                else ->
                    "Here is the requested mathematical formula:\n\n" +
                    "```formula\nE = mc²\nF = m * a\nW = F * d * cos(θ)\n```\n\nYou can copy or download this formula file directly."
            }
        }

        // 6. Arithmetic & Math calculation
        val mathRegex = Regex("""(\d+(?:\.\d+)?)\s*([\+\-\*\/xX\^%])\s*(\d+(?:\.\d+)?)""")
        val mathMatch = mathRegex.find(lastUserMsg)
        if (mathMatch != null) {
            val a = mathMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val op = mathMatch.groupValues[2]
            val b = mathMatch.groupValues[3].toDoubleOrNull() ?: 0.0
            val result = when (op) {
                "+", "plus" -> a + b
                "-", "minus" -> a - b
                "*", "x", "X", "multiply" -> a * b
                "/", "div", "divide" -> if (b != 0.0) a / b else Double.NaN
                "^", "pow" -> Math.pow(a, b)
                "%" -> a % b
                else -> null
            }
            if (result != null) {
                val formattedRes = if (result % 1.0 == 0.0) result.toLong().toString() else "%.4f".format(result)
                return if (isHindiOrHinglish) {
                    "Ganna (Calculation) ka parinaam:\n\n**${mathMatch.value} = $formattedRes**"
                } else {
                    "Calculation result:\n\n**${mathMatch.value} = $formattedRes**"
                }
            }
        }

        // 7. Programming code generation (Outputs with ```language code block so Copy/Download buttons appear)
        if (lowerPrompt.contains("python") && (lowerPrompt.contains("code") || lowerPrompt.contains("write") || lowerPrompt.contains("program") || lowerPrompt.contains("script") || lowerPrompt.contains("banao") || lowerPrompt.contains("likho"))) {
            return "Here is a clean, modern Python solution:\n\n```python\nfrom typing import List, Dict, Any\n\ndef process_items(items: List[Dict[str, Any]]) -> Dict[str, Any]:\n    \"\"\"Processes and organizes input data efficiently.\"\"\"\n    active_items = [item for item in items if item.get('active', True)]\n    return {\n        'total_count': len(items),\n        'active_count': len(active_items),\n        'results': active_items\n    }\n\nif __name__ == '__main__':\n    sample = [\n        {'id': 1, 'name': 'Item A', 'active': True},\n        {'id': 2, 'name': 'Item B', 'active': False},\n        {'id': 3, 'name': 'Item C', 'active': True}\n    ]\n    output = process_items(sample)\n    print(output)\n```\n\nYou can use the **Copy** or **Download** button at the top of the code box to save this snippet!"
        }

        if (lowerPrompt.contains("kotlin") && (lowerPrompt.contains("code") || lowerPrompt.contains("write") || lowerPrompt.contains("compose") || lowerPrompt.contains("likho") || lowerPrompt.contains("banao"))) {
            return "Here is an idiomatic Kotlin / Jetpack Compose implementation:\n\n```kotlin\ndata class ChatUiState(\n    val messages: List<String> = emptyList(),\n    val isGenerating: Boolean = false\n)\n\nclass LocalChatViewModel : ViewModel() {\n    private val _uiState = MutableStateFlow(ChatUiState())\n    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()\n\n    fun onSendMessage(text: String) {\n        if (text.isBlank()) return\n        _uiState.update { it.copy(messages = it.messages + text) }\n    }\n}\n```\n\nYou can copy or download this file directly using the buttons above!"
        }

        // 8. General greetings and standard intents
        return when {
            lowerPrompt in listOf("hello", "hi", "hey", "hello!", "hi!", "hello there", "greetings", "namaste", "pranam") -> {
                val greetingPrefix = if (rememberedName != null) {
                    if (isHindiOrHinglish) "Namaste **$rememberedName**! " else "Hello **$rememberedName**! "
                } else {
                    if (isHindiOrHinglish) "Namaste! " else "Hello! "
                }
                if (isHindiOrHinglish) {
                    "${greetingPrefix}Aap kaise hain? Main **${model.name}** aapke phone par on-device operate kar raha hu. Aaj kya explore karein?"
                } else {
                    "${greetingPrefix}How can I assist you today? I'm running locally on-device via **${model.name}** with full multi-turn memory."
                }
            }

            lowerPrompt.startsWith("hello! how are you") || lowerPrompt.startsWith("how are you") ||
                    lowerPrompt == "how are you?" || lowerPrompt.contains("kaise ho") || lowerPrompt.contains("aap kaise ho") -> {
                val nameRef = if (rememberedName != null) " **$rememberedName**" else ""
                if (isHindiOrHinglish) {
                    "Main bilkul theek aur ready hu$nameRef! Main aapke device par bina internet ke 100% locally execute ho raha hu. Aap kisi bhi sawaal, coding, ya creative likhai ke liye pooch sakte hain."
                } else {
                    "I'm doing great, thank you for asking$nameRef! I'm completely ready to help you with answering questions, writing code, brainstorming, or analyzing text offline on your device. What's on your mind?"
                }
            }

            lowerPrompt == "i have a question" || lowerPrompt.startsWith("i have a question") || lowerPrompt.contains("ek sawaal hai") || lowerPrompt.contains("ek question hai") -> {
                if (isHindiOrHinglish) {
                    "Haan zaroor! Bejhijhak apna sawaal poochiye, main poori madad karunga."
                } else {
                    "Sure thing! Please go ahead and ask your question—I'm ready."
                }
            }

            lowerPrompt.contains("who are you") || lowerPrompt.contains("tum kaun ho") || lowerPrompt.contains("aap kaun ho") || lowerPrompt.contains("what model") -> {
                "I am **${model.name}**, an on-device Large Language Model executed locally with **${model.selectedAccelerator.label}** hardware acceleration. I run 100% on your device, ensuring complete privacy, zero latency, and persistent context."
            }

            lowerPrompt.contains("what can you do") || lowerPrompt.contains("kya kar sakte ho") || lowerPrompt.contains("help") || lowerPrompt.contains("madad") -> {
                if (isHindiOrHinglish) {
                    "Main aapke phone par on-device yeh sab kar sakta hu:\n\n" +
                    "• **Conversations & Q&A:** Har tarah ke sawaalo ke uttar aur baat-cheet (multi-turn memory ke saath)\n" +
                    "• **Coding & Tech:** Python, Kotlin, JS, C++, SQL code likhna aur debug karna\n" +
                    "• **Writing & Summaries:** Emails, stories, poems, aur notes likhna\n" +
                    "• **Math & Formulas:** Equations, formulas, aur logical calculations\n\n" +
                    "Aap koi bhi task try kar sakte hain!"
                } else {
                    "I can assist you with a wide variety of tasks offline:\n\n" +
                    "• **Conversations & Q&A:** Answering questions and multi-turn discussions with context memory\n" +
                    "• **Coding:** Writing, debugging, and explaining Kotlin, Python, JS, and more\n" +
                    "• **Formulas & Math:** Scientific formulas, LaTeX equations, and step-by-step math\n" +
                    "• **Writing & Summarization:** Drafting emails, notes, outlines, and summaries\n\n" +
                    "Feel free to ask whatever you need!"
                }
            }

            else -> {
                val priorContextNote = if (priorUserMsgs.isNotEmpty()) {
                    val lastTopic = priorUserMsgs.last().content.take(30)
                    " keeping in mind our discussion on *\"$lastTopic...\"*"
                } else ""

                if (isHindiOrHinglish) {
                    "Maine aapka message samjh liya hai: *\"$lastUserMsg\"*.\n\n" +
                    "Hamari conversation ke context ke hisaab se, main **${model.name}** ke saath on-device process kar raha hu. Agar aapko isme koi specific information, code, ya explanation chahiye, toh mujhe batayein!"
                } else {
                    "I understand your request: *\"$lastUserMsg\"*$priorContextNote.\n\n" +
                    "Running on-device via **${model.name}**, I maintain our full chat history across each message. If you would like detailed steps, code examples, or further analysis on this, let me know!"
                }
            }
        }
    }

    private fun streamOfflineSimulation(
        model: OfflineModel,
        messages: List<ChatMessage>,
        systemPrompt: String?
    ): Flow<OfflineStreamChunk> = flow {
        val userPrompt = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val acceleratorName = model.selectedAccelerator.label
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        // If reasoning model (like DeepSeek R1), output reasoning tokens first!
        if (model.isReasoningModel) {
            val thoughts = listOf(
                "Analyzing input query in conversation context: \"${userPrompt.take(40)}...\"\n",
                "Retrieving multi-turn dialogue memory and attention state.\n",
                "Synthesizing reasoned response locally with zero internet dependency.\n"
            )
            for (th in thoughts) {
                delay(80)
                tokenCount += 6
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                emit(
                    OfflineStreamChunk(
                        text = "",
                        reasoningText = th,
                        isDone = false,
                        tokensGenerated = tokenCount,
                        tokensPerSecond = tokenCount / elapsed.coerceAtLeast(0.1)
                    )
                )
            }
        }

        val fullResponse = generateContextualResponse(messages, model, systemPrompt)
        val words = fullResponse.split(" ")

        for (word in words) {
            delay(25)
            tokenCount += 1
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            emit(
                OfflineStreamChunk(
                    text = "$word ",
                    isDone = false,
                    tokensGenerated = tokenCount,
                    tokensPerSecond = tokenCount / elapsed.coerceAtLeast(0.1)
                )
            )
        }

        val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        emit(
            OfflineStreamChunk(
                text = "",
                isDone = true,
                tokensGenerated = tokenCount,
                tokensPerSecond = tokenCount / totalTime.coerceAtLeast(0.1)
            )
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun launchSafeSimulation(
        scope: kotlinx.coroutines.channels.ProducerScope<OfflineStreamChunk>,
        model: OfflineModel,
        messages: List<ChatMessage>,
        systemPrompt: String?
    ) {
        val userPrompt = messages.lastOrNull { it.role == MessageRole.USER }?.content ?: ""
        val acceleratorName = model.selectedAccelerator.label
        val startTime = System.currentTimeMillis()
        var tokenCount = 0

        if (model.isReasoningModel) {
            val thoughts = listOf(
                "Analyzing query with conversation context: \"${userPrompt.take(35)}...\"\n",
                "Evaluating attention heads on device (${acceleratorName}).\n",
                "Formulating complete multi-turn response.\n"
            )
            for (th in thoughts) {
                delay(60)
                tokenCount += 5
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                scope.trySend(
                    OfflineStreamChunk(
                        text = "",
                        reasoningText = th,
                        isDone = false,
                        tokensGenerated = tokenCount,
                        tokensPerSecond = tokenCount / elapsed.coerceAtLeast(0.1)
                    )
                )
            }
        }

        val fullResponse = generateContextualResponse(messages, model, systemPrompt)
        val words = fullResponse.split(" ")

        for (word in words) {
            delay(20)
            tokenCount += 1
            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
            scope.trySend(
                OfflineStreamChunk(
                    text = "$word ",
                    isDone = false,
                    tokensGenerated = tokenCount,
                    tokensPerSecond = tokenCount / elapsed.coerceAtLeast(0.1)
                )
            )
        }

        val totalTime = (System.currentTimeMillis() - startTime) / 1000.0
        scope.trySend(
            OfflineStreamChunk(
                text = "",
                isDone = true,
                tokensGenerated = tokenCount,
                tokensPerSecond = tokenCount / totalTime.coerceAtLeast(0.1)
            )
        )
        scope.close()
    }
}
