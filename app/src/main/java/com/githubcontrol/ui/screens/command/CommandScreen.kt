package com.githubcontrol.ui.screens.command

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.viewmodel.CommandSpec
import com.githubcontrol.viewmodel.CommandViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandScreen(onBack: () -> Unit, vm: CommandViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(s.lines.size) { if (s.lines.isNotEmpty()) listState.animateScrollToItem(s.lines.size - 1) }

    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    var infoFor by remember { mutableStateOf<CommandSpec?>(null) }
    var catalogOpen by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    val grouped = remember(query) {
        CommandViewModel.catalog
            .filter { query.isBlank()
                    || it.name.contains(query, true)
                    || it.description.contains(query, true)
                    || it.category.contains(query, true) }
            .groupBy { it.category }
            .toSortedMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Command Mode") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {

            // ── Catalog header (collapsible) ─────────────────────────────
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Command catalog (${CommandViewModel.catalog.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { catalogOpen = !catalogOpen }) {
                            Icon(
                                if (catalogOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (catalogOpen) "Hide" else "Show"
                            )
                        }
                    }
                    if (catalogOpen) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Filter commands…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                            grouped.forEach { (cat, items) ->
                                item(key = "h-$cat") {
                                    Text(
                                        cat,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                    )
                                }
                                items(items, key = { "${cat}-${it.name}" }) { spec ->
                                    CommandRow(
                                        spec = spec,
                                        onUse = { vm.useTemplate(spec.template) },
                                        onCopy = {
                                            clipboard.setText(AnnotatedString(spec.template))
                                        },
                                        onInfo = { infoFor = spec }
                                    )
                                }
                            }
                            if (grouped.isEmpty()) item {
                                Text("No commands match \"$query\".",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Terminal output ──────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0D1117))
                    .padding(8.dp)
            ) {
                if (s.lines.isEmpty()) item {
                    Text(
                        "Tap a command above to insert it, or type 'help'.",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )
                }
                items(s.lines) { line ->
                    Text("$ ${line.cmd}", color = Color(0xFF2F81F7), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(
                        line.output,
                        color = if (line.ok) Color(0xFFE6EDF3) else Color(0xFFF85149),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    s.input,
                    { vm.setInput(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type command…") },
                    singleLine = true
                )
                IconButton(onClick = { vm.run() }, enabled = !s.running) {
                    Icon(Icons.Filled.PlayArrow, null)
                }
            }
            Spacer(Modifier.height(6.dp))
            EmbeddedTerminal(section = "Command")
        }

        // Info dialog
        infoFor?.let { spec ->
            AlertDialog(
                onDismissRequest = { infoFor = null },
                title = { Text(spec.name) },
                text = {
                    Column {
                        Text("Category: ${spec.category}", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Usage", style = MaterialTheme.typography.labelLarge)
                        Text(spec.template, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("What it does", style = MaterialTheme.typography.labelLarge)
                        Text(spec.description)
                        Spacer(Modifier.height(8.dp))
                        Text("Example", style = MaterialTheme.typography.labelLarge)
                        Text(spec.example, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.useTemplate(spec.template); infoFor = null
                    }) { Text("Insert") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(spec.template)); infoFor = null
                    }) { Text("Copy") }
                }
            )
        }
    }
}

@Composable
private fun CommandRow(
    spec: CommandSpec,
    onUse: () -> Unit,
    onCopy: () -> Unit,
    onInfo: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onUse() },
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    spec.name,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            IconButton(onClick = onInfo) { Icon(Icons.Filled.Info, contentDescription = "Details") }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy template") }
        }
    }
}
