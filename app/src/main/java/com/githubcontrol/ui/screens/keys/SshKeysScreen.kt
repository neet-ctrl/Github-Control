package com.githubcontrol.ui.screens.keys

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhSshKey
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SshKeysState(
    val loading: Boolean = true,
    val keys: List<GhSshKey> = emptyList(),
    val error: String? = null,
    val saving: Boolean = false,
    val bulkDeleting: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val successMessage: String? = null
)

@HiltViewModel
class SshKeysViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    val state = MutableStateFlow(SshKeysState())

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                state.value = state.value.copy(loading = false, keys = repo.sshKeys())
            } catch (t: Throwable) {
                state.value = state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun add(title: String, body: String, onDone: () -> Unit) {
        viewModelScope.launch {
            state.value = state.value.copy(saving = true, error = null)
            try {
                repo.addSshKey(title.trim(), body.trim())
                Logger.i("SshKeys", "added key '${title.trim()}'")
                reload(); onDone()
            } catch (t: Throwable) {
                Logger.e("SshKeys", "add failed", t)
                state.value = state.value.copy(saving = false, error = t.message)
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            runCatching {
                repo.deleteSshKey(id)
                Logger.i("SshKeys", "deleted key #$id")
            }.onFailure { Logger.e("SshKeys", "delete failed", it) }
            reload()
        }
    }

    // ---- Selection mode ----

    fun toggleSelectionMode() {
        state.value = state.value.copy(
            selectionMode = !state.value.selectionMode,
            selectedIds = emptySet()
        )
    }

    fun toggleSelection(id: Long) {
        val cur = state.value.selectedIds
        state.value = state.value.copy(
            selectedIds = if (id in cur) cur - id else cur + id
        )
    }

    // ---- Bulk delete helpers ----

    fun deleteSelected() {
        val ids = state.value.selectedIds.toList()
        bulkDelete(ids, "Deleted ${ids.size} key(s)")
    }

    fun deleteNewest(n: Int) {
        val ids = state.value.keys
            .sortedByDescending { parseDate(it.createdAt) }
            .take(n)
            .map { it.id }
        bulkDelete(ids, "Deleted $n newest key(s)")
    }

    fun deleteOldest(n: Int) {
        val ids = state.value.keys
            .sortedBy { parseDate(it.createdAt) }
            .take(n)
            .map { it.id }
        bulkDelete(ids, "Deleted $n oldest key(s)")
    }

    fun deleteByDateRange(fromDate: String?, toDate: String?) {
        val from = fromDate?.let { runCatching { Instant.parse("${it}T00:00:00Z") }.getOrNull() }
        val to   = toDate?.let   { runCatching { Instant.parse("${it}T23:59:59Z") }.getOrNull() }
        val ids = state.value.keys.filter { key ->
            val t = parseDate(key.createdAt) ?: return@filter false
            val afterFrom = from == null || !t.isBefore(from)
            val beforeTo  = to   == null || !t.isAfter(to)
            afterFrom && beforeTo
        }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} key(s) in date range")
    }

    fun deleteAll() {
        val ids = state.value.keys.map { it.id }
        bulkDelete(ids, "Deleted all ${ids.size} key(s)")
    }

    fun deleteReadOnly() {
        val ids = state.value.keys.filter { it.readOnly }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} read-only key(s)")
    }

    fun deleteUnverified() {
        val ids = state.value.keys.filter { !it.verified }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} unverified key(s)")
    }

    fun deleteVerified() {
        val ids = state.value.keys.filter { it.verified }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} verified key(s)")
    }

    private fun parseDate(raw: String): Instant? =
        runCatching { Instant.parse(raw) }.getOrNull()

    private fun bulkDelete(ids: List<Long>, successMsg: String) {
        if (ids.isEmpty()) {
            state.value = state.value.copy(successMessage = "No keys matched the criteria")
            return
        }
        val idsSet = ids.toSet()
        viewModelScope.launch {
            state.value = state.value.copy(bulkDeleting = true, error = null)
            var deleted = 0
            val errors = mutableListOf<String>()
            for (id in ids) {
                runCatching {
                    repo.deleteSshKey(id)
                    Logger.i("SshKeys", "bulk-deleted key #$id")
                    deleted++
                }.onFailure {
                    Logger.e("SshKeys", "bulk-delete #$id failed", it)
                    errors.add(it.message ?: "error")
                }
            }
            state.value = state.value.copy(
                bulkDeleting = false,
                keys = state.value.keys.filter { it.id !in idsSet },
                selectionMode = false,
                selectedIds = emptySet(),
                successMessage = if (errors.isEmpty()) successMsg
                    else "$deleted deleted, ${errors.size} failed",
                error = errors.firstOrNull()
            )
        }
    }

    fun clearMessages() {
        state.value = state.value.copy(error = null, successMessage = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeysScreen(onBack: () -> Unit, vm: SshKeysViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    var showAdd          by remember { mutableStateOf(false) }
    var titleField       by remember { mutableStateOf("") }
    var keyField         by remember { mutableStateOf("") }
    var showBulkMenu     by remember { mutableStateOf(false) }

    // N-based dialogs
    var showDeleteNewest     by remember { mutableStateOf(false) }
    var showDeleteOldest     by remember { mutableStateOf(false) }
    var showDeleteByDate     by remember { mutableStateOf(false) }

    // Confirmation dialogs
    var showConfirmAll        by remember { mutableStateOf(false) }
    var showConfirmSelected   by remember { mutableStateOf(false) }
    var showConfirmReadOnly   by remember { mutableStateOf(false) }
    var showConfirmUnverified by remember { mutableStateOf(false) }
    var showConfirmVerified   by remember { mutableStateOf(false) }

    // Individual delete
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(s.successMessage) {
        if (s.successMessage != null) { delay(3500); vm.clearMessages() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    if (s.selectionMode) "${s.selectedIds.size} selected"
                    else "SSH keys (${s.keys.size})"
                )
            },
            navigationIcon = {
                if (s.selectionMode) {
                    IconButton(onClick = { vm.toggleSelectionMode() }) {
                        Icon(Icons.Filled.Close, "Cancel selection")
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            },
            actions = {
                if (s.selectionMode) {
                    IconButton(
                        onClick = { showConfirmSelected = true },
                        enabled = s.selectedIds.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            "Delete selected",
                            tint = if (s.selectedIds.isNotEmpty())
                                MaterialTheme.colorScheme.error
                            else LocalContentColor.current
                        )
                    }
                } else {
                    // Add key button
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, "Add SSH key")
                    }
                    // Bulk delete menu
                    Box {
                        IconButton(onClick = { showBulkMenu = true }) {
                            Icon(Icons.Filled.DeleteSweep, "Bulk delete")
                        }
                        DropdownMenu(
                            expanded = showBulkMenu,
                            onDismissRequest = { showBulkMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete N newest") },
                                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                                onClick = { showBulkMenu = false; showDeleteNewest = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete N oldest") },
                                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                                onClick = { showBulkMenu = false; showDeleteOldest = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete by date range") },
                                leadingIcon = { Icon(Icons.Filled.DateRange, null) },
                                onClick = { showBulkMenu = false; showDeleteByDate = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete by selection") },
                                leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                onClick = { showBulkMenu = false; vm.toggleSelectionMode() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete read-only keys") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                onClick = { showBulkMenu = false; showConfirmReadOnly = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete unverified keys") },
                                leadingIcon = { Icon(Icons.Filled.GppBad, null) },
                                onClick = { showBulkMenu = false; showConfirmUnverified = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete verified keys") },
                                leadingIcon = { Icon(Icons.Filled.Verified, null) },
                                onClick = { showBulkMenu = false; showConfirmVerified = true }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete all keys",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.DeleteForever,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { showBulkMenu = false; showConfirmAll = true }
                            )
                        }
                    }
                }
            }
        )
    }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Progress / messages
            if (s.loading || s.bulkDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())

            s.successMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            s.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (!s.loading && s.keys.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Key,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No SSH keys on this account.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(s.keys, key = { it.id }) { k ->
                        SshKeyCard(
                            key = k,
                            selectionMode = s.selectionMode,
                            selected = k.id in s.selectedIds,
                            onToggleSelect = { vm.toggleSelection(k.id) },
                            onDelete = { deleteTarget = k.id }
                        )
                    }
                }
            }

            EmbeddedTerminal(section = "SshKeys")
        }
    }

    // ---- Individual delete confirm ----
    deleteTarget?.let { tid ->
        val key = s.keys.find { it.id == tid }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete SSH key") },
            text = { Text("Delete \"${key?.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(tid); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // ---- N newest ----
    if (showDeleteNewest) {
        SshNKeysDialog(
            title = "Delete N newest",
            description = "How many of the most recently added keys should be deleted?",
            onConfirm = { n -> vm.deleteNewest(n); showDeleteNewest = false },
            onDismiss = { showDeleteNewest = false }
        )
    }

    // ---- N oldest ----
    if (showDeleteOldest) {
        SshNKeysDialog(
            title = "Delete N oldest",
            description = "How many of the oldest keys should be deleted?",
            onConfirm = { n -> vm.deleteOldest(n); showDeleteOldest = false },
            onDismiss = { showDeleteOldest = false }
        )
    }

    // ---- Date range ----
    if (showDeleteByDate) {
        SshDateRangeDialog(
            onConfirm = { from, to -> vm.deleteByDateRange(from, to); showDeleteByDate = false },
            onDismiss = { showDeleteByDate = false }
        )
    }

    // ---- Confirm: delete all ----
    if (showConfirmAll) {
        AlertDialog(
            onDismissRequest = { showConfirmAll = false },
            title = { Text("Delete all SSH keys") },
            text = { Text("This will permanently delete all ${s.keys.size} key(s). You will lose SSH access until you add new keys.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); showConfirmAll = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmAll = false }) { Text("Cancel") } }
        )
    }

    // ---- Confirm: delete selected ----
    if (showConfirmSelected) {
        AlertDialog(
            onDismissRequest = { showConfirmSelected = false },
            title = { Text("Delete selected keys") },
            text = { Text("Delete ${s.selectedIds.size} selected key(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(); showConfirmSelected = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmSelected = false }) { Text("Cancel") } }
        )
    }

    // ---- Confirm: delete read-only ----
    if (showConfirmReadOnly) {
        val count = s.keys.count { it.readOnly }
        AlertDialog(
            onDismissRequest = { showConfirmReadOnly = false },
            title = { Text("Delete read-only keys") },
            text = { Text("Delete $count read-only key(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteReadOnly(); showConfirmReadOnly = false }) {
                    Text("Delete $count", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmReadOnly = false }) { Text("Cancel") } }
        )
    }

    // ---- Confirm: delete unverified ----
    if (showConfirmUnverified) {
        val count = s.keys.count { !it.verified }
        AlertDialog(
            onDismissRequest = { showConfirmUnverified = false },
            title = { Text("Delete unverified keys") },
            text = { Text("Delete $count unverified key(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteUnverified(); showConfirmUnverified = false }) {
                    Text("Delete $count", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmUnverified = false }) { Text("Cancel") } }
        )
    }

    // ---- Confirm: delete verified ----
    if (showConfirmVerified) {
        val count = s.keys.count { it.verified }
        AlertDialog(
            onDismissRequest = { showConfirmVerified = false },
            title = { Text("Delete verified keys") },
            text = { Text("Delete $count verified key(s)? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteVerified(); showConfirmVerified = false }) {
                    Text("Delete $count", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmVerified = false }) { Text("Cancel") } }
        )
    }

    // ---- Add key dialog ----
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; titleField = ""; keyField = "" },
            title = { Text("Add SSH key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        titleField, { titleField = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        keyField, { keyField = it },
                        label = { Text("Public key (ssh-rsa AAAAB3…)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = titleField.isNotBlank() && keyField.isNotBlank() && !s.saving,
                    onClick = {
                        vm.add(titleField, keyField) {
                            showAdd = false; titleField = ""; keyField = ""
                        }
                    }
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SshKeyCard(
    key: GhSshKey,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit
) {
    GhCard {
        Row(
            modifier = if (selectionMode) {
                Modifier.toggleable(value = selected, onValueChange = { onToggleSelect() }, role = Role.Checkbox)
            } else Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            } else {
                Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(key.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "added ${key.createdAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    key.key.take(48) + if (key.key.length > 48) "…" else "",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (key.verified) GhBadge("verified", MaterialTheme.colorScheme.primary)
                    if (key.readOnly) GhBadge("read-only")
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "Delete key", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun SshNKeysDialog(
    title: String,
    description: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var nText by remember { mutableStateOf("") }
    val n = nText.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description)
                OutlinedTextField(
                    value = nText,
                    onValueChange = { nText = it.filter { c -> c.isDigit() } },
                    label = { Text("Count") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (n > 0) onConfirm(n) }, enabled = n > 0) {
                Text(
                    "Delete $n",
                    color = if (n > 0) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SshDateRangeDialog(
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fromDate by remember { mutableStateOf("") }
    var toDate   by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete by date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Keys added within this range will be deleted. Leave a field blank to skip that bound.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fromDate,
                    onValueChange = { fromDate = it },
                    label = { Text("From date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toDate,
                    onValueChange = { toDate = it },
                    label = { Text("To date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        fromDate.takeIf { it.isNotBlank() },
                        toDate.takeIf { it.isNotBlank() }
                    )
                }
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
