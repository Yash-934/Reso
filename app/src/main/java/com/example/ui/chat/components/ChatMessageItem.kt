package com.example.ui.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRole
import com.example.data.model.MessageStatus
import com.example.ui.components.ResoLogo
import com.example.ui.theme.CodeBlockBorder
import com.example.ui.theme.CodeBlockDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    isStreaming: Boolean,
    isSpeakingThis: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBookmark: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = message.role == MessageRole.USER
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showTextSelectionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("chat_message_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Author & Model Tag
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            if (isUser) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else {
                ResoLogo(size = 22.dp, cornerRadius = 6.dp)
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (isUser) "You" else (message.modelId ?: "Reso Assistant"),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            Text(
                text = timeFormat.format(Date(message.createdAt)),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            )
        }

        // Reasoning Log Box if available
        if (!message.reasoningContent.isNullOrBlank()) {
            ReasoningBox(
                reasoningText = message.reasoningContent,
                isStreaming = isStreaming && message.status == MessageStatus.STREAMING,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Bubble Content
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 1f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.content.isEmpty() && message.status == MessageStatus.STREAMING) {
                    Text(
                        text = "Thinking...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                } else {
                    FormattedMessageContent(text = message.content)
                }

                // Streaming animated dot
                if (message.status == MessageStatus.STREAMING && isStreaming) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Message Action Bar (Screenshot 4)
        if (message.content.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Timestamp (Screenshot 4: e.g. 05:52)
                Text(
                    text = timeFormat.format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Copy
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Reso Message", message.content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Bookmark / Save
                IconButton(
                    onClick = {
                        onBookmark()
                        Toast.makeText(context, "Saved to Bookmarks / Prompts", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Save message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Regenerate (if assistant)
                if (!isUser) {
                    IconButton(
                        onClick = onRegenerate,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate response",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Branch chat (>)
                IconButton(
                    onClick = {
                        onBranch()
                        Toast.makeText(context, "Branched into new conversation", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Branch chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Edit (✎)
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Delete (🗑 in red #E53935)
                IconButton(
                    onClick = {
                        onDelete()
                        Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete message",
                        tint = androidx.compose.ui.graphics.Color(0xFFE53935),
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Share (↗)
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Message"))
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Read Aloud (🔊)
                IconButton(
                    onClick = {
                        if (isSpeakingThis) onStopSpeak() else onSpeak(message.content)
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeakingThis) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isSpeakingThis) "Stop audio" else "Read aloud",
                        tint = if (isSpeakingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // More Options (...) (Screenshot 4)
                IconButton(
                    onClick = { showOptionsSheet = true },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Message Options Bottom Sheet
    if (showOptionsSheet) {
        MessageOptionsBottomSheet(
            message = message,
            isSpeakingThis = isSpeakingThis,
            onDismiss = { showOptionsSheet = false },
            onCopyMarkdown = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Markdown Message", message.content))
                Toast.makeText(context, "Copied as Markdown", Toast.LENGTH_SHORT).show()
            },
            onSelectText = {
                showTextSelectionDialog = true
            },
            onEditMessage = onEdit,
            onReadAloud = {
                if (isSpeakingThis) onStopSpeak() else onSpeak(message.content)
            },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.content)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Message"))
            },
            onBranch = {
                onBranch()
                Toast.makeText(context, "Branched into new conversation", Toast.LENGTH_SHORT).show()
            },
            onSaveMessage = {
                onBookmark()
                Toast.makeText(context, "Saved to Bookmarks / Prompts", Toast.LENGTH_SHORT).show()
            },
            onRegenerate = if (!isUser) onRegenerate else null,
            onDeleteMessage = {
                onDelete()
                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Text Selection Dialog
    if (showTextSelectionDialog) {
        TextSelectionDialog(
            text = message.content,
            onDismiss = { showTextSelectionDialog = false }
        )
    }
}

@Composable
fun FormattedMessageContent(text: String) {
    // Parse code blocks with ``` fences
    val parts = text.split("```")
    if (parts.size <= 1) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        )
    } else {
        Column {
            parts.forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    // Code or Formula block
                    val lines = part.trim().split("\n")
                    val rawLanguage = lines.firstOrNull()?.trim() ?: ""
                    val isFirstLineLang = rawLanguage.isNotBlank() && !rawLanguage.contains(" ") && rawLanguage.length < 25
                    val language = if (isFirstLineLang) rawLanguage else "code"
                    val codeContent = if (isFirstLineLang) lines.drop(1).joinToString("\n") else part.trim()

                    CodeOrFormulaBlock(
                        language = language,
                        codeContent = codeContent
                    )
                } else if (part.isNotEmpty()) {
                    Text(
                        text = part,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun CodeOrFormulaBlock(
    language: String,
    codeContent: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    val langLower = language.lowercase().trim()
    val isFormula = langLower in listOf("math", "formula", "latex", "tex", "equation", "calc")
    val displayLang = when {
        isFormula -> "Formula"
        langLower.isNotBlank() && langLower != "code" -> language.uppercase()
        else -> "Code"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CodeBlockDark)
            .border(
                1.dp,
                if (isFormula) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f) else CodeBlockBorder,
                RoundedCornerShape(10.dp)
            )
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodeBlockBorder.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Language / Formula Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFormula) Icons.Default.Functions else Icons.Default.Code,
                    contentDescription = null,
                    tint = if (isFormula) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayLang,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isFormula) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }

            // Action Buttons: Copy & Download
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Copy Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCopied) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(displayLang, codeContent))
                            isCopied = true
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFFCBD5E1),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCopied) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                        )
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Download / Save Action
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSaved) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f) else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f))
                        .clickable {
                            saveCodeOrFormulaToFile(context, language, codeContent)
                            isSaved = true
                            coroutineScope.launch {
                                delay(2000)
                                isSaved = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = "Download code",
                        tint = if (isSaved) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color(0xFFCBD5E1),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSaved) "Saved" else "Download",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSaved) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color(0xFFCBD5E1)
                        )
                    )
                }
            }
        }

        // Code / Formula Content with Horizontal Scroll
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = codeContent,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.5.sp,
                    color = if (isFormula) androidx.compose.ui.graphics.Color(0xFFF8FAFC) else androidx.compose.ui.graphics.Color(0xFFE2E8F0)
                )
            )
        }
    }
}

private fun saveCodeOrFormulaToFile(context: Context, language: String, content: String) {
    try {
        val langClean = language.lowercase().trim()
        val ext = when (langClean) {
            "python", "py" -> "py"
            "kotlin", "kt" -> "kt"
            "java" -> "java"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "sql" -> "sql"
            "c", "cpp", "c++" -> "cpp"
            "rust", "rs" -> "rs"
            "go" -> "go"
            "sh", "bash", "shell" -> "sh"
            "latex", "tex", "formula", "math", "equation" -> "tex"
            "xml" -> "xml"
            "yaml", "yml" -> "yaml"
            "markdown", "md" -> "md"
            else -> "txt"
        }
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val baseName = langClean.filter { it.isLetterOrDigit() }.ifBlank { "snippet" }
        val fileName = "${baseName}_${sdf.format(Date())}.$ext"

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        file.writeText(content)
        Toast.makeText(context, "Saved: $fileName in Downloads", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, "Code snippet ($language)")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Save or Share Code"))
        } catch (e2: Exception) {
            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}
