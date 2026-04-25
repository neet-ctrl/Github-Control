package com.githubcontrol.ui.screens.files

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.Logger
import com.githubcontrol.viewmodel.TreeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeScreen(owner: String, name: String, ref: String, onBack: () -> Unit, onPreview: (String) -> Unit, vm: TreeViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, ref) { vm.load(owner, name, ref) }
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectionCount = s.items.count { it.selected && it.type == "blob" }
    val anySelected = selectionCount > 0
    var downloading by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                downloading = true
                runCatching { vm.downloadRepoZip(owner, name, ref.ifBlank { "HEAD" }, ctx, uri) }
                    .onFailure { Logger.e("Tree", "ZIP save failed", it) }
                downloading = false
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (anySelected) "$selectionCount selected" else "Tree") },
            navigationIcon = {
                IconButton(onClick = { if (anySelected) vm.clearSelection() else onBack() }) {
                    Icon(if (anySelected) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            },
            actions = {
                if (anySelected) {
                    IconButton(onClick = { vm.selectAllBlobs() }) { Icon(Icons.Filled.SelectAll, null) }
                    IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Filled.Delete, null) }
                }
                IconButton(
                    onClick = { saveZip.launch("${name}-${ref.ifBlank { "HEAD" }}.zip") },
                    enabled = !downloading
                ) { Icon(Icons.Filled.Archive, contentDescription = "Download as ZIP") }
            }
        )
    }) { pad ->
        // Render loading and ready states as siblings (if/else) instead of an
        // early `return@Scaffold` after emitting the spinner — the early-return
        // pattern reliably crashes Compose with a "Start/end imbalance" runtime
        // error on Android 15 once the loading flag flips. This was the cause
        // of the Tree-screen crash report.
        if (s.loading) {
            Column(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
        } else Column(Modifier.padding(pad).fillMaxSize()) {
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (s.truncated) Text("Tree truncated by GitHub (very large repo)", color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(8.dp))
            if (downloading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val visible = s.items.filter { it.visible }
            LazyColumn(Modifier.weight(1f)) {
                items(visible, key = { it.path }) { node ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (anySelected && node.type == "blob") vm.toggleSelect(node.path)
                                else if (node.type == "tree") vm.toggle(node.path)
                                else onPreview(Routes.preview(owner, name, node.path, ref))
                            }
                            .padding(horizontal = (12 + node.depth * 16).dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (node.type == "blob") {
                            IconButton(onClick = { vm.toggleSelect(node.path) }, modifier = Modifier.size(28.dp)) {
                                Icon(if (node.selected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null)
                            }
                        } else {
                            Spacer(Modifier.width(28.dp))
                        }
                        if (node.type == "tree") Icon(if (node.expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, null)
                        else Spacer(Modifier.width(24.dp))
                        Icon(if (node.type == "tree") Icons.Filled.Folder else Icons.Filled.InsertDriveFile, null,
                            tint = if (node.type == "tree") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(node.name, modifier = Modifier.weight(1f))
                        if (node.type == "blob")
                            Text(com.githubcontrol.utils.ByteFormat.human(node.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Divider()
                }
            }
            EmbeddedTerminal(section = "Tree")
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete $selectionCount file(s)?") },
            text = { Text("This will commit a deletion to the current branch. This action cannot be undone from the app.") },
            confirmButton = { TextButton(onClick = {
                deleteConfirm = false
                scope.launch { vm.deleteSelected(owner, name, ref.ifBlank { "HEAD" }) }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
