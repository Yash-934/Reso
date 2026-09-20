package com.example.data.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class McpTool(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val isEnabled: Boolean = false
)

object McpToolManager {

    suspend fun performWebSearch(query: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val results = mutableListOf<String>()
                
                // Simple regex extraction for snippets
                val snippetRegex = Regex("""<a class="result__snippet[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                val matches = snippetRegex.findAll(html).take(4)
                
                for (match in matches) {
                    val rawText = match.groupValues[1]
                        .replace(Regex("<.*?>"), "")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&amp;", "&")
                        .trim()
                    if (rawText.isNotEmpty()) {
                        results.add(rawText)
                    }
                }

                if (results.isNotEmpty()) {
                    return@withContext results.joinToString("\n\n") { "• $it" }
                }
            }
            "No web search results found for: \"$query\""
        } catch (e: Exception) {
            "Web search could not be completed: ${e.message}"
        }
    }

    fun evaluateMath(expression: String): String {
        return try {
            val sanitized = expression.replace("×", "*").replace("÷", "/").trim()
            val result = simpleEval(sanitized)
            "Result: $result"
        } catch (e: Exception) {
            "Math evaluation error: ${e.message}"
        }
    }

    private fun simpleEval(expr: String): Double {
        // Basic expression parser for standard arithmetic
        val tokens = expr.replace(" ", "")
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < tokens.length) tokens[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < tokens.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = tokens.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                return x
            }
        }.parse()
    }
}
