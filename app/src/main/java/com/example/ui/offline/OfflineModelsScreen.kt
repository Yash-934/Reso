package com.example.ui.offline

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Accelerator
import com.example.data.model.OfflineModel
import com.example.data.model.OfflineModelStatus
import com.example.data.offline.BenchmarkResult
import com.example.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineModelsScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onStartChatWithModel: (OfflineModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val allModels by viewModel.offlineModels.collectAsState()
    val liveProgressMap by viewModel.downloadProgress.collectAsState()
    val selectedModelId by viewModel.selectedModel.collectAsState()
    val isBenchmarking by viewModel.isBenchmarking.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showHfDialog by remember { mutableStateOf(false) }
    var hfInputText by remember { mutableStateOf("") }
    var modelToDelete by remember { mutableStateOf<OfflineModel?>(null) }
    var benchmarkModel by remember { mutableStateOf<OfflineModel?>(null) }
    var activeBenchmarkResult by remember { mutableStateOf<BenchmarkResult?>(null) }
    var detailModel by remember { mutableStateOf<OfflineModel?>(null) }
    var importProgress by remember { mutableFloatStateOf(-1f) }
    var storageInfo by remember { mutableStateOf(viewModel.getStorageInfo()) }

    // Ensure models are synced when entering screen
    LaunchedEffect(Unit) {
        viewModel.syncDefaultModels()
        storageInfo = viewModel.getStorageInfo()
    }

    // File picker for local .litertlm / .task models
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importProgress = 0f
            viewModel.importLocalModel(
                uri = uri,
                onProgress = { p -> importProgress = p },
                onSuccess = { imported ->
                    importProgress = -1f
                    storageInfo = viewModel.getStorageInfo()
                    Toast.makeText(context, "Imported ${imported.name} successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    importProgress = -1f
                    Toast.makeText(context, "Failed to import: $err", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Merge static and live progress models
    val displayedModels: List<OfflineModel> = remember(allModels, liveProgressMap, searchQuery, selectedFilter) {
        val merged = allModels.map { staticModel ->
            liveProgressMap[staticModel.id] ?: staticModel
        }
        var list = merged
        // Apply filter
        list = when (selectedFilter) {
            "Downloaded" -> list.filter { it.status == OfflineModelStatus.DOWNLOADED || it.isImported }
            "Gemma" -> list.filter { it.name.contains("Gemma", ignoreCase = true) }
            "Qwen" -> list.filter { it.name.contains("Qwen", ignoreCase = true) }
            "Reasoning" -> list.filter { it.isReasoningModel }
            else -> list
        }
        // Apply search
        if (searchQuery.isBlank()) {
            list
        } else {
            list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.description.contains(searchQuery, ignoreCase = true) ||
                        it.modelId.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("offline_models_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Models (${allModels.size})",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.syncDefaultModels()
                        storageInfo = viewModel.getStorageInfo()
                        Toast.makeText(context, "Models catalog refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh models catalog")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Storage Bar & Import quick buttons
            item {
                Spacer(modifier = Modifier.height(2.dp))
                StorageStatusCard(
                    storageInfo = storageInfo,
                    onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onHfClick = { showHfDialog = true }
                )
            }

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("search_offline_models_field"),
                    placeholder = { Text("Search models (Gemma, DeepSeek, Qwen...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )
            }

            // Filter Chips
            item {
                val filters = listOf("All", "Downloaded", "Gemma", "Qwen", "Reasoning")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter, fontSize = 12.5.sp) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Models list (Edge Gallery layout)
            items(displayedModels, key = { it.id }) { model ->
                val isCurrentActive = selectedModelId == "offline:${model.id}" || selectedModelId == model.id
                val requiresHfToken = model.name.contains("Gemma", ignoreCase = true) || model.downloadUrl.contains("huggingface.co/google/")
                EdgeGalleryModelCard(
                    model = model,
                    isActive = isCurrentActive,
                    onDownload = {
                        val currentToken = viewModel.getHfToken()
                        if (requiresHfToken && currentToken.isNullOrBlank()) {
                            showHfDialog = true
                        } else {
                            viewModel.startDownloadOfflineModel(model)
                        }
                    },
                    onOpenHfDialog = { showHfDialog = true },
                    onCancel = { viewModel.cancelDownloadOfflineModel(model.id) },
                    onDelete = { modelToDelete = model },
                    onSelectAccelerator = { acc -> viewModel.updateOfflineAccelerator(model.id, acc) },
                    onBenchmark = {
                        benchmarkModel = model
                        viewModel.runBenchmark(model) { result ->
                            activeBenchmarkResult = result
                        }
                    },
                    onStartChat = {
                        viewModel.selectOfflineModel(model)
                        onStartChatWithModel(model)
                    },
                    onShowDetails = { detailModel = model },
                    onOpenLicense = {
                        try {
                            uriHandler.openUri(model.licenseUrl)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open URL: ${model.licenseUrl}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Benchmark Dialog
    benchmarkModel?.let { model ->
        AlertDialog(
            onDismissRequest = {
                if (!isBenchmarking) {
                    benchmarkModel = null
                    activeBenchmarkResult = null
                }
            },
            icon = {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Benchmark: ${model.name}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isBenchmarking) {
                        CircularProgressIndicator(modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Benchmarking on ${model.selectedAccelerator.label}...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Testing Time to First Token & token decode throughput",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val result = activeBenchmarkResult
                        if (result != null) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    BenchmarkStatRow(label = "Hardware Accelerator", value = result.accelerator.label)
                                    BenchmarkStatRow(label = "Time to First Token (TTFT)", value = "${result.ttftMs} ms")
                                    BenchmarkStatRow(label = "Prefill Throughput", value = String.format(java.util.Locale.US, "%.1f tokens/s", result.prefillTokensPerSec))
                                    BenchmarkStatRow(label = "Generation Throughput", value = String.format(java.util.Locale.US, "%.1f tokens/s", result.decodeTokensPerSec))
                                    BenchmarkStatRow(label = "Tokens Tested", value = "${result.totalTokens} tokens")
                                }
                            }
                        } else {
                            Text("Click 'Run Benchmark' to measure on-device throughput.")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accelerator:", style = MaterialTheme.typography.labelSmall)
                            model.supportedAccelerators.forEach { acc ->
                                FilterChip(
                                    selected = model.selectedAccelerator == acc,
                                    onClick = { viewModel.updateOfflineAccelerator(model.id, acc) },
                                    label = { Text(acc.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isBenchmarking) {
                    Button(
                        onClick = {
                            viewModel.runBenchmark(model) { result ->
                                activeBenchmarkResult = result
                            }
                        }
                    ) {
                        Text("Run Again")
                    }
                }
            },
            dismissButton = {
                if (!isBenchmarking) {
                    TextButton(onClick = {
                        benchmarkModel = null
                        activeBenchmarkResult = null
                    }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Model Details Dialog
    detailModel?.let { model ->
        AlertDialog(
            onDismissRequest = { detailModel = null },
            icon = {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(model.name, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Model ID: ${model.modelId}", style = MaterialTheme.typography.bodySmall)
                    Text("File: ${model.modelFile}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${model.formattedSize}", style = MaterialTheme.typography.bodySmall)
                    Text("Context Window: ${model.contextLength}", style = MaterialTheme.typography.bodySmall)
                    Text("Accelerators: ${model.supportedAccelerators.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall)
                    Text("Runtime: Google AI Edge LiteRT-LM", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Model License: ${model.licenseUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            try { uriHandler.openUri(model.licenseUrl) } catch (e: Exception) {}
                        }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { detailModel = null }) {
                    Text("Done")
                }
            }
        )
    }

    // Import progress dialog
    if (importProgress >= 0f) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importing Model File...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { importProgress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Text(
                        text = "${(importProgress * 100).toInt()}% copied to on-device storage",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    // Delete confirmation dialog
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("Delete ${model.name}?") },
            text = {
                Text("This will remove the downloaded weights (${model.formattedSize}) from your device and free up storage space.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOfflineModel(model)
                        modelToDelete = null
                        storageInfo = viewModel.getStorageInfo()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    var hfTokenText by remember { mutableStateOf(viewModel.getHfToken() ?: "") }

    // Hugging Face import & Access Token dialog
    if (showHfDialog) {
        AlertDialog(
            onDismissRequest = { showHfDialog = false },
            title = { Text("Hugging Face Hub & Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Hugging Face Access Token (Optional / For Gated Models):",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = hfTokenText,
                        onValueChange = {
                            hfTokenText = it
                            viewModel.setHfToken(it)
                        },
                        placeholder = { Text("hf_xxxxxxxxxxxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(
                        "Required to download gated Google models (e.g. Gemma 3n). Saved securely on device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Import New Model from Repo or URL:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = hfInputText,
                        onValueChange = { hfInputText = it },
                        placeholder = { Text("e.g. litert-community/Qwen2.5-1.5B-Instruct") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                    Text(
                        "Any model compiled for LiteRT-LM (.litertlm / .task) will be added to your catalog.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = hfInputText.trim()
                        if (input.isNotEmpty()) {
                            viewModel.importFromHuggingFace(
                                urlOrId = input,
                                onSuccess = {
                                    showHfDialog = false
                                    hfInputText = ""
                                    Toast.makeText(context, "Added ${it.name} to models catalog!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else {
                            showHfDialog = false
                            Toast.makeText(context, "Settings updated!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (hfInputText.trim().isNotEmpty()) "Add Model" else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHfDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BenchmarkStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun StorageStatusCard(
    storageInfo: com.example.data.offline.StorageInfo,
    onImportClick: () -> Unit,
    onHfClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Storage & Hardware",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${storageInfo.formattedFree} free • Models using ${storageInfo.formattedUsed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File", fontSize = 12.5.sp)
                }

                OutlinedButton(
                    onClick = onHfClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hugging Face", fontSize = 12.5.sp)
                }
            }
        }
    }
}

/**
 * Google AI Edge Gallery model card implementation
 */
@Composable
private fun EdgeGalleryModelCard(
    model: OfflineModel,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onSelectAccelerator: (Accelerator) -> Unit,
    onBenchmark: () -> Unit,
    onStartChat: () -> Unit,
    onShowDetails: () -> Unit,
    onOpenLicense: () -> Unit,
    onOpenHfDialog: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    val isDownloaded = model.status == OfflineModelStatus.DOWNLOADED || model.isImported
    val isDownloading = model.status == OfflineModelStatus.DOWNLOADING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                else Modifier
            )
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Name + 3 Dots + Expand Chevron
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (model.isReasoningModel) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                "Thinking",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Model Specifications") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShowDetails()
                            }
                        )
                        if (isDownloaded) {
                            DropdownMenuItem(
                                text = { Text("Run Benchmark") },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onBenchmark()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Downloaded Model") },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Size row with icon (e.g. 2.6 GB)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = model.formattedSize,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = model.selectedAccelerator.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // "Learn more and see model license" link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenLicense() }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "Learn more and see model license",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open license",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Model description
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hardware accelerator chips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Hardware:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        model.supportedAccelerators.forEach { acc ->
                            val selected = model.selectedAccelerator == acc
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectAccelerator(acc) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (acc == Accelerator.NPU) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }
                                        Text(acc.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // Error Message (if download or loading failed)
            if (model.status == OfflineModelStatus.FAILED && !model.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = model.lastError ?: "Download failed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (model.lastError?.contains("Hugging Face Access Token", ignoreCase = true) == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onOpenHfDialog,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.End).height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Enter HF Token", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Section (Edge Gallery format)
            when {
                isDownloading -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Downloading: ${(model.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "${model.downloadSpeed ?: ""} • ${model.remainingTime ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { model.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = onCancel,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }
                    }
                }

                isDownloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onBenchmark,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Benchmark", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = onStartChat,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Try it", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                else -> {
                    // Available for download: Edge Gallery full-width dark/tonal Download button
                    FilledTonalButton(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (model.status == OfflineModelStatus.FAILED) "Retry Download" else "Download",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
