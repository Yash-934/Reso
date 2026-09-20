package com.example.ui.sidebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.SolidColor
import com.example.ui.components.ResoLogo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConnectionStatus
import com.example.data.model.Conversation
import com.example.data.model.Server
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun SidebarDrawer(
    conversations: List<Conversation>,
    currentConversationId: String?,
    activeServer: Server?,
    isTemporary: Boolean,
    isDarkTheme: Boolean,
    onSelectConversation: (Conversation) -> Unit,
    onNewChat: () -> Unit,
    onToggleTemporary: (Boolean) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onOpenServers: () -> Unit,
    onOpenLocalServer: () -> Unit,
    onOpenOfflineModels: () -> Unit = {},
    onOpenPersonas: () -> Unit,
    onOpenSavedPrompts: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else conversations.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.lastMessagePreview?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val pinnedConversations = filteredConversations.filter { it.isPinned }
    val recentConversations = filteredConversations.filter { !it.isPinned }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(310.dp)
            .testTag("sidebar_drawer"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Header: Reso Logo & Brand
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ResoLogo(size = 36.dp, cornerRadius = 10.dp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Reso",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        )
                        Text(
                            text = "LocalMind AI Client",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                // Server status indicator pill
                activeServer?.let { s ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onOpenServers)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dotColor = when (s.status) {
                            ConnectionStatus.CONNECTED -> SuccessGreen
                            ConnectionStatus.CHECKING -> WarningAmber
                            ConnectionStatus.ERROR -> ErrorRed
                            ConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = s.name.take(10),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // New Chat Action Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onNewChat)
                    .testTag("drawer_new_chat_button")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "New Conversation",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Temporary / Incognito switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isTemporary) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Temporary Chat",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Switch(
                    checked = isTemporary,
                    onCheckedChange = onToggleTemporary,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = WarningAmber,
                        checkedTrackColor = WarningAmber.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("temp_chat_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search conversations - Vertically Centered Search Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("search_conversations_field")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search conversations...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversations List (Pinned + Recent)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (pinnedConversations.isNotEmpty()) {
                    item {
                        Text(
                            text = "PINNED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                    items(pinnedConversations, key = { it.id }) { conv ->
                        ConversationItemTile(
                            conversation = conv,
                            isSelected = conv.id == currentConversationId,
                            onSelect = { onSelectConversation(conv) },
                            onTogglePin = { onTogglePin(conv.id, conv.isPinned) },
                            onDelete = { onDeleteConversation(conv.id) }
                        )
                    }
                }

                if (recentConversations.isNotEmpty()) {
                    item {
                        Text(
                            text = "CHATS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                        )
                    }
                    items(recentConversations, key = { it.id }) { conv ->
                        ConversationItemTile(
                            conversation = conv,
                            isSelected = conv.id == currentConversationId,
                            onSelect = { onSelectConversation(conv) },
                            onTogglePin = { onTogglePin(conv.id, conv.isPinned) },
                            onDelete = { onDeleteConversation(conv.id) }
                        )
                    }
                }

                if (filteredConversations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isBlank()) "No chats yet" else "No matching chats",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = DividerDefaults.color.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Bottom Navigation Items
            NavigationTile(
                icon = Icons.Default.AutoAwesome,
                title = "Offline AI Models (NPU)",
                subtitle = "Google AI Edge LiteRT • 100% on-device",
                onClick = onOpenOfflineModels
            )
            NavigationTile(
                icon = Icons.Default.Dns,
                title = "Servers & Models",
                subtitle = "${activeServer?.name ?: "Local Ollama"}",
                onClick = onOpenServers
            )
            NavigationTile(
                icon = Icons.Default.Dns,
                title = "Local API Server",
                subtitle = "Host Base URL & API endpoints",
                onClick = onOpenLocalServer
            )
            NavigationTile(
                icon = Icons.Default.Psychology,
                title = "Personas",
                subtitle = "Custom system prompts",
                onClick = onOpenPersonas
            )
            NavigationTile(
                icon = Icons.Default.Bookmark,
                title = "Saved Prompts",
                subtitle = "Snippets and shortcuts",
                onClick = onOpenSavedPrompts
            )
            NavigationTile(
                icon = Icons.Default.Settings,
                title = "Settings",
                subtitle = "TTS voice, exports & about",
                onClick = onOpenSettings
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Theme toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isDarkTheme) "Dark Mode" else "Light Mode",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationItemTile(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!conversation.lastMessagePreview.isNullOrEmpty()) {
                    Text(
                        text = conversation.lastMessagePreview,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Pin button
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = if (conversation.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (conversation.isPinned) "Unpin" else "Pin",
                    tint = if (conversation.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete conversation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun NavigationTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
