package com.githubcontrol.ui.screens.releases

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.api.GhCommit
import com.githubcontrol.data.api.GhRelease
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.ReleasesViewModel
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleasesScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    vm: ReleasesViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()

    var showBulkMenu by remember { mutableStateOf(false) }
    var showDeleteNewestDialog by remember { mutableStateOf(false) }
    var showDeleteOldestDialog by remember { mutableStateOf(false) }
    var showDeleteByDateDialog by remember { mutableStateOf(false) }
    var showCommitPickerAfter by remember { mutableStateOf(false) }
    var showCommitPickerBefore by remember { mutableStateOf(false) }
    var showConfirmAll by remember { mutableStateOf(false) }
    var showConfirmSelected by remember { mutableStateOf(false) }
    var pickedCommitSha by remember { mutableStateOf("") }
    var pickedCommitDate by remember { mutableStateOf("") }
    var showConfirmAfterCommit by remember { mutableStateOf(false) }
    var showConfirmBeforeCommit by remember { mutableStateOf(false) }
    var deleteReleaseTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(s.successMessage) {
        if (s.successMessage != null) {
            delay(3500)
            vm.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (s.selectionMode) "${s.selectedIds.size} selected"
                        else "Releases (${s.releases.size})",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    if (s.selectionMode) {
                        IconButton(onClick = { vm.toggleSelectionMode() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
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
                                contentDescription = "Delete selected",
                                tint = if (s.selectedIds.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else LocalContentColor.current
                            )
                        }
                    } else {
                        Box {
                            IconButton(onClick = { showBulkMenu = true }) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Bulk delete options")
                            }
                            DropdownMenu(
                                expanded = showBulkMenu,
                                onDismissRequest = { showBulkMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete N newest") },
                                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                                    onClick = { showBulkMenu = false; showDeleteNewestDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete N oldest") },
                                    leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                                    onClick = { showBulkMenu = false; showDeleteOldestDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete by date range") },
                                    leadingIcon = { Icon(Icons.Filled.DateRange, null) },
                                    onClick = { showBulkMenu = false; showDeleteByDateDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete by selection") },
                                    leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                                    onClick = { showBulkMenu = false; vm.toggleSelectionMode() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete releases after commit…") },
                                    leadingIcon = { Icon(Icons.Filled.ArrowUpward, null) },
                                    onClick = {
                                        showBulkMenu = false
                                        vm.loadCommits()
                                        showCommitPickerAfter = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete releases before commit…") },
                                    leadingIcon = { Icon(Icons.Filled.ArrowDownward, null) },
                                    onClick = {
                                        showBulkMenu = false
                                        vm.loadCommits()
                                        showCommitPickerBefore = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Delete all releases",
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
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading -> LoadingIndicator()
                s.releases.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No releases yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (s.bulkDeleting) {
                            item {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                            }
                        }

                        s.successMessage?.let { msg ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            msg,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        s.error?.let { err ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Error,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            err,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        items(s.releases, key = { it.id }) { release ->
                            ReleaseCard(
                                release = release,
                                selectionMode = s.selectionMode,
                                selected = release.id in s.selectedIds,
                                onToggleSelect = { vm.toggleSelection(release.id) },
                                onDelete = { deleteReleaseTarget = release.id }
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Individual delete confirm ----
    deleteReleaseTarget?.let { targetId ->
        val release = s.releases.find { it.id == targetId }
        AlertDialog(
            onDismissRequest = { deleteReleaseTarget = null },
            title = { Text("Delete release") },
            text = {
                Text(
                    "Permanently delete \"${release?.name?.takeIf { it.isNotBlank() } ?: release?.tagName}\"? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRelease(targetId)
                    deleteReleaseTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteReleaseTarget = null }) { Text("Cancel") }
            }
        )
    }

    // ---- Delete N newest ----
    if (showDeleteNewestDialog) {
        NReleasesDialog(
            title = "Delete N newest",
            description = "How many of the newest releases should be deleted?",
            onConfirm = { n -> vm.deleteNewest(n); showDeleteNewestDialog = false },
            onDismiss = { showDeleteNewestDialog = false }
        )
    }

    // ---- Delete N oldest ----
    if (showDeleteOldestDialog) {
        NReleasesDialog(
            title = "Delete N oldest",
            description = "How many of the oldest releases should be deleted?",
            onConfirm = { n -> vm.deleteOldest(n); showDeleteOldestDialog = false },
            onDismiss = { showDeleteOldestDialog = false }
        )
    }

    // ---- Delete by date range ----
    if (showDeleteByDateDialog) {
        DateRangeDialog(
            onConfirm = { from, to ->
                vm.deleteByDateRange(from, to)
                showDeleteByDateDialog = false
            },
            onDismiss = { showDeleteByDateDialog = false }
        )
    }

    // ---- Commit picker — after ----
    if (showCommitPickerAfter) {
        CommitPickerDialog(
            title = "Delete releases after commit",
            description = "Releases published after the selected commit's date will be deleted.",
            commits = s.commits,
            loading = s.commitsLoading,
            onSelect = { sha, date ->
                pickedCommitSha = sha
                pickedCommitDate = date
                showCommitPickerAfter = false
                showConfirmAfterCommit = true
            },
            onDismiss = { showCommitPickerAfter = false }
        )
    }

    // ---- Commit picker — before ----
    if (showCommitPickerBefore) {
        CommitPickerDialog(
            title = "Delete releases before commit",
            description = "Releases published before the selected commit's date will be deleted.",
            commits = s.commits,
            loading = s.commitsLoading,
            onSelect = { sha, date ->
                pickedCommitSha = sha
                pickedCommitDate = date
                showCommitPickerBefore = false
                showConfirmBeforeCommit = true
            },
            onDismiss = { showCommitPickerBefore = false }
        )
    }

    // ---- Confirm after commit ----
    if (showConfirmAfterCommit) {
        val cutoff = runCatching { Instant.parse(pickedCommitDate) }.getOrNull()
        val count = if (cutoff != null) s.releases.count { release ->
            val d = release.publishedAt ?: release.createdAt
            runCatching { Instant.parse(d).isAfter(cutoff) }.getOrDefault(false)
        } else 0
        AlertDialog(
            onDismissRequest = { showConfirmAfterCommit = false },
            title = { Text("Delete releases after commit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Commit: ${pickedCommitSha.take(7)}", style = MaterialTheme.typography.labelMedium)
                    Text("This will delete $count release(s) published after this commit's date.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteReleasesAfterCommit(pickedCommitDate)
                    showConfirmAfterCommit = false
                }) { Text("Delete $count", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAfterCommit = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Confirm before commit ----
    if (showConfirmBeforeCommit) {
        val cutoff = runCatching { Instant.parse(pickedCommitDate) }.getOrNull()
        val count = if (cutoff != null) s.releases.count { release ->
            val d = release.publishedAt ?: release.createdAt
            runCatching { Instant.parse(d).isBefore(cutoff) }.getOrDefault(false)
        } else 0
        AlertDialog(
            onDismissRequest = { showConfirmBeforeCommit = false },
            title = { Text("Delete releases before commit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Commit: ${pickedCommitSha.take(7)}", style = MaterialTheme.typography.labelMedium)
                    Text("This will delete $count release(s) published before this commit's date.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteReleasesBeforeCommit(pickedCommitDate)
                    showConfirmBeforeCommit = false
                }) { Text("Delete $count", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBeforeCommit = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Confirm delete all ----
    if (showConfirmAll) {
        AlertDialog(
            onDismissRequest = { showConfirmAll = false },
            title = { Text("Delete all releases") },
            text = {
                Text(
                    "This will permanently delete all ${s.releases.size} release(s). This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllReleases()
                    showConfirmAll = false
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAll = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Confirm delete selected ----
    if (showConfirmSelected) {
        AlertDialog(
            onDismissRequest = { showConfirmSelected = false },
            title = { Text("Delete selected releases") },
            text = {
                Text("Delete ${s.selectedIds.size} selected release(s)? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    showConfirmSelected = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSelected = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReleaseCard(
    release: GhRelease,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onDelete: () -> Unit
) {
    GhCard {
        Row(
            modifier = if (selectionMode) {
                Modifier.toggleable(
                    value = selected,
                    onValueChange = { onToggleSelect() },
                    role = Role.Checkbox
                )
            } else Modifier,
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        release.tagName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (release.draft) GhBadge("Draft", MaterialTheme.colorScheme.secondary)
                    if (release.prerelease) GhBadge("Pre-release", MaterialTheme.colorScheme.tertiary)
                }

                if (!release.name.isNullOrBlank() && release.name != release.tagName) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        release.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Published ${RelativeTime.ago(release.publishedAt ?: release.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    release.targetCommitish?.let { target ->
                        Text(
                            "@ $target",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                release.author?.let { author ->
                    Text(
                        "by ${author.login}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        release.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (release.assets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${release.assets.size} asset(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!selectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete release",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun NReleasesDialog(
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
            TextButton(
                onClick = { if (n > 0) onConfirm(n) },
                enabled = n > 0
            ) { Text("Delete $n", color = if (n > 0) MaterialTheme.colorScheme.error else LocalContentColor.current) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DateRangeDialog(
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var fromDate by remember { mutableStateOf("") }
    var toDate by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete by date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Releases published within this date range will be deleted. Leave a field blank to skip that bound.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fromDate,
                    onValueChange = { fromDate = it },
                    label = { Text("From date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = toDate,
                    onValueChange = { toDate = it },
                    label = { Text("To date") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitPickerDialog(
    title: String,
    description: String,
    commits: List<GhCommit>,
    loading: Boolean,
    onSelect: (sha: String, date: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                    commits.isEmpty() -> Text(
                        "No commits found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(commits, key = { it.sha }) { commit ->
                            Surface(
                                onClick = {
                                    onSelect(commit.sha, commit.commit.author.date)
                                },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        commit.commit.message.lineSequence().firstOrNull()
                                            ?: commit.commit.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${commit.sha.take(7)} · ${commit.commit.author.name} · ${RelativeTime.ago(commit.commit.author.date)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
