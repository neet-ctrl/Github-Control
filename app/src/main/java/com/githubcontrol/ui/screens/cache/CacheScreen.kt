package com.githubcontrol.ui.screens.cache

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.Logger
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(
    onBack: () -> Unit,
    main: MainViewModel = hiltViewModel()
) {
    val ctx        = LocalContext.current
    val scope      = rememberCoroutineScope()
    val am         = main.accountManager

    val autoClear  by am.autoClearCacheFlow.collectAsState(initial = false)

    var logCount       by remember { mutableIntStateOf(0) }
    var imageCacheKb   by remember { mutableLongStateOf(0L) }
    var coilCacheKb    by remember { mutableLongStateOf(0L) }
    var totalCacheKb   by remember { mutableLongStateOf(0L) }
    var clearHistory   by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshing     by remember { mutableStateOf(false) }
    var lastCleared    by remember { mutableStateOf<String?>(null) }

    fun refreshStats() {
        scope.launch {
            refreshing = true
            withContext(Dispatchers.IO) {
                logCount = Logger.snapshot().size

                fun dirKb(f: File) = f.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() } / 1024L

                val cacheDir  = ctx.cacheDir
                val imageDirs = listOf(
                    File(cacheDir, "image_manager_disk_cache"),
                    File(cacheDir, "coil"),
                    File(cacheDir, "coil_image_cache"),
                    File(cacheDir, "picasso-cache")
                )
                imageCacheKb = imageDirs.filter { it.exists() }.sumOf { dirKb(it) }
                coilCacheKb  = imageDirs.filter { it.exists() }.sumOf { dirKb(it) }
                totalCacheKb = dirKb(cacheDir)
            }
            refreshing = false
        }
    }

    // Auto-clear check: if enabled and last cleared > 30 min ago, clear now
    LaunchedEffect(autoClear) {
        if (autoClear) {
            val lastClaredAt = am.lastCacheClearedAt()
            val elapsed = System.currentTimeMillis() - lastClaredAt
            if (elapsed > 30 * 60 * 1000L) {
                withContext(Dispatchers.IO) {
                    Logger.clear()
                    ctx.cacheDir.walkTopDown()
                        .filter { it.isFile && it != ctx.cacheDir }
                        .forEach { it.delete() }
                }
                am.setLastCacheCleared()
                lastCleared = "Auto-cleared at ${timeStamp()}"
                clearHistory = clearHistory + "Auto-cleared at ${timeStamp()}"
            }
        }
    }

    LaunchedEffect(Unit) { refreshStats() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Cache") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            },
            actions = {
                IconButton(onClick = { refreshStats() }) {
                    Icon(Icons.Filled.Refresh, "Refresh stats")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Summary card
            GhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Total cache on disk", fontWeight = FontWeight.SemiBold)
                        if (refreshing) {
                            Text("Computing…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(formatKb(totalCacheKb), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    Logger.clear()
                                    ctx.cacheDir.walkTopDown()
                                        .filter { it.isFile }
                                        .forEach { it.delete() }
                                }
                                am.setLastCacheCleared()
                                clearHistory = clearHistory + "All cache cleared at ${timeStamp()}"
                                lastCleared = "Cleared at ${timeStamp()}"
                                refreshStats()
                            }
                        }
                    ) { Text("Clear all", color = MaterialTheme.colorScheme.error) }
                }
            }

            // Logs card
            GhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Article, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("In-memory logs", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (refreshing) "Computing…" else "$logCount log lines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            Logger.clear()
                            clearHistory = clearHistory + "Logs cleared at ${timeStamp()}"
                            refreshStats()
                        }
                    ) { Text("Clear") }
                }
            }

            // Image cache card
            GhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Image / avatar cache", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (refreshing) "Computing…" else formatKb(imageCacheKb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Coil avatar + preview thumbnails",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    listOf("image_manager_disk_cache", "coil", "coil_image_cache")
                                        .map { File(ctx.cacheDir, it) }
                                        .filter { it.exists() }
                                        .forEach { it.deleteRecursively() }
                                }
                                clearHistory = clearHistory + "Image cache cleared at ${timeStamp()}"
                                refreshStats()
                            }
                        }
                    ) { Text("Clear") }
                }
            }

            // Auto-clear card
            GhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Auto-clear every 30 min", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Automatically wipes all caches when the app has been running for 30+ minutes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        lastCleared?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Switch(
                        checked = autoClear,
                        onCheckedChange = { scope.launch { am.setAutoClearCache(it) } }
                    )
                }
            }

            // Clear history / logs
            if (clearHistory.isNotEmpty()) {
                GhCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear history", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clearHistory = emptyList() }) { Text("Delete") }
                    }
                    Spacer(Modifier.height(6.dp))
                    clearHistory.reversed().take(20).forEach { entry ->
                        Row(
                            Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(entry, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Info card
            GhCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Clearing the cache does not affect your settings, accounts, or data stored on GitHub. " +
                        "It only removes temporary files on this device. After clearing, some items may take a moment to reload.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatKb(kb: Long): String = when {
    kb < 1024  -> "$kb KB"
    else       -> "${"%.1f".format(kb / 1024f)} MB"
}

private fun timeStamp(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
