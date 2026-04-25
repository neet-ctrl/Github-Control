package com.githubcontrol.ui.screens.sync

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.auth.AccountManager
import com.githubcontrol.data.db.AppDatabase
import com.githubcontrol.data.db.SyncJobEntity
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    val db: AppDatabase,
    private val accounts: AccountManager
) : androidx.lifecycle.ViewModel() {

    fun addJob(
        owner: String, repo: String, branch: String, localUri: String,
        remotePath: String, intervalMinutes: Int, onDone: (String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val accountId = accounts.activeAccount()?.id
                    ?: return@launch onDone("No active account. Sign in first.")
                db.syncJobs().insert(
                    SyncJobEntity(
                        accountId = accountId,
                        owner = owner.trim(),
                        repo = repo.trim(),
                        branch = branch.trim().ifBlank { "main" },
                        localUri = localUri,
                        remotePath = remotePath.trim().trim('/'),
                        intervalMinutes = intervalMinutes.coerceAtLeast(15),
                        enabled = true
                    )
                )
                onDone(null)
            } catch (t: Throwable) {
                onDone(t.message ?: "Couldn't save sync job.")
            }
        }
    }

    fun setEnabled(j: SyncJobEntity, enabled: Boolean) {
        viewModelScope.launch { db.syncJobs().update(j.copy(enabled = enabled)) }
    }

    fun delete(j: SyncJobEntity) {
        viewModelScope.launch { db.syncJobs().delete(j) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit, vm: SyncViewModel = hiltViewModel()) {
    val items by vm.db.syncJobs().observeAll().collectAsState(initial = emptyList())
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showHelp by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var deleteJob by remember { mutableStateOf<SyncJobEntity?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Persist read permission so the background worker can keep reading the folder.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            pendingUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync jobs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = !showHelp }) { Icon(Icons.Filled.Info, null) }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add sync job") },
                icon = { Icon(Icons.Filled.Add, null) },
                onClick = { pickFolder.launch(null) }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showHelp || items.isEmpty()) {
                GhCard {
                    Text("How sync works", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sync jobs continuously mirror a local folder on your device into a folder " +
                            "inside one of your GitHub repos. Each tick, the app walks the local tree, " +
                            "compares it to the remote branch, and commits the differences in a single " +
                            "atomic commit (added/modified files are pushed; files removed locally are " +
                            "deleted remotely inside the synced sub-tree).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap “Add sync job”, choose a local folder, then enter the destination " +
                            "owner/repo, the branch, the path inside the repo, and how often it should run. " +
                            "Use the toggle on each job to pause or resume it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (items.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No sync jobs yet. Tap “Add sync job” to set one up.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items, key = { it.id }) { j ->
                        GhCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${j.owner}/${j.repo}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Branch: ${j.branch} • every ${j.intervalMinutes} min",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        "Remote path: ${if (j.remotePath.isBlank()) "(repo root)" else j.remotePath}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "Local: ${j.localUri}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (j.lastRun > 0) {
                                        Text(
                                            "Last run: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(j.lastRun))}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Switch(
                                    checked = j.enabled,
                                    onCheckedChange = { vm.setEnabled(j, it) }
                                )
                                IconButton(onClick = { deleteJob = j }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }
            EmbeddedTerminal(section = "Sync", initiallyExpanded = false)
        }
    }

    pendingUri?.let { uri ->
        AddSyncJobDialog(
            localUri = uri,
            onDismiss = { pendingUri = null },
            onConfirm = { owner, repo, branch, remotePath, interval ->
                vm.addJob(owner, repo, branch, uri.toString(), remotePath, interval) { err ->
                    pendingUri = null
                    if (err != null) {
                        scope.launch { snackbar.showSnackbar(err) }
                    } else {
                        scope.launch { snackbar.showSnackbar("Sync job added") }
                    }
                }
            }
        )
    }

    deleteJob?.let { j ->
        AlertDialog(
            onDismissRequest = { deleteJob = null },
            title = { Text("Delete sync job?") },
            text = { Text("Stop syncing ${j.owner}/${j.repo}?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(j); deleteJob = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteJob = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddSyncJobDialog(
    localUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (owner: String, repo: String, branch: String, remotePath: String, intervalMinutes: Int) -> Unit
) {
    var owner by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var remotePath by remember { mutableStateOf("") }
    var intervalText by remember { mutableStateOf("60") }

    val canSave = owner.isNotBlank() && repo.isNotBlank() &&
        (intervalText.toIntOrNull() ?: 0) >= 15

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New sync job") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Local: $localUri",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                }
                OutlinedTextField(
                    owner, { owner = it },
                    label = { Text("Owner (user or org)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    repo, { repo = it },
                    label = { Text("Repository name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    branch, { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    remotePath, { remotePath = it },
                    label = { Text("Path inside repo (blank = root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    intervalText, { intervalText = it.filter { c -> c.isDigit() } },
                    label = { Text("Interval (minutes, min 15)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onConfirm(owner, repo, branch, remotePath, intervalText.toIntOrNull() ?: 60)
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
