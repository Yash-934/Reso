package com.example.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Persona
import com.example.data.model.Server
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    activeServer: Server?,
    selectedModel: String,
    activePersona: Persona?,
    isTemporary: Boolean,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    onPersonaClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLocalServerClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onToggleIncognito: () -> Unit,
    onRenameConversation: () -> Unit,
    onMoveToFolder: () -> Unit,
    onExportConversation: () -> Unit,
    onShareConversation: () -> Unit,
    onClearConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier.testTag("chat_top_bar"),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.testTag("drawer_menu_button")
            ) {
                Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Model selector button
                val isOffline = selectedModel.startsWith("offline:")
                val displayModelName = if (isOffline) {
                    val raw = selectedModel.removePrefix("offline:")
                    when {
                        raw.contains("gemma3", ignoreCase = true) -> "Gemma 3 1B"
                        raw.contains("deepseek", ignoreCase = true) -> "DeepSeek R1"
                        raw.contains("qwen", ignoreCase = true) -> "Qwen 2.5"
                        raw.contains("tiny", ignoreCase = true) -> "TinyGarden"
                        else -> raw.take(14)
                    }
                } else {
                    selectedModel.substringBefore(":")
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isOffline) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onModelClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("model_selector_chip"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOffline) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "On-Device NPU",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = displayModelName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select model",
                        modifier = Modifier.size(16.dp),
                        tint = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Persona badge
                activePersona?.let { persona ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = onPersonaClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("persona_badge_chip"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = persona.emoji, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = persona.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isTemporary) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WarningAmber.copy(alpha = 0.2f))
                            .clickable(onClick = onToggleIncognito)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.VisibilityOff,
                                contentDescription = "Incognito Active",
                                tint = WarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Incognito",
                                color = WarningAmber,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        actions = {
            // Incognito toggle button
            IconButton(
                onClick = onToggleIncognito,
                modifier = Modifier.testTag("incognito_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = if (isTemporary) "Exit Incognito" else "Enter Incognito",
                    tint = if (isTemporary) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onLocalServerClick,
                modifier = Modifier.testTag("open_local_server_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = "Local API Server"
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("chat_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Chat parameters"
                )
            }

            // More Options Dropdown Menu (Screenshot 2)
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("conversation_options_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Conversation options"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.testTag("conversation_dropdown_menu")
                ) {
                    DropdownMenuItem(
                        text = { Text("New Chat") },
                        leadingIcon = {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onNewChatClick()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Rename conversation") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onRenameConversation()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onMoveToFolder()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Export conversation") },
                        leadingIcon = {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onExportConversation()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Share conversation") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onShareConversation()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Change Persona") },
                        leadingIcon = {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        onClick = {
                            showMenu = false
                            onPersonaClick()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Clear Conversation",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onClearConversation()
                        }
                    )
                }
            }
        }
    )
}
