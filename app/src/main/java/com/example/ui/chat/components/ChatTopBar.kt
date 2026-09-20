package com.example.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
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
    onOpenSavedPrompts: (() -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChanged: (String) -> Unit = {},
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    searchMatchCount: Int = 0,
    currentMatchIndex: Int = 0,
    onNextMatch: () -> Unit = {},
    onPrevMatch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    if (isSearchActive) {
        TopAppBar(
            modifier = modifier.testTag("chat_search_top_bar"),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            ),
            navigationIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            },
            title = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in chat...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (searchQuery.isNotEmpty()) {
                            Text(
                                text = if (searchMatchCount > 0) "${currentMatchIndex + 1}/$searchMatchCount" else "0 found",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = onPrevMatch, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Prev match", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = onNextMatch, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        )
    } else {
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

                    // Incognito Chat button with glasses (chashme wala icon) - sleek icon button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isTemporary) WarningAmber.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(
                                width = if (isTemporary) 1.5.dp else 0.dp,
                                color = if (isTemporary) WarningAmber else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable(onClick = onToggleIncognito)
                            .testTag("incognito_chat_top_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_incognito_glasses),
                            contentDescription = if (isTemporary) "Incognito Mode Active - Tap to disable" else "Start Incognito Chat",
                            tint = if (isTemporary) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            },
            actions = {
                // Search inside chat button
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier.testTag("chat_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search messages"
                    )
                }

                // Local Server button
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

                // More Options Dropdown Menu
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
                            text = { Text(if (isTemporary) "Exit Incognito Mode" else "Incognito Mode") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_incognito_glasses),
                                    contentDescription = null,
                                    tint = if (isTemporary) WarningAmber else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleIncognito()
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
                            text = { Text("Chat Parameters") },
                            leadingIcon = {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onSettingsClick()
                            }
                        )

                        if (onOpenSavedPrompts != null) {
                            DropdownMenuItem(
                                text = { Text("Saved Prompts & Bookmarks") },
                                leadingIcon = {
                                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onOpenSavedPrompts()
                                }
                            )
                        }

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
}
