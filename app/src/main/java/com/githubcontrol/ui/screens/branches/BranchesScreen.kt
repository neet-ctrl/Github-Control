package com.githubcontrol.ui.screens.branches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.viewmodel.BranchesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    vm: BranchesViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()

    var createDialog     by remember { mutableStateOf(false) }
    var importDialog     by remember { mutableStateOf(false) }
    var renameDialog     by remember { mutableStateOf<String?>(null) }
    var deleteDialog     by remember { mutableStateOf<String?>(null) }
    var setDefaultDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Branches") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { importDialog = true }) {
                        Icon(Icons.Filled.Download, "Import branch from another repo")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { createDialog = true }) {
                Icon(Icons.Filled.Add, "Create branch")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            // ── Global import status banner (survives navigation) ──────────
            if (s.importing || s.importPaused) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (s.importPaused)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (s.importPaused) {
                                Icon(Icons.Filled.WifiOff, null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp))
                            } else {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                s.importProgress ?: if (s.importPaused) "Waiting for network…" else "Importing…",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.cancelImport() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (!s.importPaused) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Import continues even if you navigate to other screens.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Import is paused. It will resume automatically when the network reconnects.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ── Import result banners ──────────────────────────────────────
            s.importResult?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearMessages() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            s.importError?.let { err ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.clearMessages() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ── Local error ────────────────────────────────────────────────
            s.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
            s.message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (s.loading) {
                LoadingIndicator()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(s.items, key = { it.name }) { branch ->
                        GhCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AccountTree, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(branch.name, fontWeight = FontWeight.SemiBold)
                                        if (branch.name == s.defaultBranch)
                                            GhBadge("default", MaterialTheme.colorScheme.primary)
                                        if (branch.protected == true)
                                            GhBadge("protected", MaterialTheme.colorScheme.tertiary)
                                    }
                                    branch.commit?.sha?.let { sha ->
                                        Text(
                                            sha.take(7),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Actions menu
                                var menuOpen by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Filled.MoreVert, null)
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                            onClick = { menuOpen = false; renameDialog = branch.name }
                                        )
                                        if (branch.name != s.defaultBranch) {
                                            DropdownMenuItem(
                                                text = { Text("Set as default") },
                                                leadingIcon = { Icon(Icons.Filled.Star, null) },
                                                onClick = { menuOpen = false; setDefaultDialog = branch.name }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { menuOpen = false; deleteDialog = branch.name }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Create branch dialog ───────────────────────────────────────────────
    if (createDialog) {
        var newName by remember { mutableStateOf("") }
        var fromBranch by remember { mutableStateOf(s.defaultBranch) }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("New branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Branch name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = fromBranch,
                        onValueChange = { fromBranch = it },
                        label = { Text("From branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.create(owner, name, newName.trim(), fromBranch.trim())
                        createDialog = false
                    },
                    enabled = newName.isNotBlank() && fromBranch.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Import dialog ──────────────────────────────────────────────────────
    if (importDialog) {
        var sourceUrl    by remember { mutableStateOf("") }
        var sourceBranch by remember { mutableStateOf("main") }
        var newBranch    by remember { mutableStateOf("") }
        val parsed = remember(sourceUrl) { vm.parseRepoUrl(sourceUrl) }
        val urlValid = sourceUrl.isBlank() || parsed != null

        AlertDialog(
            onDismissRequest = { importDialog = false },
            title = { Text("Import branch from another repo") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Import runs in the background — you can navigate away and it will continue automatically. " +
                                "If your connection drops, it pauses and retries when reconnected.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = sourceUrl,
                        onValueChange = { sourceUrl = it },
                        label = { Text("Source repository URL") },
                        placeholder = { Text("https://github.com/owner/repo  or  owner/repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = sourceUrl.isNotBlank() && !urlValid,
                        supportingText = {
                            when {
                                sourceUrl.isBlank() -> {}
                                parsed != null -> Text(
                                    "✓ ${parsed.first} / ${parsed.second}",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                else -> Text("Invalid URL format", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = sourceBranch,
                        onValueChange = { sourceBranch = it },
                        label = { Text("Branch in source repo") },
                        placeholder = { Text("main") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBranch,
                        onValueChange = { newBranch = it },
                        label = { Text("New branch name in this repo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.importBranch(owner, name, sourceUrl, sourceBranch, newBranch.trim())
                        importDialog = false
                    },
                    enabled = parsed != null && sourceBranch.isNotBlank() && newBranch.isNotBlank()
                ) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importDialog = false }) { Text("Cancel") } }
        )
    }

    // ── Rename dialog ──────────────────────────────────────────────────────
    renameDialog?.let { oldName ->
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Rename branch") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name for '$oldName'") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.rename(owner, name, oldName, newName.trim()); renameDialog = null },
                    enabled = newName.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Cancel") } }
        )
    }

    // ── Delete dialog ──────────────────────────────────────────────────────
    deleteDialog?.let { branchName ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text("Delete branch") },
            text = { Text("Permanently delete '$branchName'? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(owner, name, branchName); deleteDialog = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = null }) { Text("Cancel") } }
        )
    }

    // ── Set default dialog ─────────────────────────────────────────────────
    setDefaultDialog?.let { branchName ->
        AlertDialog(
            onDismissRequest = { setDefaultDialog = null },
            title = { Text("Set default branch") },
            text = { Text("Set '$branchName' as the default branch?") },
            confirmButton = {
                Button(onClick = { vm.setDefault(owner, name, branchName); setDefaultDialog = null }) {
                    Text("Set default")
                }
            },
            dismissButton = { TextButton(onClick = { setDefaultDialog = null }) { Text("Cancel") } }
        )
    }
}
