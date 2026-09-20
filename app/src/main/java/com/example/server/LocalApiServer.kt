package com.example.server

import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.data.model.ModelInfo
import com.example.data.model.Server
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

class LocalApiServer(
    private val repository: ResoRepository
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

    fun getDeviceIp(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        if (!host.contains(":")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
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
        var requestPath = "/"
        var statusCode = 200
        var modelUsed: String? = null

        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val output = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size >= 2) {
                requestMethod = parts[0].uppercase()
                requestPath = parts[1]
            }

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

            // Check Authorization if enabled
            if (_requireAuth.value && requestPath.startsWith("/v1/")) {
                val authHeader = headers["authorization"] ?: ""
                val token = authHeader.removePrefix("Bearer ").trim()
                if (token != _apiKey.value) {
                    statusCode = 401
                    sendJsonResponse(output, 401, JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Incorrect or missing API key.")
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

            // Route Requests
            when {
                requestMethod == "GET" && (requestPath == "/" || requestPath == "/status") -> {
                    val activeServer = activeServerProvider()
                    val activeModel = activeModelProvider()
                    val json = JSONObject().apply {
                        put("app", "Reso Local API Gateway")
                        put("status", "running")
                        put("active_model", activeModel)
                        put("upstream_server", activeServer?.name ?: "None")
                        put("endpoints", JSONArray().apply {
                            put("/v1/models")
                            put("/v1/chat/completions")
                            put("/api/chat")
                        })
                        put("ip", getDeviceIp())
                        put("port", _port.value)
                    }
                    sendJsonResponse(output, 200, json.toString(2))
                }

                requestMethod == "GET" && requestPath == "/v1/models" -> {
                    val activeServer = activeServerProvider()
                    val modelsList = if (activeServer != null) {
                        repository.fetchModels(activeServer)
                    } else {
                        listOf(
                            ModelInfo("llama3.2:latest", "Llama 3.2 3B", null, 8192),
                            ModelInfo("deepseek-r1:8b", "DeepSeek R1 8B", null, 16384, isReasoningModel = true)
                        )
                    }

                    val modelsJson = JSONArray()
                    modelsList.forEach { m ->
                        modelsJson.put(JSONObject().apply {
                            put("id", m.id)
                            put("object", "model")
                            put("created", System.currentTimeMillis() / 1000)
                            put("owned_by", "reso-local")
                        })
                    }

                    val responseJson = JSONObject().apply {
                        put("object", "list")
                        put("data", modelsJson)
                    }
                    sendJsonResponse(output, 200, responseJson.toString())
                }

                requestMethod == "POST" && (requestPath == "/v1/chat/completions" || requestPath == "/api/chat") -> {
                    val reqJson = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                    val isStream = reqJson.optBoolean("stream", false)
                    val requestedModel = reqJson.optString("model", activeModelProvider())
                    modelUsed = requestedModel

                    val messagesJson = reqJson.optJSONArray("messages")
                    val messageList = mutableListOf<ChatMessage>()
                    if (messagesJson != null) {
                        for (i in 0 until messagesJson.length()) {
                            val msgObj = messagesJson.getJSONObject(i)
                            val roleStr = msgObj.optString("role", "user").uppercase()
                            val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                            val content = msgObj.optString("content", "")
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

                    val activeServer = activeServerProvider() ?: Server(
                        id = "default-ollama",
                        name = "Local Ollama",
                        type = com.example.data.model.ServerType.OLLAMA,
                        host = "10.0.2.2",
                        port = 11434
                    )

                    val temperature = reqJson.optDouble("temperature", 0.7).toFloat()
                    val topP = reqJson.optDouble("top_p", 0.9).toFloat()
                    val maxTokens = reqJson.optInt("max_tokens", 2048)

                    if (isStream) {
                        // Stream SSE Response
                        sendSseHeader(output)
                        val chatId = "chatcmpl-" + UUID.randomUUID().toString().take(12)
                        var chunkIndex = 0

                        repository.streamChat(
                            server = activeServer,
                            modelId = requestedModel,
                            messages = messageList,
                            systemPrompt = null,
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
                                output.write("data: ${ssePayload}\n\n".toByteArray(StandardCharsets.UTF_8))
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
                                output.write("data: ${stopPayload}\n\ndata: [DONE]\n\n".toByteArray(StandardCharsets.UTF_8))
                                output.flush()
                            }
                        }
                    } else {
                        // Non-streaming completion
                        var fullText = ""
                        var tokenCount = 0
                        repository.streamChat(
                            server = activeServer,
                            modelId = requestedModel,
                            messages = messageList,
                            systemPrompt = null,
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

                else -> {
                    statusCode = 404
                    val errJson = JSONObject().apply {
                        put("error", JSONObject().apply {
                            put("message", "Unknown endpoint: $requestPath")
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
                path = requestPath,
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

    private fun sendJsonResponse(output: OutputStream, status: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val statusText = when (status) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Error"
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

    private fun sendSseHeader(output: OutputStream) {
        val res = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/event-stream\r\n")
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
}
