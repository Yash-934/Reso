package com.example.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsSheet(
    initialTemp: Float,
    initialTopP: Float,
    initialMaxTokens: Int,
    initialSystemPrompt: String?,
    onSaveSettings: (Float, Float, Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var temperature by remember { mutableFloatStateOf(initialTemp) }
    var topP by remember { mutableFloatStateOf(initialTopP) }
    var maxTokens by remember { mutableIntStateOf(initialMaxTokens) }
    var systemPrompt by remember { mutableStateOf(initialSystemPrompt ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("chat_settings_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chat Parameters",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Temperature",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = String.format(Locale.US, "%.2f", temperature),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
            Slider(
                value = temperature,
                onValueChange = { temperature = (it * 100).roundToInt() / 100f },
                valueRange = 0.0f..2.0f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Top P
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Top P",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = String.format(Locale.US, "%.2f", topP),
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
            Slider(
                value = topP,
                onValueChange = { topP = (it * 100).roundToInt() / 100f },
                valueRange = 0.0f..1.0f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Max Tokens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Max Tokens",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    text = "$maxTokens",
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { maxTokens = it.roundToInt() },
                valueRange = 256f..8192f,
                steps = 30,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // System Prompt
            Text(
                text = "System Prompt",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                placeholder = { Text("Customize how the model behaves...") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSaveSettings(
                        temperature,
                        topP,
                        maxTokens,
                        systemPrompt.trim().ifEmpty { null }
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .testTag("save_parameters_button")
            ) {
                Text("Apply Parameters")
            }
        }
    }
}
