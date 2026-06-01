package com.githubcontrol.ui.screens.commands

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.api.GhRepo
import com.githubcontrol.data.db.SnippetEntity
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.GhCommandsState
import com.githubcontrol.viewmodel.GitHubCommandsViewModel
import com.githubcontrol.viewmodel.ShellCmd
import com.githubcontrol.viewmodel.SnippetsViewModel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Placeholder helpers
// ─────────────────────────────────────────────────────────────────────────────

private val PLACEHOLDER = Regex("\\{(\\w+)\\}")

private fun extractParams(cmd: String): List<String> =
    PLACEHOLDER.findAll(cmd).map { it.groupValues[1] }.distinct().toList()

private fun paramCategory(key: String): String = when {
    key == "owner" || key == "repo"                                              -> "repo"
    key.contains("branch", ignoreCase = true) || key == "base" || key == "head" -> "branch"
    else                                                                         -> "text"
}

private fun resolveText(text: String, values: Map<String, String>): String {
    var r = text
    values.forEach { (k, v) -> if (v.isNotBlank()) r = r.replace("{$k}", v) }
    return r
}

// ─────────────────────────────────────────────────────────────────────────────
// Main screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GitHubCommandsScreen(
    onBack: () -> Unit,
    vm:   GitHubCommandsViewModel = hiltViewModel(),
    svVm: SnippetsViewModel       = hiltViewModel()
) {
    val s         by vm.state.collectAsState()
    val sv        by svVm.state.collectAsState()
    val snippets  by svVm.filtered.collectAsState()
    val ctx       = LocalContext.current
    val scope     = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab      by remember { mutableStateOf(0) }
    var editingCmd       by remember { mutableStateOf<ShellCmd?>(null) }
    var infoCmd          by remember { mutableStateOf<ShellCmd?>(null) }
    var savingCmd        by remember { mutableStateOf<ShellCmd?>(null) }
    var editingSnippet   by remember { mutableStateOf<SnippetEntity?>(null) }
    var showNewSnippet   by remember { mutableStateOf(false) }

    // Snackbar when a snippet is saved from the catalog
    LaunchedEffect(sv.justSavedLabel) {
        sv.justSavedLabel?.let { label ->
            snackbarHostState.showSnackbar("\"$label\" saved to Snippets")
            svVm.clearJustSaved()
        }
    }

    // ── filtered catalog list ─────────────────────────────────────────────────
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 1) {
                FloatingActionButton(
                    onClick = { showNewSnippet = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.Add, "New snippet")
                }
            }
        },
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
                                if (selectedTab == 0) "${filtered.size} of ${vm.catalog.size} commands"
                                else "${snippets.size} snippet${if (snippets.size == 1) "" else "s"}",
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
                        if (s.reposLoading && selectedTab == 0) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(end = 4.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                )

                // ── Tab row ──────────────────────────────────────────────────
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick  = { selectedTab = 0 },
                        text     = { Text("Catalog", style = MaterialTheme.typography.labelLarge) },
                        icon     = { Icon(Icons.Filled.Terminal, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick  = { selectedTab = 1 },
                        text     = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Snippets", style = MaterialTheme.typography.labelLarge)
                                if (sv.all.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "${sv.all.size}",
                                            style    = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                            color    = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (selectedTab == 0) {
                    // ── Catalog: search bar ──────────────────────────────────
                    OutlinedTextField(
                        value       = s.filter,
                        onValueChange = { vm.setFilter(it) },
                        modifier    = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 4.dp),
                        placeholder = { Text("Search by name, command, use-case, tag...") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (s.filter.isNotBlank()) {
                                IconButton(onClick = { vm.setFilter("") }) {
                                    Icon(Icons.Filled.Close, null)
                                }
                            }
                        },
                        singleLine = true,
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )
                    // ── Category filter chips ────────────────────────────────
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
                } else {
                    // ── Snippets: search bar ─────────────────────────────────
                    OutlinedTextField(
                        value         = sv.filter,
                        onValueChange = { svVm.setFilter(it) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        placeholder   = { Text("Search snippets...") },
                        leadingIcon   = { Icon(Icons.Filled.Search, null) },
                        trailingIcon  = {
                            if (sv.filter.isNotBlank()) {
                                IconButton(onClick = { svVm.setFilter("") }) {
                                    Icon(Icons.Filled.Close, null)
                                }
                            }
                        },
                        singleLine = true,
                        shape  = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider()
            }
        }
    ) { pad ->

        if (selectedTab == 0) {
            // ── Catalog tab content ───────────────────────────────────────────
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
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            "No commands match \"${s.filter}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { vm.setFilter(""); vm.setCategory("All") }) {
                            Text("Clear filters")
                        }
                    }
                }
            } else {
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
                                onInfo = { infoCmd    = cmd },
                                onSave = { savingCmd  = cmd }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        } else {
            // ── Snippets tab content ──────────────────────────────────────────
            SnippetsTabContent(
                snippets  = snippets,
                filter    = sv.filter,
                allEmpty  = sv.all.isEmpty(),
                padding   = pad,
                onCopy    = { sn -> ShareUtils.copyToClipboard(ctx, sn.command, sn.label) },
                onEdit    = { sn -> editingSnippet = sn },
                onDelete  = { sn -> svVm.delete(sn) }
            )
        }
    }

    // ── Info dialog (catalog) ─────────────────────────────────────────────────
    infoCmd?.let { cmd ->
        CommandInfoDialog(
            cmd       = cmd,
            onEdit    = { editingCmd = cmd; infoCmd = null },
            onSave    = { savingCmd  = cmd; infoCmd = null },
            onDismiss = { infoCmd    = null }
        )
    }

    // ── Smart-fill dialog (catalog) ───────────────────────────────────────────
    editingCmd?.let { cmd ->
        CommandEditDialog(
            cmd            = cmd,
            s              = s,
            onRepoSelected = { vm.selectRepo(it) },
            onClearRepo    = { vm.clearRepo() },
            onDismiss      = { editingCmd = null },
            ctx            = ctx
        )
    }

    // ── Save from catalog dialog ──────────────────────────────────────────────
    savingCmd?.let { cmd ->
        SnippetFormDialog(
            initialLabel   = cmd.label,
            initialCommand = cmd.command,
            initialDescription = cmd.description,
            repos          = s.repos,
            reposLoading   = s.reposLoading,
            onSave         = { label, command, desc, owner, repo ->
                svVm.save(label, command, desc, owner, repo)
            },
            onDismiss      = { savingCmd = null }
        )
    }

    // ── Edit snippet dialog ───────────────────────────────────────────────────
    editingSnippet?.let { sn ->
        SnippetFormDialog(
            initialLabel       = sn.label,
            initialCommand     = sn.command,
            initialDescription = sn.description,
            initialOwner       = sn.owner,
            initialRepo        = sn.repo,
            existingId         = sn.id,
            repos              = s.repos,
            reposLoading       = s.reposLoading,
            onSave             = { label, command, desc, owner, repo ->
                svVm.update(sn.copy(label = label, command = command, description = desc, owner = owner, repo = repo))
                scope.launch { snackbarHostState.showSnackbar("Snippet updated") }
            },
            onDelete           = { svVm.delete(sn) },
            onDismiss          = { editingSnippet = null }
        )
    }

    // ── New blank snippet dialog ──────────────────────────────────────────────
    if (showNewSnippet) {
        SnippetFormDialog(
            repos        = s.repos,
            reposLoading = s.reposLoading,
            onSave       = { label, command, desc, owner, repo ->
                svVm.save(label, command, desc, owner, repo)
            },
            onDismiss    = { showNewSnippet = false }
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
// Command row (catalog)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CommandRow(
    cmd: ShellCmd,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cmd.label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                IconButton(onClick = onInfo, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Info, "Info",
                        modifier = Modifier.size(16.dp),
                        tint     = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (cmd.description.isNotBlank()) {
                Text(
                    cmd.description,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
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
        // Copy
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ContentCopy, "Copy",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        // Smart-fill / edit
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Edit, "Edit & copy",
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
        // Save to snippets
        IconButton(onClick = onSave, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Bookmark, "Save to snippets",
                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info dialog — when/where to use, tags, full command
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CommandInfoDialog(
    cmd: ShellCmd,
    onEdit: () -> Unit,
    onSave: () -> Unit,
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
                        style      = MaterialTheme.typography.labelSmall,
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ── Command block ─────────────────────────────────────────────
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
                // ── Short description ─────────────────────────────────────────
                if (cmd.description.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        InfoSectionTitle(icon = Icons.Filled.ShortText, label = "What it does")
                        Text(cmd.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
                // ── Detailed explanation ──────────────────────────────────────
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
                // ── Tags / keywords ───────────────────────────────────────────
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
                OutlinedButton(onClick = onSave) {
                    Icon(Icons.Filled.Bookmark, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
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
// Snippets tab content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SnippetsTabContent(
    snippets: List<SnippetEntity>,
    filter: String,
    allEmpty: Boolean,
    padding: PaddingValues,
    onCopy: (SnippetEntity) -> Unit,
    onEdit: (SnippetEntity) -> Unit,
    onDelete: (SnippetEntity) -> Unit
) {
    if (allEmpty) {
        Box(
            Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Filled.Bookmark, null,
                    modifier = Modifier.size(56.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Text(
                    "No snippets yet",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "Tap the 🔖 icon on any command in the Catalog tab to save it here, " +
                        "or tap ＋ to write your own custom command.",
                        style  = MaterialTheme.typography.bodySmall,
                        color  = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    } else if (snippets.isEmpty()) {
        Box(
            Modifier.padding(padding).fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.SearchOff, null,
                    modifier = Modifier.size(48.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                Text("No snippets match \"$filter\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            modifier       = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(snippets, key = { it.id }) { sn ->
                SnippetRow(
                    sn       = sn,
                    onCopy   = { onCopy(sn) },
                    onEdit   = { onEdit(sn) },
                    onDelete = { onDelete(sn) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    color    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snippet row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SnippetRow(
    sn: SnippetEntity,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    sn.label,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f),
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                SnippetScopeChip(sn)
            }
            if (sn.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    sn.description,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Surface(
                shape    = RoundedCornerShape(6.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(0.96f)
            ) {
                Text(
                    sn.command,
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
        IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ContentCopy, "Copy",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Edit, "Edit",
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, "Delete",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete snippet?") },
            text    = { Text("\"${sn.label}\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snippet scope chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SnippetScopeChip(sn: SnippetEntity) {
    val isGlobal = sn.owner == null
    val label    = if (isGlobal) "Global" else "${sn.repo}"
    val bgColor  = if (isGlobal) MaterialTheme.colorScheme.primaryContainer
                   else           MaterialTheme.colorScheme.tertiaryContainer
    val fgColor  = if (isGlobal) MaterialTheme.colorScheme.onPrimaryContainer
                   else           MaterialTheme.colorScheme.onTertiaryContainer
    Surface(shape = RoundedCornerShape(20.dp), color = bgColor) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                if (isGlobal) Icons.Filled.Public else Icons.Filled.Folder,
                null,
                modifier = Modifier.size(10.dp),
                tint     = fgColor
            )
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color    = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Snippet form dialog  (create from scratch, save from catalog, edit existing)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SnippetFormDialog(
    initialLabel:       String     = "",
    initialCommand:     String     = "",
    initialDescription: String     = "",
    initialOwner:       String?    = null,
    initialRepo:        String?    = null,
    existingId:         Long?      = null,
    repos:              List<GhRepo>,
    reposLoading:       Boolean    = false,
    onSave:    (label: String, command: String, description: String, owner: String?, repo: String?) -> Unit,
    onDelete:  (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var label       by remember { mutableStateOf(initialLabel) }
    var command     by remember { mutableStateOf(initialCommand) }
    var description by remember { mutableStateOf(initialDescription) }
    var isGlobal    by remember { mutableStateOf(initialOwner == null) }
    var selectedRepo by remember {
        mutableStateOf<GhRepo?>(null)
    }
    var repoExpanded    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Restore selected repo when editing
    LaunchedEffect(repos, initialOwner) {
        if (initialOwner != null && selectedRepo == null) {
            selectedRepo = repos.find {
                it.owner.login == initialOwner && it.name == initialRepo
            }
        }
    }

    val canSave = label.isNotBlank() && command.isNotBlank() &&
                  (isGlobal || selectedRepo != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Filled.Bookmark, null) },
        title = {
            Text(
                if (existingId != null) "Edit Snippet" else "Save Snippet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Label
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    singleLine    = true,
                    label         = { Text("Label *") },
                    leadingIcon   = { Icon(Icons.Filled.Edit, null, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth()
                )
                // Command
                OutlinedTextField(
                    value         = command,
                    onValueChange = { command = it },
                    label         = { Text("Command *") },
                    leadingIcon   = { Icon(Icons.Filled.Terminal, null, modifier = Modifier.size(18.dp)) },
                    textStyle     = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    ),
                    minLines      = 2,
                    maxLines      = 6,
                    modifier      = Modifier.fillMaxWidth()
                )
                // Description
                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    singleLine    = true,
                    label         = { Text("Description (optional)") },
                    leadingIcon   = { Icon(Icons.Filled.ShortText, null, modifier = Modifier.size(18.dp)) },
                    modifier      = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Scope selector
                Text(
                    "Scope",
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selected = isGlobal, onClick = { isGlobal = true })
                    Column {
                        Text("Global", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Available in all repos", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selected = !isGlobal, onClick = { isGlobal = false })
                    Column {
                        Text("Repository-specific", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Shown only for one repo", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                AnimatedVisibility(
                    visible = !isGlobal,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
                    ExposedDropdownMenuBox(
                        expanded         = repoExpanded,
                        onExpandedChange = { repoExpanded = it }
                    ) {
                        OutlinedTextField(
                            value         = selectedRepo?.fullName ?: "",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Repository *") },
                            trailingIcon  = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (reposLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = repoExpanded)
                                }
                            },
                            leadingIcon  = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(18.dp)) },
                            modifier     = Modifier.menuAnchor().fillMaxWidth(),
                            colors       = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded         = repoExpanded,
                            onDismissRequest = { repoExpanded = false },
                            modifier         = Modifier.heightIn(max = 220.dp)
                        ) {
                            repos.forEach { r ->
                                DropdownMenuItem(
                                    text = {
                                        Text(r.fullName, style = MaterialTheme.typography.bodySmall)
                                    },
                                    onClick = { selectedRepo = r; repoExpanded = false },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        val owner = if (isGlobal) null else selectedRepo?.owner?.login
                        val repo  = if (isGlobal) null else selectedRepo?.name
                        onSave(label.trim(), command.trim(), description.trim(), owner, repo)
                        onDismiss()
                    },
                    enabled = canSave
                ) {
                    Icon(Icons.Filled.Done, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete snippet?") },
            text    = { Text("\"$label\" will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete?.invoke()
                    showDeleteConfirm = false
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Smart edit / fill dialog (catalog)
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

    var currentText by remember(cmd) { mutableStateOf(cmd.command) }
    val textValues   = remember(cmd) { mutableStateMapOf<String, String>() }

    fun fillParam(key: String, value: String) {
        if (value.isNotBlank()) currentText = currentText.replace("{$key}", value)
    }

    val hasChanges = currentText != cmd.command

    var repoExpanded   by remember { mutableStateOf(false) }
    var branchExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Filled.Terminal, null) },
        title = {
            Column {
                Text(cmd.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(cmd.category, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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

                // ── Repo picker ───────────────────────────────────────────────
                if (hasRepo) {
                    ExposedDropdownMenuBox(
                        expanded         = repoExpanded,
                        onExpandedChange = { repoExpanded = it }
                    ) {
                        OutlinedTextField(
                            value         = s.selectedRepo?.fullName ?: "",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Repository  →  {owner} / {repo}") },
                            trailingIcon  = {
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
                            modifier    = Modifier.menuAnchor().fillMaxWidth(),
                            colors      = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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

                // ── Branch picker ─────────────────────────────────────────────
                if (hasBranch) {
                    val branchParams = params.filter { paramCategory(it) == "branch" }
                    branchParams.forEach { bKey ->
                        val bLabel = when (bKey) {
                            "base" -> "Base branch  {base}"
                            "head" -> "Target / head branch  {head}"
                            else   -> "Branch  {$bKey}"
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
                                    label         = { Text(bLabel) },
                                    trailingIcon  = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (s.branchesLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchExpanded)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CallSplit, null, modifier = Modifier.size(18.dp)) },
                                    modifier    = Modifier.menuAnchor().fillMaxWidth(),
                                    colors      = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
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
                                                branchExpanded   = false
                                                textValues[bKey] = b.name
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
                                label       = { Text(bLabel) },
                                placeholder = { Text("Pick a repo above to get a branch dropdown") },
                                leadingIcon = { Icon(Icons.Filled.CallSplit, null, modifier = Modifier.size(18.dp)) },
                                singleLine  = true,
                                modifier    = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // ── Free-text params ──────────────────────────────────────────
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

                // ── Editable command text ─────────────────────────────────────
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Terminal, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("Command  (editable)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(
                    value         = currentText,
                    onValueChange = { currentText = it },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    textStyle     = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp
                    ),
                    shape = RoundedCornerShape(8.dp)
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
    "message"     -> "e.g. fix: resolve null-pointer crash"
    "tag"         -> "e.g. v1.2.0"
    "sha"         -> "e.g. a1b2c3d"
    "file"        -> "e.g. src/main/App.kt"
    "remote"      -> "e.g. origin"
    "n"           -> "e.g. 3 (number of commits)"
    "name"        -> "e.g. feature-login"
    "pattern"     -> "e.g. TODO"
    "url"         -> "e.g. https://github.com/user/repo.git"
    "path"        -> "e.g. src/components"
    "version"     -> "e.g. 2.1.0"
    "start", "end" -> "YYYY-MM-DD"
    else          -> "Value for {$key}"
}

private fun paramIcon(key: String): androidx.compose.ui.graphics.vector.ImageVector = when (key) {
    "message"  -> Icons.Filled.Comment
    "tag"      -> Icons.Filled.Tag
    "sha"      -> Icons.Filled.Fingerprint
    "file"     -> Icons.Filled.InsertDriveFile
    "remote"   -> Icons.Filled.Cloud
    "n"        -> Icons.Filled.Tag
    "name"     -> Icons.Filled.Label
    "pattern"  -> Icons.Filled.Search
    "url"      -> Icons.Filled.Link
    "path"     -> Icons.Filled.FolderOpen
    "version"  -> Icons.Filled.NewReleases
    else       -> Icons.Filled.TextFields
}

// ─────────────────────────────────────────────────────────────────────────────
// Category → icon mapping
// ─────────────────────────────────────────────────────────────────────────────

internal fun categoryIcon(cat: String): androidx.compose.ui.graphics.vector.ImageVector = when (cat) {
    "All"                -> Icons.Filled.GridView
    "Setup"              -> Icons.Filled.Settings
    "Repository"         -> Icons.Filled.FolderOpen
    "Basic"              -> Icons.Filled.Terminal
    "Branching"          -> Icons.Filled.CallSplit
    "Log & Inspect"      -> Icons.Filled.History
    "Stash"              -> Icons.Filled.Inventory2
    "Reset & Clean"      -> Icons.Filled.CleaningServices
    "Tags & Releases"    -> Icons.Filled.NewReleases
    "Advanced"           -> Icons.Filled.Science
    else                 -> Icons.Filled.Code
}

