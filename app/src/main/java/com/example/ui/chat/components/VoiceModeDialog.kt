package com.example.ui.chat.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Persona
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun VoiceModeDialog(
    selectedModel: String,
    activePersona: Persona?,
    isStreaming: Boolean,
    isTtsSpeaking: Boolean,
    lastAiResponse: String?,
    onSendMessage: (String) -> Unit,
    onStopStreaming: () -> Unit,
    onStopTts: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var spokenTranscript by remember { mutableStateOf("") }
    var aiSpokenText by remember { mutableStateOf("") }
    var isMuted by remember { mutableStateOf(false) }

    // Fallback Intent Launcher if SpeechRecognizer service is unavailable
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                spokenTranscript = text
                voiceState = VoiceState.THINKING
                onSendMessage(text)
            } else {
                voiceState = VoiceState.IDLE
            }
        } else {
            voiceState = VoiceState.IDLE
        }
    }

    fun startListening() {
        if (isMuted) return
        onStopTts()
        spokenTranscript = ""
        voiceState = VoiceState.LISTENING

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
            }
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice input not supported", Toast.LENGTH_SHORT).show()
            voiceState = VoiceState.IDLE
        }
    }

    LaunchedEffect(isStreaming, isTtsSpeaking, lastAiResponse) {
        when {
            isStreaming -> {
                voiceState = VoiceState.THINKING
            }
            isTtsSpeaking -> {
                voiceState = VoiceState.SPEAKING
                if (!lastAiResponse.isNullOrBlank()) {
                    aiSpokenText = lastAiResponse
                }
            }
            voiceState == VoiceState.SPEAKING && !isTtsSpeaking -> {
                voiceState = VoiceState.IDLE
            }
        }
    }

    // Pulse Animation
    val infiniteTransition = rememberInfiniteTransition(label = "voice_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_angle"
    )

    Dialog(
        onDismissRequest = {
            onStopStreaming()
            onStopTts()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("voice_mode_dialog"),
            color = Color(0xFF090D16)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Top Header: Model / Persona Info & Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activePersona?.emoji ?: "⚡",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activePersona?.name ?: "LocalMind AI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                        Text(
                            text = selectedModel.removePrefix("offline:").substringBefore(":"),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        )
                    }

                    IconButton(
                        onClick = {
                            onStopStreaming()
                            onStopTts()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Voice Mode",
                            tint = Color.White
                        )
                    }
                }

                // Center Dynamic Waveform & Voice Visualizer Orb
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val primaryColor = when (voiceState) {
                        VoiceState.LISTENING -> Color(0xFF38BDF8)
                        VoiceState.THINKING -> Color(0xFFA855F7)
                        VoiceState.SPEAKING -> Color(0xFF22C55E)
                        VoiceState.IDLE -> Color(0xFF64748B)
                    }

                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .scale(if (voiceState != VoiceState.IDLE) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.45f),
                                        primaryColor.copy(alpha = 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .clickable {
                                if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) {
                                    onStopStreaming()
                                    onStopTts()
                                    voiceState = VoiceState.IDLE
                                } else {
                                    startListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (voiceState) {
                                    VoiceState.LISTENING -> Icons.Default.Mic
                                    VoiceState.THINKING -> Icons.Default.GraphicEq
                                    VoiceState.SPEAKING -> Icons.Default.GraphicEq
                                    VoiceState.IDLE -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = when (voiceState) {
                            VoiceState.LISTENING -> "Listening to your voice..."
                            VoiceState.THINKING -> "Thinking..."
                            VoiceState.SPEAKING -> "Speaking..."
                            VoiceState.IDLE -> "Tap orb or mic to speak"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live spoken text or AI transcript
                    if (spokenTranscript.isNotBlank() || aiSpokenText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.8f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = if (voiceState == VoiceState.SPEAKING) aiSpokenText else spokenTranscript,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Bottom Action Buttons: Mic Tap | Stop/Interrupt | Mute
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute button
                    IconButton(
                        onClick = { isMuted = !isMuted },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = if (isMuted) Color(0xFFEF4444) else Color.White
                        )
                    }

                    // Main Speak / Push-to-Talk button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                            .clickable {
                                if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) {
                                    onStopStreaming()
                                    onStopTts()
                                    voiceState = VoiceState.IDLE
                                } else {
                                    startListening()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Speak / Stop",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Interrupt / Stop Voice button
                    IconButton(
                        onClick = {
                            onStopStreaming()
                            onStopTts()
                            voiceState = VoiceState.IDLE
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
