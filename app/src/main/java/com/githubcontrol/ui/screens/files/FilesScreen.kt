package com.githubcontrol.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.components.SwipeRow
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.ByteFormat
import com.githubcontrol.viewmodel.FilesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    owner: String, name: String, path: String, ref: String,
    onBack: () -> Unit, onNavigate: (String) -> Unit,
    vm: FilesViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name, path, ref) { vm.load(owner, name, path, ref) }
    val s by vm.state.collectAsState()
    var showBranches by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<com.githubcontrol.data.api.GhContent?>(null) }
    var showFolderDelete by remember { mutableStateOf<com.githubcontrol.data.api.GhContent?>(null) }
    var showRename by remember { mutableStateOf<com.githubcontrol.data.api.GhContent?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (path.isBlank()) name else "$name/$path", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    AssistChip(onClick = { showBranches = true }, label = { Text(s.ref.ifBlank { "main" }) }, leadingIcon = { Icon(Icons.Filled.CallSplit, null) })
                    IconButton(onClick = { vm.toggleMultiSelect() }) { Icon(if (s.multiSelect) Icons.Filled.Close else Icons.Filled.Checklist, null) }
                    IconButton(onClick = { onNavigate(Routes.upload(owner, name, path, s.ref)) }) { Icon(Icons.Filled.Upload, null) }
                }
            )
        },
        bottomBar = {
            if (s.multiSelect && s.selection.isNotEmpty()) {
                BottomAppBar(actions = {
                    Text("${s.selection.size} selected", modifier = Modifier.padding(start = 16.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { bulkDelete = true }) { Icon(Icons.Filled.Delete, null) }
                })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.loading) {
                LoadingIndicator()
            } else {
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (path.isNotBlank()) {
                        item {
                            GhCard(onClick = {
                                val parent = path.substringBeforeLast('/', "")
                                onNavigate(Routes.files(owner, name, parent, s.ref))
                            }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("..")
                                }
                            }
                        }
                    }
                    items(s.items, key = { it.path }) { item ->
                val selected = s.selection.contains(item.path)
                SwipeRow(
                    onDelete = {
                        if (item.type == "file") showDelete = item
                        else if (item.type == "dir") showFolderDelete = item
                    },
                    onArchive = { if (item.type == "file") showRename = item },
                    rightLabel = "Delete", leftLabel = "Rename"
                ) {
                    GhCard(onClick = {
                        if (s.multiSelect) vm.toggleSelect(item.path)
                        else if (item.type == "dir") onNavigate(Routes.files(owner, name, item.path, s.ref))
                        else onNavigate(Routes.preview(owner, name, item.path, s.ref))
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.multiSelect) Checkbox(checked = selected, onCheckedChange = { vm.toggleSelect(item.path) })
                            Icon(if (item.type == "dir") Icons.Filled.Folder else Icons.Filled.InsertDriveFile, null,
                                tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                if (item.type == "file") Text(ByteFormat.human(item.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }

    if (showBranches) {
        ModalBottomSheet(onDismissRequest = { showBranches = false }) {
            Text("Switch branch", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            s.branches.forEach { b ->
                ListItem(
                    headlineContent = { Text(b.name) },
                    leadingContent = { Icon(Icons.Filled.CallSplit, null) },
                    modifier = Modifier.clickable { vm.setRef(b.name); showBranches = false }
                )
            }
        }
    }
    showDelete?.let { item ->
        var msg by remember { mutableStateOf("Delete ${item.path}") }
        AlertDialog(
            onDismissRequest = { showDelete = null },
            title = { Text("Delete file") },
            text = { OutlinedTextField(msg, { msg = it }, label = { Text("Commit message") }) },
            confirmButton = { TextButton(onClick = { vm.deletePath(item.path, item.sha, msg) { showDelete = null } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDelete = null }) { Text("Cancel") } }
        )
    }
    showFolderDelete?.let { folder ->
        var msg by remember { mutableStateOf("Delete folder ${folder.path}") }
        AlertDialog(
            onDismissRequest = { showFolderDelete = null },
            title = { Text("Delete folder") },
            text = {
                Column {
                    Text(
                        "This removes ${folder.path} and every file inside it in a single atomic commit. This cannot be undone from the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(msg, { msg = it }, label = { Text("Commit message") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFolder(folder.path, msg) { showFolderDelete = null }
                }) { Text("Delete folder", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showFolderDelete = null }) { Text("Cancel") } }
        )
    }
    if (bulkDelete) {
        var msg by remember { mutableStateOf("Delete ${s.selection.size} files") }
        AlertDialog(
            onDismissRequest = { bulkDelete = false },
            title = { Text("Delete selected") },
            text = { OutlinedTextField(msg, { msg = it }, label = { Text("Commit message") }) },
            confirmButton = { TextButton(onClick = { vm.deleteSelected(msg) { bulkDelete = false } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { bulkDelete = false }) { Text("Cancel") } }
        )
    }
    showRename?.let { item ->
        val parent = item.path.substringBeforeLast('/', "")
        var newName by remember(item.path) { mutableStateOf(item.name) }
        var msg by remember(item.path) { mutableStateOf("Rename ${item.name}") }
        AlertDialog(
            onDismissRequest = { showRename = null },
            title = { Text("Rename file") },
            text = {
                Column {
                    OutlinedTextField(
                        newName, { newName = it },
                        label = { Text("New name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        msg, { msg = it },
                        label = { Text("Commit message") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank() && newName != item.name,
                    onClick = {
                        val target = if (parent.isBlank()) newName else "$parent/$newName"
                        vm.renamePath(item, target, msg) { showRename = null }
                    }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRename = null }) { Text("Cancel") } }
        )
    }
}
