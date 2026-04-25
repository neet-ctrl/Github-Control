package com.githubcontrol.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.CrashHandler
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.utils.ShareUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val files = remember(refresh) { CrashHandler.list() }
    var selected by remember { mutableStateOf<File?>(null) }
    var clearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Crash reports") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (files.isNotEmpty()) TextButton(onClick = { clearAllDialog = true }) { Text("Clear all") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (files.isEmpty()) {
                GhCard {
                    Text("No crashes recorded.", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "If the app ever crashes, the full stack trace and the last 200 log entries are saved here automatically and kept until you delete them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "${files.size} report(s) stored permanently in app private storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(files, key = { it.absolutePath }) { f ->
                        GhCard {
                            Row(
                                Modifier.fillMaxWidth().clickable { selected = f },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GhBadge(RelativeTime.format(f.lastModified()))
                                        GhBadge("${f.length()} B")
                                    }
                                }
                                IconButton(onClick = {
                                    if (CrashHandler.delete(f)) refresh++
                                }) { Icon(Icons.Filled.Delete, null) }
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { file ->
        val text = remember(file) { CrashHandler.read(file) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(file.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
            text = {
                Box(
                    Modifier.heightIn(min = 240.dp, max = 480.dp)
                        .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(text, color = Color(0xFFE6EDF3), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { ShareUtils.copyToClipboard(ctx, text, label = file.name) }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copy")
                    }
                    TextButton(onClick = { ShareUtils.shareText(ctx, text, subject = file.name) }) {
                        Icon(Icons.Filled.Share, null); Spacer(Modifier.width(4.dp)); Text("Share")
                    }
                    TextButton(onClick = {
                        if (CrashHandler.delete(file)) { refresh++; selected = null }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }

    if (clearAllDialog) {
        AlertDialog(
            onDismissRequest = { clearAllDialog = false },
            title = { Text("Clear all crash reports?") },
            text = { Text("This permanently deletes every saved crash report. Cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { CrashHandler.deleteAll(); refresh++; clearAllDialog = false }) {
                    Text("Delete all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { clearAllDialog = false }) { Text("Cancel") } }
        )
    }
}
