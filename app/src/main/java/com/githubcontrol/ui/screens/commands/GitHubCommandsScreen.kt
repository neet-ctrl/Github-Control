package com.githubcontrol.ui.screens.commands

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.api.GhRepo
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.GhCommandsState
import com.githubcontrol.viewmodel.GitHubCommandsViewModel
import com.githubcontrol.viewmodel.ShellCmd

// ─────────────────────────────────────────────────────────────────────────────
// Placeholder helpers
// ─────────────────────────────────────────────────────────────────────────────

private val PLACEHOLDER = Regex("\\{(\\w+)\\}")

private fun extractParams(cmd: String): List<String> =
    PLACEHOLDER.findAll(cmd).map { it.groupValues[1] }.distinct().toList()

private fun paramCategory(key: String): String = when {
    key == "owner" || key == "repo"                                         -> "repo"
    key.contains("branch", ignoreCase = true) || key == "base" || key == "head" -> "branch"
    else                                                                    -> "text"
}

private fun resolveText(text: String, values: Map<String, String>): String {
    var r = text
    values.forEach { (k, v) -> if (v.isNotBlank()) r = r.replace("{$k}", v) }
    return r
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubCommandsScreen(
    onBack: () -> Unit,
    vm: GitHubCommandsViewModel = hiltViewModel()
) {
    val s    by vm.state.collectAsState()
    val ctx  = LocalContext.current
    val listState = rememberLazyListState()

    var editingCmd by remember { mutableStateOf<ShellCmd?>(null) }
    var infoCmd    by remember { mutableStateOf<ShellCmd?>(null) }

    // ── filtered list ────────────────────────────────────────────────────────
    val filtered = remember(vm.catalog, s.filter, s.selectedCategory) {
        vm.catalog
            .filter { c -> s.selectedCategory == "All" || c.category == s.selectedCategory }
            .filter { c ->
                if (s.filter.isBlank()) true
                else {
                    c.label.contains(s.filter, true) ||
                    c.command.contains(s.filter, true) ||
                    c.description.contains(s.filter, true) ||
                    c.detail.contains(s.filter, true) ||
                    c.category.contains(s.filter, true) ||
                    c.tags.any { it.contains(s.filter, true) }
                }
            }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.category } }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                // ── App bar ──────────────────────────────────────────────────
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Git / GitHub Commands",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${filtered.size} of ${vm.catalog.size} commands",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                        }
                    },
                    actions = {
                        if (s.reposLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )

                // ── Search bar ───────────────────────────────────────────────
                OutlinedTextField(
                    value = s.filter,
                    onValueChange = { vm.setFilter(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 4.dp),
                    placeholder = { Text("Search by name, command, use-case, tag…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (s.filter.isNotBlank()) {
                            IconButton(onClick = { vm.setFilter("") }) {
                                Icon(Icons.Filled.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                )

                // ── Category filter chips ────────────────────────────────────
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vm.categories.forEach { cat ->
                        val selected = s.selectedCategory == cat
                        val count    = if (cat == "All") vm.catalog.size
                                       else vm.catalog.count { it.category == cat }
                        FilterChip(
                            selected = selected,
                            onClick  = { vm.setCategory(cat) },
                            label    = {
                                Text(
                                    if (cat == "All") "All  $count" else "$cat  $count",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (selected) ({
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp))
                            }) else ({
                                Icon(categoryIcon(cat), null, modifier = Modifier.size(14.dp))
                            }),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                HorizontalDivider()
            }
        }
    ) { pad ->

        // ── Empty state ───────────────────────────────────────────────────────
        if (grouped.isEmpty()) {
            Box(
                Modifier.padding(pad).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.SearchOff, null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        "No commands match "${s.filter}"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { vm.setFilter(""); vm.setCategory("All") }) {
                        Text("Clear filters")
                    }
                }
            }
            return@Scaffold
        }

        // ── Command list ──────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            grouped.forEach { (category, cmds) ->
                stickyHeader(key = "hdr_$category") {
                    CategoryHeader(category = category, count = cmds.size)
                }
                items(cmds, key = { "${it.category}/${it.label}" }) { cmd ->
                    CommandRow(
                        cmd    = cmd,
                        onCopy = { ShareUtils.copyToClipboard(ctx, cmd.command, cmd.label) },
                        onEdit = { editingCmd = cmd },
                        onInfo = { infoCmd    = cmd }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }

    // ── Info dialog ───────────────────────────────────────────────────────────
    infoCmd?.let { cmd ->
        CommandInfoDialog(cmd = cmd, onEdit = { editingCmd = cmd; infoCmd = null }, onDismiss = { infoCmd = null })
    }

    // ── Edit / smart-fill dialog ──────────────────────────────────────────────
    editingCmd?.let { cmd ->
        CommandEditDialog(
            cmd           = cmd,
            s             = s,
            onRepoSelected = { vm.selectRepo(it) },
            onClearRepo    = { vm.clearRepo() },
            onDismiss      = { editingCmd = null },
            ctx            = ctx
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category sticky header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(category: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape    = RoundedCornerShape(6.dp),
            color    = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(26.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    categoryIcon(category), null,
                    modifier = Modifier.size(14.dp),
                    tint     = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            category,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.primary,
            modifier   = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "$count",
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color    = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Command row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CommandRow(
    cmd: ShellCmd,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            // Label row + info icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cmd.label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                // Info icon — inline, small, unobtrusive
                IconButton(
                    onClick  = onInfo,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "Info",
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.outline
                    )
                }
            }
            // Short description
            if (cmd.description.isNotBlank()) {
                Text(
                    cmd.description,
                    style  = MaterialTheme.typography.labelSmall,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            // Monospace command chip
            Surface(
                shape    = RoundedCornerShape(6.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Text(
                    cmd.command,
                    style    = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 10.sp
                    ),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        // Copy icon
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.ContentCopy, "Copy",
                tint     = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        // Edit / smart-fill icon
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Edit, "Edit & copy",
                tint     = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info dialog  — when/where to use, tags, full command
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CommandInfoDialog(
    cmd: ShellCmd,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        categoryIcon(cmd.category), null,
                        modifier = Modifier.size(22.dp),
                        tint     = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(cmd.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        cmd.category,
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color    = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

                // ── Command block ─────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Terminal, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Command", style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(
                        shape    = RoundedCornerShape(8.dp),
                        color    = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            cmd.command,
                            style    = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(10.dp),
                            color    = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ── Short description ─────────────────────────────────────
                if (cmd.description.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoSectionTitle(icon = Icons.Filled.ShortText, label = "What it does")
                        Text(cmd.description, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // ── Detailed explanation ───────────────────────────────────
                if (cmd.detail.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoSectionTitle(icon = Icons.Filled.Lightbulb, label = "When & how to use")
                        Surface(
                            shape    = RoundedCornerShape(8.dp),
                            color    = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                cmd.detail,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // ── Tags / keywords ───────────────────────────────────────
                if (cmd.tags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoSectionTitle(icon = Icons.Filled.Tag, label = "Keywords")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement   = Arrangement.spacedBy(4.dp)
                        ) {
                            cmd.tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        tag,
                                        style    = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color    = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit & copy")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoSectionTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Smart edit / fill dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandEditDialog(
    cmd: ShellCmd,
    s: GhCommandsState,
    onRepoSelected: (GhRepo) -> Unit,
    onClearRepo: () -> Unit,
    onDismiss: () -> Unit,
    ctx: android.content.Context
) {
    val params     = remember(cmd.command) { extractParams(cmd.command) }
    val hasRepo    = remember(params) { "owner" in params || "repo" in params }
    val hasBranch  = remember(params) { params.any { paramCategory(it) == "branch" } }
    val textParams = remember(params) { params.filter { paramCategory(it) == "text" } }

    var currentText  by remember(cmd) { mutableStateOf(cmd.command) }
    val textValues    = remember(cmd) { mutableStateMapOf<String, String>() }

    fun fillParam(key: String, value: String) {
        if (value.isNotBlank()) currentText = currentText.replace("{$key}", value)
    }

    val hasChanges = currentText != cmd.command

    var repoExpanded   by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Terminal, null) },
        title = {
            Column {
                Text(cmd.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(cmd.category, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Info chip
                Surface(
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Fill placeholders with your repos & branches. Command updates live.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // ── Repo picker ────────────────────────────────────────────
                if (hasRepo) {
                    ExposedDropdownMenuBox(
                        expanded = repoExpanded,
                        onExpandedChange = { repoExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = s.selectedRepo?.fullName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label    = { Text("Repository  →  {owner} / {repo}") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.selectedRepo != null) {
                                        IconButton(onClick = {
                                            onClearRepo()
                                            currentText = cmd.command
                                            textValues.clear()
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = repoExpanded)
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors   = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded         = repoExpanded,
                            onDismissRequest = { repoExpanded = false },
                            modifier         = Modifier.heightIn(max = 260.dp)
                        ) {
                            if (s.reposLoading) {
                                DropdownMenuItem(
                                    text    = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
                                    onClick = {}
                                )
                            }
                            s.repos.forEach { r ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(r.fullName, style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold)
                                            r.description?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    },
                                    onClick = {
                                        repoExpanded = false
                                        onRepoSelected(r)
                                        fillParam("owner", r.owner.login)
                                        fillParam("repo", r.name)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Branch picker ──────────────────────────────────────────
                if (hasBranch) {
                    val branchParams = params.filter { paramCategory(it) == "branch" }
                    branchParams.forEach { bKey ->
                        val label = when (bKey) {
                            "base"   -> "Base branch  {base}"
                            "head"   -> "Target / head branch  {head}"
                            else     -> "Branch  {$bKey}"
                        }
                        if (s.selectedRepo != null) {
                            ExposedDropdownMenuBox(
                                expanded         = branchExpanded,
                                onExpandedChange = { branchExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value         = textValues[bKey] ?: "",
                                    onValueChange = {},
                                    readOnly      = true,
                                    label         = { Text(label) },
                                    trailingIcon  = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (s.branchesLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded)
                                        }
                                    },
                                    leadingIcon  = {
                                        Icon(Icons.Filled.CallSplit, null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier     = Modifier.menuAnchor().fillMaxWidth(),
                                    colors       = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                                )
                                ExposedDropdownMenu(
                                    expanded         = branchExpanded,
                                    onDismissRequest = { branchExpanded = false },
                                    modifier         = Modifier.heightIn(max = 220.dp)
                                ) {
                                    s.branches.forEach { b ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment     = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(b.name, style = MaterialTheme.typography.bodySmall)
                                                    if (b.protected) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.errorContainer
                                                        ) {
                                                            Text(
                                                                "protected",
                                                                style    = MaterialTheme.typography.labelSmall,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                color    = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                branchExpanded    = false
                                                textValues[bKey]  = b.name
                                                fillParam(bKey, b.name)
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Filled.CallSplit, null, modifier = Modifier.size(16.dp))
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value         = textValues[bKey] ?: "",
                                onValueChange = { v ->
                                    textValues[bKey] = v
                                    if (v.isNotBlank()) currentText = resolveText(currentText, mapOf(bKey to v))
                                },
                                label       = { Text(label) },
                                placeholder = { Text("Pick a repo above to get a branch dropdown") },
                                leadingIcon = {
                                    Icon(Icons.Filled.CallSplit, null, modifier = Modifier.size(18.dp))
                                },
                                singleLine  = true,
                                modifier    = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ── Free-text params ───────────────────────────────────────
                textParams.forEach { pKey ->
                    OutlinedTextField(
                        value         = textValues[pKey] ?: "",
                        onValueChange = { v ->
                            textValues[pKey] = v
                            if (v.isNotBlank()) currentText = resolveText(currentText, mapOf(pKey to v))
                        },
                        label       = { Text("${pKey.replaceFirstChar { it.uppercase() }}  {$pKey}") },
                        placeholder = { Text(paramHint(pKey)) },
                        leadingIcon = { Icon(paramIcon(pKey), null, modifier = Modifier.size(18.dp)) },
                        singleLine  = pKey != "message",
                        modifier    = Modifier.fillMaxWidth()
                    )
                }

                // ── Editable command text ──────────────────────────────────
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Terminal, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Command  (editable)",
                        style  = MaterialTheme.typography.labelMedium,
                        color  = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value         = currentText,
                    onValueChange = { currentText = it },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    textStyle     = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    ),
                    shape         = RoundedCornerShape(8.dp)
                )
                if (hasChanges) {
                    Text("Modified from original",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = hasChanges,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    IconButton(onClick = {
                        currentText = cmd.command
                        textValues.clear()
                        onClearRepo()
                    }) {
                        Icon(Icons.Filled.Refresh, "Reset to original",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp))
                    }
                }
                FilledIconButton(
                    onClick = { ShareUtils.copyToClipboard(ctx, currentText, cmd.label) },
                    colors  = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.ContentCopy, "Copy", modifier = Modifier.size(18.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Param helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun paramHint(key: String): String = when (key) {
    "name"    -> "e.g. Jane Doe"
    "email"   -> "e.g. you@example.com"
    "editor"  -> "e.g. nano   or   code --wait"
    "file"    -> "e.g. src/main.kt"
    "message" -> "e.g. fix: correct typo in README"
    "sha"     -> "e.g. a1b2c3d  (7-char short SHA)"
    "version" -> "e.g. 1.2.0"
    "url"     -> "e.g. https://github.com/…"
    "path"    -> "e.g. ../my-worktree"
    "pattern" -> "e.g. TODO  or  functionName"
    "n"       -> "e.g. 3  (number of commits)"
    "key"     -> "e.g. core.editor"
    else      -> "{$key}"
}

private fun paramIcon(key: String) = when (key) {
    "name"    -> Icons.Filled.Person
    "email"   -> Icons.Filled.Email
    "editor"  -> Icons.Filled.Code
    "file"    -> Icons.Filled.InsertDriveFile
    "message" -> Icons.Filled.Message
    "sha"     -> Icons.Filled.Tag
    "version" -> Icons.Filled.NewReleases
    "url"     -> Icons.Filled.Link
    "path"    -> Icons.Filled.FolderOpen
    "pattern" -> Icons.Filled.Search
    "n"       -> Icons.Filled.Tag
    else      -> Icons.Filled.TextFields
}

// ─────────────────────────────────────────────────────────────────────────────
// Category icon map
// ─────────────────────────────────────────────────────────────────────────────

internal fun categoryIcon(category: String) = when (category) {
    "All"            -> Icons.Filled.GridView
    "Setup"          -> Icons.Filled.Tune
    "Repository"     -> Icons.Filled.FolderOpen
    "Basic"          -> Icons.Filled.Code
    "Branching"      -> Icons.Filled.CallSplit
    "Log & Inspect"  -> Icons.Filled.History
    "Stash"          -> Icons.Filled.Inventory2
    "Reset & Clean"  -> Icons.Filled.CleaningServices
    "Tags & Releases"-> Icons.Filled.NewReleases
    "Advanced"       -> Icons.Filled.Science
    else             -> Icons.Filled.Terminal
}
