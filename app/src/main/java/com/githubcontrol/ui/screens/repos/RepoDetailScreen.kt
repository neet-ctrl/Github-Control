package com.githubcontrol.ui.screens.repos

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.components.QrDialog
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.ByteFormat
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.MainViewModel
import com.githubcontrol.viewmodel.RepoActionsViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun RepoDetailScreen(
    owner: String, name: String, main: MainViewModel,
    onBack: () -> Unit, onNavigate: (String) -> Unit,
    vm: RepoActionsViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()
    val dangerousMode by main.accountManager.dangerousModeFlow.collectAsState(initial = false)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.repo?.fullName ?: "Repository", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    s.repo?.let {
                        IconButton(onClick = { vm.toggleStar() }) {
                            Icon(if (s.starred) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                                tint = if (s.starred) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                    }
                }
            )
        }
    ) { pad ->
        val r = s.repo
        when {
            s.loading && r == null -> Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
            r == null -> Box(Modifier.padding(pad).fillMaxSize()) {
                Text(s.error ?: "Repository unavailable", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            else ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GhCard {
                Text(r.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (r.description != null) Text(r.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (r.private) GhBadge("Private", MaterialTheme.colorScheme.tertiary) else GhBadge("Public")
                    if (r.fork) GhBadge("Fork")
                    if (r.archived) GhBadge("Archived")
                    r.language?.let { GhBadge(it) }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("★ ${r.stars}")
                    Text("⑂ ${r.forks}")
                    Text("👁 ${r.watchers}")
                    Text("◌ ${r.openIssues}")
                    Text(ByteFormat.human(r.size * 1024L))
                }
                Spacer(Modifier.height(8.dp))
                Text("Default branch: ${r.defaultBranch}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GhCard {
                Text("Browse", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRowSimple {
                    OutlinedButton(onClick = { onNavigate(Routes.readme(owner, name, r.defaultBranch)) }) { Icon(Icons.Filled.Description, null); Spacer(Modifier.width(6.dp)); Text("README") }
                    OutlinedButton(onClick = { onNavigate(Routes.files(owner, name, "", r.defaultBranch)) }) { Icon(Icons.Filled.Folder, null); Spacer(Modifier.width(6.dp)); Text("Files") }
                    OutlinedButton(onClick = { onNavigate(Routes.tree(owner, name, r.defaultBranch)) }) { Icon(Icons.Filled.AccountTree, null); Spacer(Modifier.width(6.dp)); Text("Tree") }
                    OutlinedButton(onClick = { onNavigate(Routes.commits(owner, name, r.defaultBranch)) }) { Icon(Icons.Filled.History, null); Spacer(Modifier.width(6.dp)); Text("Commits") }
                    OutlinedButton(onClick = { onNavigate(Routes.branches(owner, name)) }) { Icon(Icons.Filled.CallSplit, null); Spacer(Modifier.width(6.dp)); Text("Branches") }
                    OutlinedButton(onClick = { onNavigate(Routes.pulls(owner, name)) }) { Icon(Icons.Filled.MergeType, null); Spacer(Modifier.width(6.dp)); Text("PRs") }
                    OutlinedButton(onClick = { onNavigate(Routes.issues(owner, name)) }) { Icon(Icons.Filled.BugReport, null); Spacer(Modifier.width(6.dp)); Text("Issues") }
                    OutlinedButton(onClick = { onNavigate(Routes.actions(owner, name)) }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Actions") }
                    OutlinedButton(onClick = { onNavigate(Routes.analytics(owner, name)) }) { Icon(Icons.Filled.Analytics, null); Spacer(Modifier.width(6.dp)); Text("Analytics") }
                    OutlinedButton(onClick = { onNavigate(Routes.releases(owner, name)) }) { Icon(Icons.Filled.NewReleases, null); Spacer(Modifier.width(6.dp)); Text("Releases") }
                    Button(onClick = { onNavigate(Routes.upload(owner, name, "", r.defaultBranch)) }) { Icon(Icons.Filled.Upload, null); Spacer(Modifier.width(6.dp)); Text("Upload") }
                }
            }

            GhCard {
                Text("Languages", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val total = s.languages.values.sum().coerceAtLeast(1)
                s.languages.entries.sortedByDescending { it.value }.take(8).forEach { (k, v) ->
                    val pct = (v * 100 / total).toInt()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k, modifier = Modifier.weight(1f))
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.weight(2f))
                        Spacer(Modifier.width(8.dp))
                        Text("$pct%", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            GhCard {
                Text("Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                ListItem(headlineContent = { Text("Issues") }, trailingContent = { Switch(checked = r.hasIssues, onCheckedChange = { vm.toggleFeatures(issues = it) }) })
                ListItem(headlineContent = { Text("Wiki") }, trailingContent = { Switch(checked = r.hasWiki, onCheckedChange = { vm.toggleFeatures(wiki = it) }) })
                ListItem(headlineContent = { Text("Projects") }, trailingContent = { Switch(checked = r.hasProjects, onCheckedChange = { vm.toggleFeatures(projects = it) }) })
                ListItem(headlineContent = { Text("Private") }, trailingContent = { Switch(checked = r.private, onCheckedChange = { vm.setVisibility(it) }) })
                ListItem(headlineContent = { Text("Archived") }, trailingContent = { Switch(checked = r.archived, onCheckedChange = { vm.setArchived(it) }) })
            }

            GhCard {
                Text("Administration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRowSimple {
                    OutlinedButton(onClick = { onNavigate(Routes.collaborators(owner, name)) }) { Icon(Icons.Filled.Group, null); Spacer(Modifier.width(6.dp)); Text("Collaborators") }
                    OutlinedButton(onClick = { onNavigate(Routes.branchProtection(owner, name, r.defaultBranch)) }) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(6.dp)); Text("Branch protection") }
                    OutlinedButton(onClick = { onNavigate(Routes.compare(owner, name, r.defaultBranch, "")) }) { Icon(Icons.Filled.CompareArrows, null); Spacer(Modifier.width(6.dp)); Text("Compare") }
                }
            }
            GhCard {
                Text("Actions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRowSimple {
                    OutlinedButton(onClick = { vm.fork() }) { Text("Fork") }
                    OutlinedButton(onClick = { showRename = true }) { Text("Rename") }
                    OutlinedButton(onClick = { showTransfer = true }) { Text("Transfer") }
                    OutlinedButton(onClick = { vm.watch(true) }) { Text("Watch") }
                    OutlinedButton(onClick = { vm.watch(false) }) { Text("Unwatch") }
                }
            }

            GhCard {
                Text("Share & open", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val webUrl = "https://github.com/$owner/$name"
                val cloneHttps = "https://github.com/$owner/$name.git"
                val cloneSsh = "git@github.com:$owner/$name.git"
                FlowRowSimple {
                    OutlinedButton(onClick = { ShareUtils.openInBrowser(ctx, webUrl) }) {
                        Icon(Icons.Filled.OpenInBrowser, null); Spacer(Modifier.width(6.dp)); Text("Open in browser")
                    }
                    OutlinedButton(onClick = { ShareUtils.shareText(ctx, webUrl, "${r.fullName} on GitHub") }) {
                        Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share link")
                    }
                    OutlinedButton(onClick = { ShareUtils.copyToClipboard(ctx, cloneHttps, "Clone URL") }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy HTTPS")
                    }
                    OutlinedButton(onClick = { ShareUtils.copyToClipboard(ctx, cloneSsh, "Clone URL") }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy SSH")
                    }
                    OutlinedButton(onClick = { showQr = true }) {
                        Icon(Icons.Filled.QrCode, null); Spacer(Modifier.width(6.dp)); Text("QR code")
                    }
                }
            }

            GitCommandsCard(owner = owner, name = name, defaultBranch = r.defaultBranch, ctx = ctx)

            // Danger zone — always shown. Delete itself is gated by typing the repo name in the
            // confirm dialog, so an explicit "dangerous mode" toggle is no longer required.
            GhCard {
                Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dangerousMode) "Dangerous mode is on — actions execute without extra confirmation."
                    else "You'll be asked to type the repository name to confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showDelete = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(6.dp)); Text("Delete this repository")
                }
            }
            if (s.actionMessage != null) {
                LaunchedEffect(s.actionMessage) { /* could surface as snackbar */ }
                Text(s.actionMessage!!, color = MaterialTheme.colorScheme.primary)
            }
        }
        }
    }

    if (showRename) {
        var newName by remember { mutableStateOf(s.repo?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename repository") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("New name") }) },
            confirmButton = { TextButton(onClick = { vm.rename(newName) { showRename = false } }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
    if (showTransfer) {
        var newOwner by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("Transfer ownership") },
            text = { OutlinedTextField(newOwner, { newOwner = it }, singleLine = true, label = { Text("New owner login") }) },
            confirmButton = { TextButton(onClick = { vm.transfer(newOwner); showTransfer = false }) { Text("Transfer") } },
            dismissButton = { TextButton(onClick = { showTransfer = false }) { Text("Cancel") } }
        )
    }
    if (showQr) {
        QrDialog(text = "https://github.com/$owner/$name", title = "$owner/$name", onDismiss = { showQr = false })
    }
    if (showDelete) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete repository") },
            text = {
                Column {
                    Text("Type the repository name '${s.repo?.name}' to confirm.", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("Confirm name") })
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(typed) { ok -> showDelete = false; if (ok) onBack() } }) { Text("Delete forever", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Git Commands card
// ─────────────────────────────────────────────────────────────────────────────

private data class GitCmd(val label: String, val command: String)

@Composable
private fun GitCommandsCard(
    owner: String,
    name: String,
    defaultBranch: String,
    ctx: android.content.Context
) {
    val commands = remember(owner, name, defaultBranch) {
        listOf(
            GitCmd("Stage all changes", "git add ."),
            GitCmd("Commit", """git commit -m "mmm""""),
            GitCmd("Push to $defaultBranch", "git push origin $defaultBranch"),
            GitCmd(
                "Generate SSH key & set remote",
                """ssh-keygen -t ed25519 -C "your-email@example.com" -f ~/.ssh/id_ed25519 -N "" && cat ~/.ssh/id_ed25519.pub && git remote set-url origin git@github.com:$owner/$name.git"""
            ),
            GitCmd(
                "Nuke folder & fresh clone",
                """rm -rf .cache && rm -rf .[!.]* ..?* * 2>/dev/null && git clone https://github.com/$owner/$name.git"""
            )
        )
    }

    var editingCmd by remember { mutableStateOf<GitCmd?>(null) }

    GhCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Terminal,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Git Commands", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap copy to copy immediately. Tap edit to customise before copying.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        commands.forEachIndexed { idx, cmd ->
            GitCommandRow(
                cmd        = cmd,
                onCopy     = { ShareUtils.copyToClipboard(ctx, cmd.command, cmd.label) },
                onEdit     = { editingCmd = cmd }
            )
            if (idx < commands.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }

    editingCmd?.let { cmd ->
        GitCommandEditDialog(
            cmd      = cmd,
            ctx      = ctx,
            onDismiss = { editingCmd = null }
        )
    }
}

@Composable
private fun GitCommandRow(
    cmd: GitCmd,
    onCopy: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Coloured number badge
        Surface(
            shape    = MaterialTheme.shapes.extraSmall,
            color    = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Code,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                cmd.label,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                cmd.command,
                style      = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
        // Icon-only copy button
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy command",
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp)
            )
        }
        // Icon-only open/edit button
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit & copy",
                tint               = MaterialTheme.colorScheme.secondary,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun GitCommandEditDialog(
    cmd: GitCmd,
    ctx: android.content.Context,
    onDismiss: () -> Unit
) {
    var text by remember(cmd) { mutableStateOf(cmd.command) }
    val hasChanges = text != cmd.command

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Terminal, null) },
        title = { Text(cmd.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color  = MaterialTheme.colorScheme.secondaryContainer,
                    shape  = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Changes are temporary — the original command is never altered.",
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                OutlinedTextField(
                    value         = text,
                    onValueChange = { text = it },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    textStyle     = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    label         = { Text("Command") }
                )
                // Show diff hint only when changed
                if (hasChanges) {
                    Text(
                        "Modified from original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Reset to original
                if (hasChanges) {
                    IconButton(onClick = { text = cmd.command }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reset to original",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier           = Modifier.size(20.dp)
                        )
                    }
                }
                // Copy edited command
                FilledIconButton(
                    onClick = { ShareUtils.copyToClipboard(ctx, text, cmd.label) },
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        modifier           = Modifier.size(18.dp)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
