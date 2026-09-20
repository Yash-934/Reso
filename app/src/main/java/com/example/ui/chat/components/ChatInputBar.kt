package com.example.ui.chat.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun ChatInputBar(
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onOpenSavedPrompts: () -> Unit,
    inputTextState: String,
    onInputTextChanged: (String) -> Unit,
    selectedModel: String = "Select model",
    onOpenModelPicker: () -> Unit = {},
    onVoiceModeClick: () -> Unit = {},
    contextPercentage: Int = 44,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                val newText = if (inputTextState.isBlank()) spokenText else "$inputTextState $spokenText"
                onInputTextChanged(newText)
            }
        }
    }

    val suggestions = listOf(
        "Hello! How are you?",
        "I have a question",
        "Let's get started",
        "Explain this code",
        "Summarize text",
        "Brainstorm ideas"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Horizontal prompt suggestions row (Screenshot 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { prompt ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                onInputTextChanged(prompt)
                            }
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Expanded Pill Input Card (Screenshot 1)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // Top Row: Text field + Context Ring Percentage (Screenshot 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 2.dp, end = 8.dp)
                        ) {
                            if (inputTextState.isEmpty()) {
                                Text(
                                    text = "Ask anything",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 15.sp
                                    )
                                )
                            }

                            BasicTextField(
                                value = inputTextState,
                                onValueChange = onInputTextChanged,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 24.dp, max = 110.dp)
                                    .testTag("chat_input_text_field")
                            )
                        }

                        // Context Ring & Percentage (Screenshot 1: ⭕ 44%)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            val progressColor = MaterialTheme.colorScheme.primary
                            Canvas(modifier = Modifier.size(15.dp)) {
                                drawCircle(
                                    color = trackColor,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawArc(
                                    color = progressColor,
                                    startAngle = -90f,
                                    sweepAngle = (contextPercentage / 100f) * 360f,
                                    useCenter = false,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$contextPercentage%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bottom Row: + | Select model ⌵ | Mic | Waveform | Send (Screenshot 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left group: + and Select model ⌵
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // + Quick Actions / Saved Prompts button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                    .clickable(onClick = onOpenSavedPrompts),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Attach / Prompts",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // "Select model ⌵" pill button
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(onClick = onOpenModelPicker)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val displayModel = if (selectedModel.isNotBlank()) {
                                        selectedModel.substringBefore(":").take(16)
                                    } else {
                                        "Select model"
                                    }
                                    Text(
                                        text = displayModel,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Change model",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        // Right group: Mic, Waveform, Send
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Dictation Mic button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable {
                                        try {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(
                                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                                )
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your prompt...")
                                            }
                                            speechLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Voice input not available",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Voice dictation",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Waveform voice mode button
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                                    .clickable(onClick = onVoiceModeClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Voice mode",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send (Upward Arrow) or Stop Button
                            val canSend = inputTextState.isNotBlank() || isStreaming
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isStreaming -> MaterialTheme.colorScheme.error
                                            canSend -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        }
                                    )
                                    .clickable(enabled = canSend) {
                                        if (isStreaming) {
                                            onStopStreaming()
                                        } else if (inputTextState.isNotBlank()) {
                                            val textToSend = inputTextState.trim()
                                            onInputTextChanged("")
                                            onSendMessage(textToSend)
                                        }
                                    }
                                    .testTag("send_or_stop_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isStreaming) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = if (isStreaming) "Stop" else "Send",
                                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
