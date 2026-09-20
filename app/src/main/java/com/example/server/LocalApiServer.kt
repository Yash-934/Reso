package com.example.server

import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.ModelInfo
import com.example.data.model.OfflineModel
import com.example.data.model.OfflineModelStatus
import com.example.data.model.Server
import com.example.data.offline.OfflineModelManager
import com.example.data.repository.ResoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ServerLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val clientIp: String,
    val durationMs: Long,
    val model: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class NetworkAddressInfo(
    val label: String,
    val ip: String,
    val isRecommended: Boolean = false
)

class LocalApiServer(
    private val repository: ResoRepository,
    private val offlineModelManager: OfflineModelManager
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _port = MutableStateFlow(8080)
    val port: StateFlow<Int> = _port.asStateFlow()

    private val _apiKey = MutableStateFlow("reso-sk-local-" + UUID.randomUUID().toString().take(8))
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _requireAuth = MutableStateFlow(false)
    val requireAuth: StateFlow<Boolean> = _requireAuth.asStateFlow()

    private val _logs = MutableStateFlow<List<ServerLogEntry>>(emptyList())
    val logs: StateFlow<List<ServerLogEntry>> = _logs.asStateFlow()

    private val _totalRequests = MutableStateFlow(0)
    val totalRequests: StateFlow<Int> = _totalRequests.asStateFlow()

    fun getAvailableIpAddresses(): List<NetworkAddressInfo> {
        val results = mutableListOf<NetworkAddressInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.contains(":")) {
                            val name = intf.name.lowercase()
                            val (label, isRec) = when {
                                name.startsWith("wlan") -> "Wi-Fi (${intf.name})" to true
                                name.startsWith("ap") || name.startsWith("swlan") -> "Hotspot (${intf.name})" to true
                                name.startsWith("eth") -> "Ethernet (${intf.name})" to true
                                name.startsWith("rndis") -> "USB Tethering (${intf.name})" to false
                                name.startsWith("rmnet") || name.startsWith("ccmni") -> "Cellular (${intf.name})" to false
                                else -> "Network (${intf.name})" to false
                            }
                            results.add(NetworkAddressInfo(label, host, isRec))
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        results.add(NetworkAddressInfo("Localhost (device loopback)", "127.0.0.1", false))
        return results
    }

    fun getDeviceIp(): String {
        val addrs = getAvailableIpAddresses()
        return addrs.firstOrNull { it.label.startsWith("Wi-Fi") }?.ip
            ?: addrs.firstOrNull { it.label.startsWith("Hotspot") }?.ip
            ?: addrs.firstOrNull { it.label.startsWith("Ethernet") }?.ip
            ?: addrs.firstOrNull { it.label.startsWith("USB") }?.ip
            ?: addrs.firstOrNull { it.label.startsWith("Cellular") }?.ip
            ?: addrs.firstOrNull { it.ip != "127.0.0.1" }?.ip
            ?: "127.0.0.1"
    }

    fun getBaseUrl(): String {
        val ip = getDeviceIp()
        return "http://$ip:${_port.value}/v1"
    }

    fun setPort(newPort: Int) {
        if (!_isRunning.value && newPort in 1024..65535) {
            _port.value = newPort
        }
    }

    fun setRequireAuth(required: Boolean) {
        _requireAuth.value = required
    }

    fun regenerateApiKey() {
        _apiKey.value = "reso-sk-local-" + UUID.randomUUID().toString().take(8)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun startServer(activeServerProvider: () -> Server?, activeModelProvider: () -> String): Boolean {
        if (_isRunning.value) return true

        return try {
            val p = _port.value
            val socket = ServerSocket(p)
            serverSocket = socket
            _isRunning.value = true

            serverJob = serverScope.launch {
                while (isActive && !socket.isClosed) {
                    try {
                        val clientSocket = socket.accept()
                        launch {
                            handleClient(clientSocket, activeServerProvider, activeModelProvider)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            _isRunning.value = false
            false
        }
    }

    fun stopServer() {
        _isRunning.value = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private suspend fun handleClient(
        socket: Socket,
        activeServerProvider: () -> Server?,
        activeModelProvider: () -> String
    ) = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
        var requestMethod = "GET"
        var rawPath = "/"
        var statusCode = 200
        var modelUsed: String? = null

        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                requestMethod = parts[0].uppercase()
                rawPath = parts[1]
            }

            val cleanPath = rawPath.substringBefore('?').trimEnd('/')
            val normalizedPath = if (cleanPath.isEmpty()) "/" else cleanPath

            // Read Headers
            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val headerParts = line!!.split(":", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].trim().lowercase()] = headerParts[1].trim()
                }
            }

            // Handle CORS Preflight
            if (requestMethod == "OPTIONS") {
                sendCorsResponse(output)
                return@withContext
            }

            // Check Favicon (no log noise)
            if (normalizedPath == "/favicon.ico") {
                sendNoContentResponse(output)
                return@withContext
            }

            // Check Authorization if enabled
            if (_requireAuth.value && (normalizedPath.startsWith("/v1/") || normalizedPath == "/v1" || normalizedPath.startsWith("/api/"))) {
                val authHeader = headers["authorization"] ?: ""
                val token = authHeader.removePrefix("Bearer ").trim()
                if (token != _apiKey.value) {
                    statusCode = 401
                    sendJsonResponse(output, 401, JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Incorrect or missing API key. Provide Bearer ${_apiKey.value}")
                            put("type", "invalid_request_error")
                            put("code", "invalid_api_key")
                        })
                    }.toString())
                    return@withContext
                }
            }

            // Read Body if available
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = reader.read(charArray, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                String(charArray, 0, readTotal)
            } else ""

            val isBrowser = headers["accept"]?.contains("text/html") == true

            // Fetch current downloaded offline models
            val downloadedOffline = offlineModelManager.downloadedModels.firstOrNull() ?: emptyList()
            val allOffline = offlineModelManager.allModels.firstOrNull() ?: emptyList()

            // Route Requests
            when {
                // Status / Health check / Web Dashboard
                requestMethod == "GET" && (normalizedPath == "/" || normalizedPath == "/v1" || normalizedPath == "/status" || normalizedPath == "/v1/status" || normalizedPath == "/health" || normalizedPath == "/ping") -> {
                    val activeServer = activeServerProvider()
                    val activeModel = activeModelProvider()

                    if (isBrowser) {
                        // Serve Web Dashboard with Playground
                        val html = generateWebDashboardHtml(
                            activeModel = activeModel,
                            activeServer = activeServer,
                            downloadedModels = downloadedOffline,
                            deviceIp = getDeviceIp(),
                            port = _port.value,
                            apiKey = _apiKey.value,
                            requireAuth = _requireAuth.value
                        )
                        sendHtmlResponse(output, 200, html)
                    } else {
                        val json = JSONObject().apply {
                            put("app", "Reso Local AI Gateway")
                            put("status", "running")
                            put("active_model", activeModel)
                            put("upstream_server", activeServer?.name ?: "On-Device Offline Inference")
                            put("endpoints", JSONArray().apply {
                                put("/v1/models")
                                put("/v1/chat/completions")
                                put("/v1/status")
                                put("/api/chat")
                            })
                            put("ip", getDeviceIp())
                            put("port", _port.value)
                            put("auth_required", _requireAuth.value)
                        }
                        sendJsonResponse(output, 200, json.toString(2))
                    }
                }

                // List Models
                requestMethod == "GET" && (normalizedPath == "/v1/models" || normalizedPath == "/models") -> {
                    val activeServer = activeServerProvider()
                    val modelsJson = JSONArray()

                    // Add downloaded offline models
                    downloadedOffline.forEach { m ->
                        modelsJson.put(JSONObject().apply {
                            put("id", "offline:${m.id}")
                            put("object", "model")
                            put("created", System.currentTimeMillis() / 1000)
                            put("owned_by", "reso-offline-npu")
                            put("description", "${m.name} (${m.formattedSize})")
                        })
                    }

                    // Add remote server models if available
                    if (activeServer != null) {
                        val remoteModels = repository.fetchModels(activeServer)
                        remoteModels.forEach { m ->
                            modelsJson.put(JSONObject().apply {
                                put("id", m.id)
                                put("object", "model")
                                put("created", System.currentTimeMillis() / 1000)
                                put("owned_by", activeServer.name)
                                put("description", m.description ?: "Remote server model")
                            })
                        }
                    }

                    // If no models loaded, provide default representation
                    if (modelsJson.length() == 0) {
                        modelsJson.put(JSONObject().apply {
                            put("id", activeModelProvider())
                            put("object", "model")
                            put("created", System.currentTimeMillis() / 1000)
                            put("owned_by", "reso-local")
                        })
                    }

                    val responseJson = JSONObject().apply {
                        put("object", "list")
                        put("data", modelsJson)
                    }
                    sendJsonResponse(output, 200, responseJson.toString(2))
                }

                // Chat Completions (OpenAI & Ollama format)
                requestMethod == "POST" && (normalizedPath == "/v1/chat/completions" || normalizedPath == "/chat/completions" || normalizedPath == "/api/chat" || normalizedPath == "/v1/completions" || normalizedPath == "/api/generate") -> {
                    val reqJson = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                    val isStream = reqJson.optBoolean("stream", false)
                    val fallbackModel = activeModelProvider()
                    val requestedModel = reqJson.optString("model", fallbackModel).ifEmpty { fallbackModel }
                    modelUsed = requestedModel

                    // Parse messages
                    val messageList = mutableListOf<ChatMessage>()
                    var systemPrompt: String? = null

                    val messagesJson = reqJson.optJSONArray("messages")
                    if (messagesJson != null) {
                        for (i in 0 until messagesJson.length()) {
                            val msgObj = messagesJson.getJSONObject(i)
                            val roleStr = msgObj.optString("role", "user").uppercase()
                            val content = msgObj.optString("content", "")
                            if (roleStr == "SYSTEM") {
                                systemPrompt = content
                            } else {
                                val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                                messageList.add(
                                    ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        conversationId = "api-request",
                                        role = role,
                                        content = content,
                                        status = MessageStatus.COMPLETE
                                    )
                                )
                            }
                        }
                    } else {
                        // Support single prompt e.g. /v1/completions or /api/generate
                        val promptText = reqJson.optString("prompt", "")
                        if (promptText.isNotEmpty()) {
                            messageList.add(
                                ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    conversationId = "api-request",
                                    role = MessageRole.USER,
                                    content = promptText,
                                    status = MessageStatus.COMPLETE
                                )
                            )
                        }
                    }

                    if (messageList.isEmpty()) {
                        statusCode = 400
                        sendJsonResponse(output, 400, JSONObject().apply {
                            put("error", JSONObject().apply {
                                put("message", "No prompt or messages provided in request.")
                                put("type", "invalid_request_error")
                            })
                        }.toString())
                        return@withContext
                    }

                    val temperature = reqJson.optDouble("temperature", 0.7).toFloat()
                    val topP = reqJson.optDouble("top_p", 0.9).toFloat()
                    val maxTokens = reqJson.optInt("max_tokens", 2048)

                    // Determine if offline model or remote server
                    val isOffline = requestedModel.startsWith("offline:") ||
                            downloadedOffline.any { it.id == requestedModel || "offline:${it.id}" == requestedModel } ||
                            (activeServerProvider() == null)

                    if (isOffline) {
                        val cleanId = requestedModel.removePrefix("offline:")
                        val offlineModel = downloadedOffline.firstOrNull { it.id == cleanId }
                            ?: allOffline.firstOrNull { it.id == cleanId }

                        if (offlineModel == null || offlineModel.status != OfflineModelStatus.DOWNLOADED) {
                            statusCode = 400
                            val availableModels = downloadedOffline.map { "offline:${it.id}" }
                            sendJsonResponse(output, 400, JSONObject().apply {
                                put("error", JSONObject().apply {
                                    put("message", "Offline model '$requestedModel' is not downloaded on device yet. Download it in Reso Offline Hub or select an available model: $availableModels")
                                    put("type", "model_not_found")
                                    put("code", "model_not_ready")
                                })
                            }.toString())
                            return@withContext
                        }

                        // Stream or non-stream using On-Device Offline Engine
                        if (isStream) {
                            sendSseHeader(output)
                            val chatId = "chatcmpl-" + UUID.randomUUID().toString().take(12)
                            var chunkIndex = 0

                            offlineModelManager.streamChat(
                                model = offlineModel,
                                messages = messageList,
                                systemPrompt = systemPrompt,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens
                            ).collect { chunk ->
                                if (chunk.text.isNotEmpty()) {
                                    val ssePayload = JSONObject().apply {
                                        put("id", chatId)
                                        put("object", "chat.completion.chunk")
                                        put("created", System.currentTimeMillis() / 1000)
                                        put("model", requestedModel)
                                        put("choices", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("index", 0)
                                                put("delta", JSONObject().apply {
                                                    put("content", chunk.text)
                                                })
                                                put("finish_reason", null)
                                            })
                                        })
                                    }
                                    output.write("data: $ssePayload\n\n".toByteArray(StandardCharsets.UTF_8))
                                    output.flush()
                                    chunkIndex++
                                }
                                if (chunk.isDone) {
                                    val stopPayload = JSONObject().apply {
                                        put("id", chatId)
                                        put("object", "chat.completion.chunk")
                                        put("created", System.currentTimeMillis() / 1000)
                                        put("model", requestedModel)
                                        put("choices", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("index", 0)
                                                put("delta", JSONObject())
                                                put("finish_reason", "stop")
                                            })
                                        })
                                    }
                                    output.write("data: $stopPayload\n\ndata: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
                                    output.flush()
                                }
                            }
                        } else {
                            var fullText = ""
                            var tokenCount = 0
                            offlineModelManager.streamChat(
                                model = offlineModel,
                                messages = messageList,
                                systemPrompt = systemPrompt,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens
                            ).collect { chunk ->
                                if (chunk.text.isNotEmpty()) {
                                    fullText += chunk.text
                                }
                                if (chunk.tokensGenerated > tokenCount) {
                                    tokenCount = chunk.tokensGenerated
                                }
                            }

                            val responseJson = JSONObject().apply {
                                put("id", "chatcmpl-" + UUID.randomUUID().toString().take(12))
                                put("object", "chat.completion")
                                put("created", System.currentTimeMillis() / 1000)
                                put("model", requestedModel)
                                put("choices", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("index", 0)
                                        put("message", JSONObject().apply {
                                            put("role", "assistant")
                                            put("content", fullText)
                                        })
                                        put("finish_reason", "stop")
                                    })
                                })
                                put("usage", JSONObject().apply {
                                    put("prompt_tokens", messageList.sumOf { it.content.length / 4 }.coerceAtLeast(1))
                                    put("completion_tokens", tokenCount.coerceAtLeast(1))
                                    put("total_tokens", (messageList.sumOf { it.content.length / 4 } + tokenCount).coerceAtLeast(2))
                                })
                            }
                            sendJsonResponse(output, 200, responseJson.toString())
                        }
                    } else {
                        // Remote LLM Server routing
                        val activeServer = activeServerProvider()
                        if (activeServer == null) {
                            statusCode = 503
                            sendJsonResponse(output, 503, JSONObject().apply {
                                put("error", JSONObject().apply {
                                    put("message", "No active remote LLM server is connected. Please connect Ollama/vLLM/OpenAI in Reso or select an offline model.")
                                    put("type", "server_error")
                                })
                            }.toString())
                            return@withContext
                        }

                        if (isStream) {
                            sendSseHeader(output)
                            val chatId = "chatcmpl-" + UUID.randomUUID().toString().take(12)

                            repository.streamChat(
                                server = activeServer,
                                modelId = requestedModel,
                                messages = messageList,
                                systemPrompt = systemPrompt,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens
                            ).collect { chunk ->
                                if (chunk.text.isNotEmpty()) {
                                    val ssePayload = JSONObject().apply {
                                        put("id", chatId)
                                        put("object", "chat.completion.chunk")
                                        put("created", System.currentTimeMillis() / 1000)
                                        put("model", requestedModel)
                                        put("choices", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("index", 0)
                                                put("delta", JSONObject().apply {
                                                    put("content", chunk.text)
                                                })
                                                put("finish_reason", null)
                                            })
                                        })
                                    }
                                    output.write("data: $ssePayload\n\n".toByteArray(StandardCharsets.UTF_8))
                                    output.flush()
                                }
                                if (chunk.isDone) {
                                    val stopPayload = JSONObject().apply {
                                        put("id", chatId)
                                        put("object", "chat.completion.chunk")
                                        put("created", System.currentTimeMillis() / 1000)
                                        put("model", requestedModel)
                                        put("choices", JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("index", 0)
                                                put("delta", JSONObject())
                                                put("finish_reason", "stop")
                                            })
                                        })
                                    }
                                    output.write("data: $stopPayload\n\ndata: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
                                    output.flush()
                                }
                            }
                        } else {
                            var fullText = ""
                            var tokenCount = 0
                            repository.streamChat(
                                server = activeServer,
                                modelId = requestedModel,
                                messages = messageList,
                                systemPrompt = systemPrompt,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokens
                            ).collect { chunk ->
                                if (chunk.text.isNotEmpty()) {
                                    fullText += chunk.text
                                }
                                if (chunk.tokensGenerated > tokenCount) {
                                    tokenCount = chunk.tokensGenerated
                                }
                            }

                            val responseJson = JSONObject().apply {
                                put("id", "chatcmpl-" + UUID.randomUUID().toString().take(12))
                                put("object", "chat.completion")
                                put("created", System.currentTimeMillis() / 1000)
                                put("model", requestedModel)
                                put("choices", JSONArray().apply {
                                    put(JSONObject().apply {
                                        put("index", 0)
                                        put("message", JSONObject().apply {
                                            put("role", "assistant")
                                            put("content", fullText)
                                        })
                                        put("finish_reason", "stop")
                                    })
                                })
                                put("usage", JSONObject().apply {
                                    put("prompt_tokens", messageList.sumOf { it.content.length / 4 }.coerceAtLeast(1))
                                    put("completion_tokens", tokenCount.coerceAtLeast(1))
                                    put("total_tokens", (messageList.sumOf { it.content.length / 4 } + tokenCount).coerceAtLeast(2))
                                })
                            }
                            sendJsonResponse(output, 200, responseJson.toString())
                        }
                    }
                }

                else -> {
                    statusCode = 404
                    val errJson = JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Unknown endpoint: $rawPath. Supported endpoints: /v1, /v1/models, /v1/chat/completions, /v1/status")
                            put("type", "invalid_request_error")
                        })
                    }
                    sendJsonResponse(output, 404, errJson.toString())
                }
            }
        } catch (e: Exception) {
            statusCode = 500
            try {
                val errJson = JSONObject().apply {
                    put("error", JSONObject().apply {
                        put("message", e.message ?: "Internal server error")
                        put("type", "api_error")
                    })
                }
                sendJsonResponse(socket.getOutputStream(), 500, errJson.toString())
            } catch (_: Exception) {}
        } finally {
            val duration = System.currentTimeMillis() - startTime
            _totalRequests.value = _totalRequests.value + 1
            val entry = ServerLogEntry(
                method = requestMethod,
                path = rawPath,
                statusCode = statusCode,
                clientIp = clientIp,
                durationMs = duration,
                model = modelUsed
            )
            _logs.value = (listOf(entry) + _logs.value).take(100)

            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun sendCorsResponse(output: OutputStream) {
        val res = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, Accept, X-Requested-With\r\n")
            append("Access-Control-Max-Age: 86400\r\n")
            append("\r\n")
        }
        output.write(res.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun sendNoContentResponse(output: OutputStream) {
        val res = buildString {
            append("HTTP/1.1 204 No Content\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(res.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun sendJsonResponse(output: OutputStream, status: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val statusText = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Internal Server Error"
        }
        val res = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, Accept\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(res.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendHtmlResponse(output: OutputStream, status: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        val res = buildString {
            append("HTTP/1.1 $status OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(res.toByteArray(StandardCharsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun sendSseHeader(output: OutputStream) {
        val res = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream; charset=utf-8\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Connection: keep-alive\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Authorization, Content-Type, Accept\r\n")
            append("\r\n")
        }
        output.write(res.toByteArray(StandardCharsets.UTF_8))
        output.flush()
    }

    private fun generateWebDashboardHtml(
        activeModel: String,
        activeServer: Server?,
        downloadedModels: List<OfflineModel>,
        deviceIp: String,
        port: Int,
        apiKey: String,
        requireAuth: Boolean
    ): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reso Local AI Gateway</title>
    <style>
        :root {
            --bg: #0F172A;
            --surface: #1E293B;
            --border: #334155;
            --primary: #38BDF8;
            --text: #F8FAFC;
            --text-dim: #94A3B8;
            --success: #22C55E;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background: var(--bg); color: var(--text); padding: 24px; display: flex; justify-content: center; }
        .container { max-width: 760px; width: 100%; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 24px; margin-bottom: 20px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.3); }
        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
        .badge { display: inline-flex; align-items: center; gap: 6px; background: rgba(34, 197, 94, 0.15); color: var(--success); padding: 4px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; }
        .dot { width: 8px; height: 8px; background: var(--success); border-radius: 50%; animation: pulse 2s infinite; }
        @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
        h1 { font-size: 22px; font-weight: 700; color: #FFF; }
        p { color: var(--text-dim); font-size: 14px; line-height: 1.5; }
        .meta-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 16px; }
        .meta-box { background: rgba(0,0,0,0.25); border: 1px solid var(--border); border-radius: 10px; padding: 12px; }
        .meta-label { font-size: 11px; text-transform: uppercase; color: var(--text-dim); letter-spacing: 0.5px; }
        .meta-value { font-size: 14px; font-weight: 600; color: var(--text); margin-top: 4px; word-break: break-all; }
        .code-box { background: #090D16; border: 1px solid var(--border); border-radius: 10px; padding: 14px; font-family: monospace; font-size: 13px; color: #E2E8F0; overflow-x: auto; margin-top: 10px; }
        .input-group { margin-top: 16px; display: flex; gap: 8px; }
        input[type="text"] { flex: 1; background: #090D16; border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; color: #FFF; font-size: 14px; outline: none; }
        input[type="text"]:focus { border-color: var(--primary); }
        button { background: var(--primary); color: #0F172A; border: none; border-radius: 8px; padding: 10px 18px; font-weight: 600; cursor: pointer; transition: 0.2s; }
        button:hover { opacity: 0.9; }
        #response-box { margin-top: 14px; display: none; background: #090D16; border: 1px solid var(--border); border-radius: 10px; padding: 14px; font-family: monospace; font-size: 13px; max-height: 250px; overflow-y: auto; white-space: pre-wrap; color: #38BDF8; }
    </style>
</head>
<body>
    <div class="container">
        <div class="card">
            <div class="header">
                <div>
                    <h1>⚡ Reso Local AI Gateway</h1>
                    <p>OpenAI & Ollama Compatible On-Device API Endpoint</p>
                </div>
                <div class="badge"><span class="dot"></span> Online</div>
            </div>

            <div class="meta-grid">
                <div class="meta-box">
                    <div class="meta-label">Base URL</div>
                    <div class="meta-value">http://$deviceIp:$port/v1</div>
                </div>
                <div class="meta-box">
                    <div class="meta-label">Active Model</div>
                    <div class="meta-value">$activeModel</div>
                </div>
                <div class="meta-box">
                    <div class="meta-label">Routing Backend</div>
                    <div class="meta-value">${activeServer?.name ?: "On-Device NPU/GPU Inference"}</div>
                </div>
                <div class="meta-box">
                    <div class="meta-label">Auth Key</div>
                    <div class="meta-value">${if (requireAuth) apiKey else "Disabled (Open)"}</div>
                </div>
            </div>
        </div>

        <div class="card">
            <h2 style="font-size: 17px; margin-bottom: 8px;">🚀 Interactive Test Playground</h2>
            <p>Test sending a chat prompt directly to this local endpoint:</p>
            <div class="input-group">
                <input type="text" id="promptInput" placeholder="Ask anything (e.g. 'Say hello in 3 words')..." value="Hello, local AI!">
                <button onclick="sendTestChat()">Send</button>
            </div>
            <div id="response-box"></div>
        </div>

        <div class="card">
            <h2 style="font-size: 17px; margin-bottom: 8px;">📋 Quick cURL Example</h2>
            <div class="code-box">curl -X POST http://$deviceIp:$port/v1/chat/completions \
  -H "Content-Type: application/json" \
  ${if (requireAuth) "-H \"Authorization: Bearer $apiKey\" \\\n  " else ""}-d '{
    "model": "$activeModel",
    "messages": [{"role": "user", "content": "Hello world!"}]
  }'</div>
        </div>
    </div>

    <script>
        async function sendTestChat() {
            const prompt = document.getElementById('promptInput').value;
            const respBox = document.getElementById('response-box');
            respBox.style.display = 'block';
            respBox.innerText = 'Connecting to model...';

            try {
                const res = await fetch('/v1/chat/completions', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        ${if (requireAuth) "'Authorization': 'Bearer $apiKey'" else ""}
                    },
                    body: JSON.stringify({
                        model: '$activeModel',
                        messages: [{ role: 'user', content: prompt }]
                    })
                });

                const data = await res.json();
                if (data.error) {
                    respBox.innerText = 'Error: ' + data.error.message;
                    respBox.style.color = '#EF4444';
                } else {
                    respBox.innerText = data.choices[0].message.content;
                    respBox.style.color = '#38BDF8';
                }
            } catch (err) {
                respBox.innerText = 'Request failed: ' + err.message;
                respBox.style.color = '#EF4444';
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
