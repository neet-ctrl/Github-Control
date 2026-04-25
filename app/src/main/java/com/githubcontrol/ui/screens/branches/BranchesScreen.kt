package com.githubcontrol.ui.screens.branches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.viewmodel.BranchesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchesScreen(owner: String, name: String, onBack: () -> Unit, vm: BranchesViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf<String?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }
    var setDefaultDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Branches") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { createDialog = true }) { Icon(Icons.Filled.Add, null) } }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.loading) {
                LoadingIndicator()
            } else {
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
                s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp)) }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(s.items, key = { it.name }) { b ->
                        val isDefault = b.name == s.defaultBranch
                        val busy = s.settingDefault == b.name
                        GhCard {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                                        if (isDefault) {
                                            Spacer(Modifier.width(6.dp))
                                            GhBadge("default", MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text(b.commit.sha.take(7), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (b.protected) GhBadge("protected", MaterialTheme.colorScheme.tertiary)
                                IconButton(
                                    onClick = { if (!isDefault) setDefaultDialog = b.name },
                                    enabled = !isDefault && !busy
                                ) {
                                    Icon(
                                        if (isDefault) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = if (isDefault) "Default branch" else "Set as default",
                                        tint = if (isDefault) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                                IconButton(onClick = { renameDialog = b.name }) { Icon(Icons.Filled.Edit, null) }
                                IconButton(
                                    onClick = { deleteDialog = b.name },
                                    enabled = !isDefault
                                ) { Icon(Icons.Filled.Delete, null) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (createDialog) {
        var newBranch by remember { mutableStateOf("") }
        var fromBranch by remember { mutableStateOf("main") }
        AlertDialog(onDismissRequest = { createDialog = false },
            title = { Text("Create branch") },
            text = {
                Column {
                    OutlinedTextField(newBranch, { newBranch = it }, label = { Text("New branch") }, singleLine = true)
                    OutlinedTextField(fromBranch, { fromBranch = it }, label = { Text("From branch") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.create(owner, name, newBranch, fromBranch); createDialog = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } }
        )
    }
    renameDialog?.let { old ->
        var newName by remember { mutableStateOf(old) }
        AlertDialog(onDismissRequest = { renameDialog = null },
            title = { Text("Rename '$old'") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.rename(owner, name, old, newName); renameDialog = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameDialog = null }) { Text("Cancel") } }
        )
    }
    deleteDialog?.let { br ->
        AlertDialog(onDismissRequest = { deleteDialog = null },
            title = { Text("Delete '$br'?") },
            confirmButton = { TextButton(onClick = { vm.delete(owner, name, br); deleteDialog = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteDialog = null }) { Text("Cancel") } }
        )
    }
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
                TextButton(onClick = {
                    vm.setDefault(owner, name, br)
                    setDefaultDialog = null
                }) { Text("Set as default") }
            },
            dismissButton = { TextButton(onClick = { setDefaultDialog = null }) { Text("Cancel") } }
        )
    }
}
