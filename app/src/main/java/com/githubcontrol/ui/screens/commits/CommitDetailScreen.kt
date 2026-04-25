package com.githubcontrol.ui.screens.commits

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.Diff
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
