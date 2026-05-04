package com.githubcontrol.ui.screens.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.githubcontrol.data.auth.Account
import com.githubcontrol.data.auth.ScopeCatalog
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: MainViewModel, onBack: () -> Unit, onAdd: () -> Unit) {
    val s by vm.state.collectAsState()
    var inspect   by remember { mutableStateOf<Account?>(null) }
    var showPatFor by remember { mutableStateOf<Account?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Accounts") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(s.accounts, key = { it.id }) { acc ->
                    val isActive = acc.id == s.activeLogin
                    GhCard(onClick = { vm.switchAccount(acc.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = acc.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.name ?: acc.login, fontWeight = FontWeight.SemiBold)
                                Text("@${acc.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (acc.scopes.isNotEmpty())
                                    Text(
                                        "${acc.scopes.size} scope(s) · ${acc.tokenType ?: "token"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            }
                            if (isActive) {
                                GhBadge("Active", MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                            }
                            // PAT viewer icon
                            IconButton(onClick = { showPatFor = acc }) {
                                Icon(
                                    Icons.Filled.Key,
                                    contentDescription = "View PAT",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { inspect = acc }) {
                                Icon(Icons.Filled.Info, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Inspect token")
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.logoutActive() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Logout, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign out current account")
                    }
                }
            }
            EmbeddedTerminal(section = "Accounts")
        }
    }

    inspect?.let { acc ->
        TokenInspectorDialog(account = acc, vm = vm, onDismiss = { inspect = null })
    }

    showPatFor?.let { acc ->
        PatViewerDialog(account = acc, onDismiss = { showPatFor = null })
    }
}

// ─── PAT viewer dialog ───────────────────────────────────────────────────────

@Composable
private fun PatViewerDialog(account: Account, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var revealed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Column {
                Text("Personal Access Token")
                Text(
                    "@${account.login}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Token type + expiry chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GhBadge(account.tokenType ?: "classic", MaterialTheme.colorScheme.tertiary)
                    account.tokenExpiry?.let { GhBadge("expires $it", MaterialTheme.colorScheme.error) }
                }

                // Token display card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Token",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Toggle visibility
                                IconButton(
                                    onClick = { revealed = !revealed },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (revealed) "Hide token" else "Show token",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Copy
                                IconButton(
                                    onClick = {
                                        ShareUtils.copyToClipboard(ctx, account.token, "PAT copied")
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = "Copy PAT",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (revealed) {
                            SelectionContainer {
                                Text(
                                    account.token,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            // Masked — show first 4 + dots + last 4
                            val masked = if (account.token.length > 8)
                                account.token.take(4) + "•".repeat(account.token.length - 8) + account.token.takeLast(4)
                            else
                                "•".repeat(account.token.length)
                            Text(
                                masked,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    "Never share your token. Anyone who has it has full access to this GitHub account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ─── Token inspector dialog (unchanged) ──────────────────────────────────────

@Composable
private fun TokenInspectorDialog(account: Account, vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var revalidating by remember { mutableStateOf(false) }
    val acc = vm.state.collectAsState().value.accounts.firstOrNull { it.id == account.id } ?: account

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@${acc.login}") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(
                enabled = !revalidating,
                onClick = {
                    scope.launch {
                        revalidating = true
                        vm.revalidateActive()
                        revalidating = false
                    }
                }
            ) {
                if (revalidating) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Re-validate") }
            }
        },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GhBadge(acc.tokenType ?: "token", MaterialTheme.colorScheme.tertiary)
                        if (acc.lastValidatedAt > 0) GhBadge("validated " + RelativeTime.format(acc.lastValidatedAt))
                        acc.tokenExpiry?.let { GhBadge("expires $it", MaterialTheme.colorScheme.error) }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Permissions (${acc.scopes.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { ShareUtils.copyToClipboard(ctx, acc.scopes.joinToString(","), "Scopes") }) {
                            Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Copy")
                        }
                    }
                }
                if (acc.scopes.isEmpty()) item {
                    Text(
                        "No OAuth scopes returned. Fine-grained tokens declare permissions per repository instead — view at github.com/settings/tokens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(acc.scopes) { sc ->
                    val info = ScopeCatalog.describe(sc)
                    val color = when (info.risk) {
                        ScopeCatalog.Risk.CRITICAL, ScopeCatalog.Risk.HIGH -> MaterialTheme.colorScheme.error
                        ScopeCatalog.Risk.MEDIUM -> MaterialTheme.colorScheme.tertiary
                        ScopeCatalog.Risk.LOW -> MaterialTheme.colorScheme.primary
                    }
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GhBadge(sc, color)
                            Text(info.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            GhBadge(info.risk.name, color)
                        }
                        Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    val missing = ScopeCatalog.recommended.filterNot { req -> acc.scopes.any { it == req || it.startsWith("$req:") } }
                    if (missing.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Missing recommended scopes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            missing.forEach { GhBadge(it, MaterialTheme.colorScheme.tertiary) }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Validation history (${acc.validations.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (acc.validations.isEmpty()) item {
                    Text("No validations recorded yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(acc.validations) { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (v.ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                            tint = if (v.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "HTTP ${v.httpCode ?: "?"} · " +
                                    (if (v.ok) "${v.scopes.size} scopes" else (v.error ?: "failed")),
                                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "${RelativeTime.format(v.ts)}  ·  rate=${v.rateLimit ?: "?"}/${v.rateMax ?: "?"}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}
