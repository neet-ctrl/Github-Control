package com.githubcontrol.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.PermissionsCatalog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    val items = remember { PermissionsCatalog.items.filter { it.applies } }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        results.forEach { (perm, granted) ->
            Logger.i("Permissions", "$perm → ${if (granted) "GRANTED" else "DENIED"}")
        }
        refreshTick++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    TextButton(onClick = {
                        val all = items.filter { it.kind == PermissionsCatalog.Kind.Runtime }
                            .flatMap { it.permissions }
                            .filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
                            .toTypedArray()
                        if (all.isNotEmpty()) {
                            Logger.i("Permissions", "Requesting ${all.size} permission(s) at once")
                            runtimeLauncher.launch(all)
                        } else refreshTick++
                    }) { Text("Ask all") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "GitHub Control asks for each permission only when needed. Tap a row's button to grant it now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(items, key = { it.id }) { item ->
                    key(refreshTick) {
                        PermissionRow(
                            item = item,
                            granted = isGranted(ctx, item),
                            onAsk = {
                                when (item.kind) {
                                    PermissionsCatalog.Kind.Runtime -> {
                                        val missing = item.permissions.filter {
                                            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                                        }.toTypedArray()
                                        if (missing.isEmpty()) refreshTick++
                                        else runtimeLauncher.launch(missing)
                                    }
                                    PermissionsCatalog.Kind.Special -> {
                                        openSpecialSettings(ctx, item.id)
                                        refreshTick++
                                    }
                                }
                            },
                            onOpenSettings = { openAppDetails(ctx); refreshTick++ }
                        )
                    }
                }
            }
            EmbeddedTerminal(section = "Permissions")
        }
    }
}

@Composable
private fun PermissionRow(
    item: PermissionsCatalog.Item,
    granted: Boolean,
    onAsk: () -> Unit,
    onOpenSettings: () -> Unit
) {
    GhCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (granted) Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2EA043))
            else Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    if (item.essential) GhBadge("required", color = MaterialTheme.colorScheme.error)
                    if (item.kind == PermissionsCatalog.Kind.Special) GhBadge("system setting")
                }
                Text(
                    item.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (granted) {
                TextButton(onClick = onOpenSettings) { Text("Manage") }
            } else {
                Button(onClick = onAsk) { Text(if (item.kind == PermissionsCatalog.Kind.Special) "Open" else "Grant") }
            }
        }
    }
}

private fun isGranted(ctx: Context, item: PermissionsCatalog.Item): Boolean = when (item.kind) {
    PermissionsCatalog.Kind.Runtime ->
        item.permissions.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    PermissionsCatalog.Kind.Special -> when (item.id) {
        "battery" -> {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        }
        "all_files" -> Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
        else -> false
    }
}

private fun openSpecialSettings(ctx: Context, id: String) {
    runCatching {
        val intent = when (id) {
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:" + ctx.packageName))
            "all_files" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:" + ctx.packageName))
            else null
            else -> null
        } ?: return@runCatching
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        Logger.i("Permissions", "Opened special-settings for $id")
    }.onFailure { Logger.w("Permissions", "Could not open settings for $id: ${it.message}") }
}

private fun openAppDetails(ctx: Context) {
    runCatching {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }
}
