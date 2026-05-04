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

    var createDialog    by remember { mutableStateOf(false) }
    var importDialog    by remember { mutableStateOf(false) }
    var renameDialog    by remember { mutableStateOf<String?>(null) }
    var deleteDialog    by remember { mutableStateOf<String?>(null) }
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
            if (s.loading) {
                LoadingIndicator()
            } else {
                // Import progress banner
                if (s.importing) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            s.importProgress ?: "Importing…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                s.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                s.message?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(s.items, key = { it.name }) { b ->
                        val isDefault = b.name == s.defaultBranch
                        val busy = s.settingDefault == b.name
                        GhCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                                        if (isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            GhBadge("default", MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text(
                                        b.commit.sha.take(7),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (b.protected) GhBadge("protected", MaterialTheme.colorScheme.tertiary)
                                IconButton(
                                    onClick = { if (!isDefault) setDefaultDialog = b.name },
                                    enabled = !isDefault && !busy
                                ) {
                                    Icon(
                                        if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (isDefault) "Default branch" else "Set as default",
                                        tint = if (isDefault) MaterialTheme.colorScheme.primary
                                            else LocalContentColor.current
                                    )
                                }
                                IconButton(onClick = { renameDialog = b.name }) {
                                    Icon(Icons.Filled.Edit, null)
                                }
                                IconButton(
                                    onClick = { deleteDialog = b.name },
                                    enabled = !isDefault
                                ) {
                                    Icon(Icons.Filled.Delete, null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Create branch dialog ----
    if (createDialog) {
        var newBranch  by remember { mutableStateOf("") }
        var fromBranch by remember { mutableStateOf("main") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("Create branch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newBranch, { newBranch = it },
                        label = { Text("New branch name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        fromBranch, { fromBranch = it },
                        label = { Text("From branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newBranch.isNotBlank() && fromBranch.isNotBlank(),
                    onClick = { vm.create(owner, name, newBranch, fromBranch); createDialog = false }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } }
        )
    }

    // ---- Import branch from another repo dialog ----
    if (importDialog) {
        var repoUrl        by remember { mutableStateOf("") }
        var sourceBranch   by remember { mutableStateOf("main") }
        var newBranchName  by remember { mutableStateOf("") }
        val urlValid = vm.parseRepoUrl(repoUrl) != null
        AlertDialog(
            onDismissRequest = { importDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Download,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import branch from another repo")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Copies all files from a public repo's branch into a brand-new branch in this repo. " +
                        "Limited to 300 files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        label = { Text("Source repo URL") },
                        placeholder = { Text("https://github.com/owner/repo  or  owner/repo") },
                        singleLine = true,
                        isError = repoUrl.isNotBlank() && !urlValid,
                        supportingText = {
                            if (repoUrl.isNotBlank() && !urlValid) {
                                Text("Use https://github.com/owner/repo or owner/repo")
                            } else if (urlValid) {
                                val p = vm.parseRepoUrl(repoUrl)!!
                                Text("${p.first} / ${p.second}", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sourceBranch,
                        onValueChange = { sourceBranch = it },
                        label = { Text("Source branch") },
                        placeholder = { Text("main") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("New branch name (in this repo)") },
                        placeholder = { Text("imported-branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "This creates an orphan branch — it won't share history with the current repo. Large repos may take a while.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = urlValid && sourceBranch.isNotBlank() && newBranchName.isNotBlank() && !s.importing,
                    onClick = {
                        importDialog = false
                        vm.importBranch(owner, name, repoUrl, sourceBranch, newBranchName)
                    }
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { importDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Rename dialog ----
    renameDialog?.let { old ->
        var newName by remember { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renameDialog = null },
            title = { Text("Rename '$old'") },
            text = {
                OutlinedTextField(
                    newName, { newName = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.rename(owner, name, old, newName); renameDialog = null }) {
                    Text("Rename")
                }
            },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Cancel") } }
        )
    }

    // ---- Delete dialog ----
    deleteDialog?.let { br ->
        AlertDialog(
            onDismissRequest = { deleteDialog = null },
            title = { Text("Delete '$br'?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(owner, name, br); deleteDialog = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = null }) { Text("Cancel") } }
        )
    }

    // ---- Set default dialog ----
    setDefaultDialog?.let { br ->
        AlertDialog(
            onDismissRequest = { setDefaultDialog = null },
            icon = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Set '$br' as default?") },
            text = {
                Text(
                    "This makes '$br' the default branch for the repository. " +
                    "New clones, pull requests and the GitHub UI will use it as the base branch."
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setDefault(owner, name, br); setDefaultDialog = null }) {
                    Text("Set as default")
                }
            },
            dismissButton = { TextButton(onClick = { setDefaultDialog = null }) { Text("Cancel") } }
        )
    }
}
