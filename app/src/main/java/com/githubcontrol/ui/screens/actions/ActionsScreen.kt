package com.githubcontrol.ui.screens.actions

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.api.GhWorkflowRun
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.ActionsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    vm: ActionsViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()

    // Dispatch dialog
    var dispatchTarget by remember { mutableStateOf<Long?>(null) }
    var ref by remember { mutableStateOf("main") }

    // Bulk-delete menu
    var showBulkMenu by remember { mutableStateOf(false) }

    // N-based dialogs
    var showDeleteNewest  by remember { mutableStateOf(false) }
    var showDeleteOldest  by remember { mutableStateOf(false) }
    var showDeleteByDate  by remember { mutableStateOf(false) }

    // Conclusion-based dialog
    var showDeleteConclusion by remember { mutableStateOf(false) }

    // Confirm dialogs
    var showConfirmAll        by remember { mutableStateOf(false) }
    var showConfirmSelected   by remember { mutableStateOf(false) }

    // Individual delete confirm
    var deleteTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(s.message) {
        if (s.message != null) { delay(3500); vm.clearMessages() }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(
                    if (s.selectionMode) "${s.selectedIds.size} selected"
                    else "Actions · $name"
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
                    // Reload
                    IconButton(onClick = { vm.reload() }, enabled = !s.loading) {
                        Icon(Icons.Filled.Refresh, "Reload")
                    }
                    // Bulk-delete menu
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
                                text = { Text("Delete by conclusion") },
                                leadingIcon = { Icon(Icons.Filled.FilterList, null) },
                                onClick = { showBulkMenu = false; showDeleteConclusion = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete by selection") },
                                leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                onClick = { showBulkMenu = false; vm.toggleSelectionMode() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete all completed",
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
            if (s.loading || s.bulkDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())

            // Success banner
            s.message?.let { msg ->
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

            // Error banner
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

            if (!s.loading && s.workflows.isEmpty() && s.runs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "No workflows or runs found.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // ---- Workflows section ----
                    if (s.workflows.isNotEmpty() && !s.selectionMode) {
                        item {
                            Text(
                                "Workflows (${s.workflows.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(s.workflows, key = { "wf_${it.id}" }) { w ->
                            GhCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Bolt,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 10.dp)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(w.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            w.path,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        GhBadge(
                                            w.state,
                                            if (w.state == "active") MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                    IconButton(onClick = { dispatchTarget = w.id }) {
                                        Icon(Icons.Filled.PlayArrow, "Dispatch", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Runs (${s.runs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    // ---- Runs section ----
                    items(s.runs, key = { it.id }) { r ->
                        RunCard(
                            run = r,
                            selectionMode = s.selectionMode,
                            selected = r.id in s.selectedIds,
                            inFlight = r.id in s.actionInFlight,
                            onToggleSelect = { vm.toggleSelection(r.id) },
                            onDelete = { deleteTarget = r.id },
                            onCancel = { vm.cancelRun(r.id) },
                            onRerun  = { vm.rerunRun(r.id) }
                        )
                    }
                }
            }

            EmbeddedTerminal(section = "Actions")
        }
    }

    // ---- Dispatch workflow ----
    dispatchTarget?.let { wId ->
        AlertDialog(
            onDismissRequest = { dispatchTarget = null },
            icon = { Icon(Icons.Filled.PlayArrow, null) },
            title = { Text("Dispatch workflow") },
            text = {
                OutlinedTextField(
                    ref, { ref = it },
                    label = { Text("Ref / branch") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dispatch(owner, name, wId, ref)
                    dispatchTarget = null
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { dispatchTarget = null }) { Text("Cancel") } }
        )
    }

    // ---- Individual delete confirm ----
    deleteTarget?.let { rid ->
        val run = s.runs.find { it.id == rid }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete run") },
            text = {
                Text(
                    "Delete run #${run?.runNumber} (${run?.name ?: run?.headBranch ?: "—"})? " +
                    if (run?.status != "completed") "\n\nNote: only completed runs can be deleted." else "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteRun(rid); deleteTarget = null },
                    enabled = run?.status == "completed"
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // ---- N newest ----
    if (showDeleteNewest) {
        ActionsNDialog(
            title = "Delete N newest",
            description = "How many of the most recently created completed runs should be deleted?",
            onConfirm = { n -> vm.deleteNewest(n); showDeleteNewest = false },
            onDismiss = { showDeleteNewest = false }
        )
    }

    // ---- N oldest ----
    if (showDeleteOldest) {
        ActionsNDialog(
            title = "Delete N oldest",
            description = "How many of the oldest completed runs should be deleted?",
            onConfirm = { n -> vm.deleteOldest(n); showDeleteOldest = false },
            onDismiss = { showDeleteOldest = false }
        )
    }

    // ---- Date range ----
    if (showDeleteByDate) {
        ActionsDateRangeDialog(
            onConfirm = { from, to -> vm.deleteByDateRange(from, to); showDeleteByDate = false },
            onDismiss = { showDeleteByDate = false }
        )
    }

    // ---- Delete by conclusion ----
    if (showDeleteConclusion) {
        ActionsConclusionDialog(
            conclusions = listOf("success", "failure", "cancelled", "skipped", "timed_out", "neutral", "action_required", "stale"),
            runs = s.runs,
            onConfirm = { c -> vm.deleteByConclusion(c); showDeleteConclusion = false },
            onDismiss = { showDeleteConclusion = false }
        )
    }

    // ---- Confirm: delete all ----
    if (showConfirmAll) {
        val count = s.runs.count { it.status == "completed" }
        AlertDialog(
            onDismissRequest = { showConfirmAll = false },
            title = { Text("Delete all completed runs") },
            text = { Text("This will permanently delete all $count completed run(s). In-progress runs are not affected.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAll(); showConfirmAll = false }) {
                    Text("Delete $count", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmAll = false }) { Text("Cancel") } }
        )
    }

    // ---- Confirm: delete selected ----
    if (showConfirmSelected) {
        AlertDialog(
            onDismissRequest = { showConfirmSelected = false },
            title = { Text("Delete selected runs") },
            text = { Text("Delete ${s.selectedIds.size} selected run(s)? Only completed runs will be deleted.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelected(); showConfirmSelected = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirmSelected = false }) { Text("Cancel") } }
        )
    }
}

// ---- Run card ----

@Composable
private fun RunCard(
    run: GhWorkflowRun,
    selectionMode: Boolean,
    selected: Boolean,
    inFlight: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit
) {
    val conclusionColor = when (run.conclusion) {
        "success"  -> Color(0xFF3FB950)
        "failure"  -> MaterialTheme.colorScheme.error
        "cancelled" -> MaterialTheme.colorScheme.tertiary
        "skipped"  -> MaterialTheme.colorScheme.onSurfaceVariant
        "timed_out" -> Color(0xFFD29922)
        else       -> MaterialTheme.colorScheme.primary
    }

    GhCard {
        Column {
            Row(
                modifier = if (selectionMode)
                    Modifier.toggleable(value = selected, onValueChange = { onToggleSelect() }, role = Role.Checkbox)
                else Modifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        run.name ?: "Run #${run.runNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            run.headBranch?.let { append(it); append(" · ") }
                            append(run.event)
                            append(" · ")
                            append(RelativeTime.ago(run.createdAt))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GhBadge(run.status, MaterialTheme.colorScheme.surfaceVariant)
                        run.conclusion?.let { GhBadge(it, conclusionColor) }
                    }
                }
                if (inFlight) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(4.dp))
                }
            }

            if (!selectionMode && !inFlight) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Cancel — only for active runs
                    if (run.status in setOf("in_progress", "queued", "waiting", "requested", "pending")) {
                        TextButton(onClick = onCancel) {
                            Icon(
                                Icons.Filled.Stop,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // Re-run — only for completed runs
                    if (run.status == "completed") {
                        TextButton(onClick = onRerun) {
                            Icon(
                                Icons.Filled.Replay,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Re-run", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // Delete — only for completed runs
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Filled.Delete,
                            "Delete run",
                            tint = if (run.status == "completed") MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---- Helper dialogs ----

@Composable
private fun ActionsNDialog(
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
                Text("Delete $n", color = if (n > 0) MaterialTheme.colorScheme.error else LocalContentColor.current)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActionsDateRangeDialog(
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fromText by remember { mutableStateOf("") }
    var toText   by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete by date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete completed runs created within this range (YYYY-MM-DD). Either bound can be left blank.")
                OutlinedTextField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = { Text("From (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = { Text("To (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(fromText.ifBlank { null }, toText.ifBlank { null }) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ActionsConclusionDialog(
    conclusions: List<String>,
    runs: List<GhWorkflowRun>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(conclusions.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete by conclusion") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Choose a conclusion — all matching completed runs will be deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                conclusions.forEach { c ->
                    val count = runs.count { it.status == "completed" && it.conclusion == c }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = c == selected, onClick = { selected = c })
                        Spacer(Modifier.width(4.dp))
                        Text(c, Modifier.weight(1f))
                        Text(
                            "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            val count = runs.count { it.status == "completed" && it.conclusion == selected }
            TextButton(onClick = { onConfirm(selected) }, enabled = count > 0) {
                Text(
                    "Delete $count",
                    color = if (count > 0) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
