package com.githubcontrol.ui.screens.commits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.api.GhCodespace
import com.githubcontrol.data.api.GhCodespaceMachine
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.Diff
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.CommitsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(owner: String, name: String, sha: String, onBack: () -> Unit, vm: CommitsViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, sha) { vm.loadDetail(owner, name, sha) }
    val s by vm.detail.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showCreateBranch by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var showCreateCodespace by remember { mutableStateOf(false) }
    var detailCodespace by remember { mutableStateOf<GhCodespace?>(null) }
    val ctx = LocalContext.current

    /** github.com fallback URL — opens the "New codespace" picker pinned to this commit. */
    val webNewCodespaceUrl = "https://github.com/codespaces/new/$owner/$name/tree/$sha"

    LaunchedEffect(s.actionMessage) {
        s.actionMessage?.let {
            snackbarHost.showSnackbar(it)
            vm.clearDetailMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(sha.take(7)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    FilterChip(selected = s.ignoreWhitespace, onClick = { vm.toggleIgnoreWs() }, label = { Text("ws") })
                    Spacer(Modifier.width(4.dp))
                    FilterChip(selected = s.sideBySide, onClick = { vm.toggleSideBySide() }, label = { Text("split") })
                })
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { pad ->
        val c = s.commit
        when {
            s.loading -> Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
            c == null -> Box(Modifier.padding(pad).fillMaxSize()) { Text("No commit data", modifier = Modifier.padding(16.dp)) }
            else -> Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GhCard {
                    Text(c.commit.message, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("${c.commit.author.name} <${c.commit.author.email}>", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    Text("+${c.stats?.additions ?: 0} −${c.stats?.deletions ?: 0} (${c.files?.size ?: 0} files)", style = MaterialTheme.typography.labelMedium)
                }
                // Top-level actions for this commit: reset default branch here, or branch off of it.
                GhCard {
                    Text("Actions", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showResetConfirm = true },
                            enabled = !s.actionInFlight && s.defaultBranch.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.RestartAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reset ${s.defaultBranch.ifBlank { "default" }}", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { newBranchName = ""; showCreateBranch = true },
                            enabled = !s.actionInFlight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text("New branch", maxLines = 1)
                        }
                    }
                    if (s.actionInFlight) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                // ── Codespaces card ────────────────────────────────────
                GhCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Codespaces", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.loadCodespaces(owner, name) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh codespaces")
                        }
                    }
                    Text(
                        "Spin up a fresh dev environment pinned to ${sha.take(7)}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreateCodespace = true },
                            enabled = !s.creatingCodespace,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.AddCircleOutline, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (s.creatingCodespace) "Creating…" else "Create at this commit", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { ShareUtils.openInBrowser(ctx, webNewCodespaceUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.OpenInNew, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Open in browser", maxLines = 1)
                        }
                    }
                    if (s.creatingCodespace) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    when {
                        s.codespacesLoading -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading codespaces…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        s.codespacesError != null -> {
                            Text(
                                "Couldn't load codespaces: ${s.codespacesError}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        s.codespaces.isEmpty() -> {
                            Text(
                                "No codespaces yet for $owner/$name on this account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                "${s.codespaces.size} codespace${if (s.codespaces.size == 1) "" else "s"} in this repo",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            s.codespaces.forEach { cs ->
                                CodespaceRow(
                                    cs = cs,
                                    busy = s.busyCodespaceName == cs.name,
                                    onOpen = {
                                        cs.webUrl?.let { ShareUtils.openInBrowser(ctx, it) }
                                    },
                                    onCopy = {
                                        cs.webUrl?.let {
                                            ShareUtils.copyToClipboard(ctx, it, "Codespace URL")
                                        }
                                    },
                                    onStart = { vm.startCodespace(cs.name) },
                                    onStop = { vm.stopCodespace(cs.name) },
                                    onDelete = { vm.deleteCodespace(cs.name) },
                                    onDetails = { detailCodespace = cs }
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                    }
                }

                (c.files ?: emptyList()).forEach { f ->
                    GhCard {
                        Text(f.filename, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text("+${f.additions} −${f.deletions} • ${f.status}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val parsed = remember(f.patch, s.ignoreWhitespace) {
                            val raw = Diff.parse(f.patch)
                            if (s.ignoreWhitespace) Diff.ignoreWhitespace(raw) else raw
                        }
                        DiffBlock(parsed, sideBySide = s.sideBySide)
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Filled.RestartAlt, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hard reset ${s.defaultBranch}?") },
            text = {
                Text(
                    "This will force-update '${s.defaultBranch}' to point at ${sha.take(7)}. " +
                    "Commits after this one on '${s.defaultBranch}' will no longer be reachable from the branch tip. " +
                    "This cannot be undone from the app."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    vm.hardResetDefaultBranch(owner, name, sha)
                }) { Text("Hard reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } }
        )
    }

    if (showCreateCodespace) {
        CreateCodespaceDialog(
            sha = sha,
            machines = s.codespaceMachines,
            onDismiss = { showCreateCodespace = false },
            onConfirm = { machine, devcontainerPath, displayName, idleMinutes ->
                showCreateCodespace = false
                vm.createCodespaceForCommit(
                    owner = owner, name = name, sha = sha,
                    machine = machine.ifBlank { null },
                    devcontainerPath = devcontainerPath.ifBlank { null },
                    displayName = displayName.ifBlank { null },
                    idleTimeoutMinutes = idleMinutes
                )
            }
        )
    }

    detailCodespace?.let { cs ->
        CodespaceDetailDialog(
            cs = cs,
            onDismiss = { detailCodespace = null },
            onCopyAll = {
                ShareUtils.copyToClipboard(ctx, codespaceSummary(cs), "Codespace details")
            },
            onOpen = { cs.webUrl?.let { ShareUtils.openInBrowser(ctx, it) } }
        )
    }

    if (showCreateBranch) {
        AlertDialog(
            onDismissRequest = { showCreateBranch = false },
            icon = { Icon(Icons.Filled.AddCircleOutline, null) },
            title = { Text("Create branch from ${sha.take(7)}") },
            text = {
                Column {
                    Text("New branch will point at this commit.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it.trim() },
                        singleLine = true,
                        label = { Text("Branch name") },
                        placeholder = { Text("feature/my-change") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newBranchName.isNotBlank(),
                    onClick = {
                        val n = newBranchName
                        showCreateBranch = false
                        vm.createBranchAtSha(owner, name, sha, n)
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateBranch = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DiffBlock(lines: List<Diff.Line>, sideBySide: Boolean) {
    val bgAdd = Color(0xFF033A16); val bgDel = Color(0xFF490202); val bgCtx = Color(0xFF161B22); val bgHdr = Color(0xFF21262D)
    val txt = Color(0xFFE6EDF3)
    Column(Modifier.fillMaxWidth().background(bgCtx, RoundedCornerShape(8.dp)).horizontalScroll(rememberScrollState())) {
        for (l in lines) {
            val bg = when (l.type) { Diff.Type.ADD -> bgAdd; Diff.Type.REMOVE -> bgDel; Diff.Type.HEADER -> bgHdr; else -> bgCtx }
            val sign = when (l.type) { Diff.Type.ADD -> "+"; Diff.Type.REMOVE -> "−"; Diff.Type.HEADER -> "@"; else -> " " }
            Row(Modifier.background(bg).padding(horizontal = 8.dp, vertical = 1.dp)) {
                if (sideBySide) {
                    Text((l.oldNum?.toString() ?: "").padStart(4), color = Color(0xFF8B949E), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text((l.newNum?.toString() ?: "").padStart(4), color = Color(0xFF8B949E), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("$sign ${l.text}", color = txt, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CodespaceRow(
    cs: GhCodespace,
    busy: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    var confirmDelete by remember(cs.name) { mutableStateOf(false) }
    val state = cs.state.orEmpty()
    val isRunning = state.equals("Available", true) || state.equals("Starting", true)
    val canStart = !isRunning && !state.equals("Provisioning", true)
    val stateColor = when {
        state.equals("Available", true) -> Color(0xFF2EA043)
        state.equals("Starting", true) || state.equals("Provisioning", true) -> Color(0xFFD29922)
        state.equals("Shutdown", true) || state.equals("Stopped", true) -> Color(0xFF8B949E)
        state.equals("Failed", true) || state.equals("Unknown", true) -> Color(0xFFF85149)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { onDetails() }
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cs.displayName ?: cs.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                AssistChip(
                    onClick = onDetails,
                    label = { Text(state.ifEmpty { "?" }, color = stateColor, fontSize = 11.sp) },
                    modifier = Modifier.height(24.dp)
                )
            }
            val sub = buildString {
                cs.machine?.displayName?.let { append(it) }
                cs.gitStatus?.ref?.let {
                    if (isNotEmpty()) append(" • ")
                    append("ref: $it")
                }
                cs.gitStatus?.let { gs ->
                    if (gs.ahead > 0 || gs.behind > 0) append(" (↑${gs.ahead} ↓${gs.behind})")
                }
            }
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (busy) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpen, enabled = !cs.webUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open in browser")
                }
                IconButton(onClick = onCopy, enabled = !cs.webUrl.isNullOrBlank()) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy URL")
                }
                IconButton(onClick = onStart, enabled = !busy && canStart) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
                }
                IconButton(onClick = onStop, enabled = !busy && isRunning) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }, enabled = !busy) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete codespace?") },
            text = { Text("This permanently deletes '${cs.displayName ?: cs.name}'. Any uncommitted changes inside it will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCodespaceDialog(
    sha: String,
    machines: List<GhCodespaceMachine>,
    onDismiss: () -> Unit,
    onConfirm: (machine: String, devcontainerPath: String, displayName: String, idleMinutes: Int?) -> Unit
) {
    var machine by remember { mutableStateOf("") }
    var devcontainer by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var idle by remember { mutableStateOf("") }
    var machineMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Cloud, null) },
        title = { Text("New codespace at ${sha.take(7)}") },
        text = {
            Column {
                Text(
                    "All fields are optional — leave blank to use the repo defaults.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                if (machines.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = machineMenu,
                        onExpandedChange = { machineMenu = it }
                    ) {
                        OutlinedTextField(
                            value = machine,
                            onValueChange = { machine = it },
                            readOnly = true,
                            label = { Text("Machine type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = machineMenu,
                            onDismissRequest = { machineMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("(default)") },
                                onClick = { machine = ""; machineMenu = false }
                            )
                            machines.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.displayName ?: m.name} • ${m.cpus ?: "?"} CPU") },
                                    onClick = { machine = m.name; machineMenu = false }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = machine,
                        onValueChange = { machine = it },
                        label = { Text("Machine (e.g. basicLinux32gb)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayName, onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = devcontainer, onValueChange = { devcontainer = it },
                    label = { Text("Devcontainer path (.devcontainer/devcontainer.json)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = idle, onValueChange = { v -> if (v.all { it.isDigit() }) idle = v },
                    label = { Text("Idle timeout (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(machine, devcontainer, displayName, idle.toIntOrNull()) }) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CodespaceDetailDialog(
    cs: GhCodespace,
    onDismiss: () -> Unit,
    onCopyAll: () -> Unit,
    onOpen: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Cloud, null) },
        title = { Text(cs.displayName ?: cs.name, maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DetailLine("Name", cs.name)
                DetailLine("State", cs.state)
                DetailLine("Environment", cs.environmentId)
                DetailLine("Created", cs.createdAt)
                DetailLine("Updated", cs.updatedAt)
                DetailLine("Last used", cs.lastUsedAt)
                DetailLine("Idle timeout", cs.idleTimeoutMinutes?.let { "$it min" })
                DetailLine("Location", cs.location)
                DetailLine("Prebuild", cs.prebuild?.toString())
                cs.machine?.let { m ->
                    DetailLine("Machine", "${m.displayName ?: m.name}")
                    DetailLine("CPU / RAM / Disk",
                        listOfNotNull(
                            m.cpus?.let { "$it CPU" },
                            m.memoryInBytes?.let { "${it / (1024L * 1024L * 1024L)} GB RAM" },
                            m.storageInBytes?.let { "${it / (1024L * 1024L * 1024L)} GB Disk" }
                        ).joinToString(" • ").ifEmpty { null }
                    )
                }
                cs.gitStatus?.let { gs ->
                    DetailLine("Git ref", gs.ref)
                    DetailLine("Ahead / behind", "${gs.ahead} ahead, ${gs.behind} behind")
                    DetailLine("Has uncommitted", gs.hasUncommittedChanges.toString())
                    DetailLine("Has unpushed", gs.hasUnpushedChanges.toString())
                }
                DetailLine("Web URL", cs.webUrl)
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen, enabled = !cs.webUrl.isNullOrBlank()) { Text("Open") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopyAll) { Text("Copy details") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

private fun codespaceSummary(cs: GhCodespace): String = buildString {
    appendLine("Codespace: ${cs.displayName ?: cs.name}")
    appendLine("Name: ${cs.name}")
    cs.state?.let { appendLine("State: $it") }
    cs.machine?.displayName?.let { appendLine("Machine: $it") }
    cs.gitStatus?.ref?.let { appendLine("Ref: $it") }
    cs.webUrl?.let { appendLine("URL: $it") }
}
