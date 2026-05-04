package com.githubcontrol.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.SettingsBackup
import com.githubcontrol.utils.UpdateChecker
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(main: MainViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val theme by main.accountManager.themeFlow.collectAsState(initial = "system")
    val biometric by main.accountManager.biometricEnabledFlow.collectAsState(initial = false)
    val dangerous by main.accountManager.dangerousModeFlow.collectAsState(initial = false)
    val autoLock by main.accountManager.autoLockMinutesFlow.collectAsState(initial = 5)
    val scope = rememberCoroutineScope()
    var wipeDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var lastBackupJson by remember { mutableStateOf<String?>(null) }
    var showExportWarning by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = lastBackupJson
        if (uri != null && text != null) {
            runCatching { SettingsBackup.writeToUri(ctx, uri, text) }
                .onSuccess { Toast.makeText(ctx, "Backup saved (contains your PATs — keep it safe!)", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(ctx, "Failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
        lastBackupJson = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = SettingsBackup.readFromUri(ctx, uri) ?: error("empty file")
                    val backup = SettingsBackup.decode(text)
                    val added = SettingsBackup.apply(main.accountManager, backup)
                    // Refresh auth state so newly added accounts appear immediately
                    main.refresh()
                    added
                }.onSuccess { added ->
                    importResult = if (added > 0)
                        "Settings imported. $added account(s) logged in automatically."
                    else
                        "Settings imported. All accounts were already present — nothing new added."
                    Toast.makeText(ctx, importResult, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(ctx, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GhCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Light · dark · AMOLED, accent color, density, corner radius, text size, terminal palette and Material You.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system", "light", "dark").forEach { mode ->
                        FilterChip(
                            selected = theme == mode,
                            onClick = { scope.launch { main.accountManager.setTheme(mode) } },
                            label = { Text(mode) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onNavigate(Routes.APPEARANCE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open full appearance editor")
                }
            }

            GhCard {
                Text("Security", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Biometric unlock") },
                    trailingContent = { Switch(biometric, { scope.launch { main.accountManager.setBiometric(it) } }) }
                )
                ListItem(
                    headlineContent = { Text("Dangerous mode (destructive ops)") },
                    trailingContent = { Switch(dangerous, { scope.launch { main.accountManager.setDangerous(it) } }) }
                )
                ListItem(
                    headlineContent = { Text("Auto-lock (minutes)") },
                    trailingContent = {
                        OutlinedTextField(
                            autoLock.toString(),
                            { v -> v.toIntOrNull()?.let { scope.launch { main.accountManager.setAutoLockMinutes(it) } } },
                            modifier = Modifier.width(80.dp), singleLine = true
                        )
                    }
                )
                Button(onClick = { main.lock() }, modifier = Modifier.padding(8.dp)) { Text("Lock now") }
            }

            GhCard {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onNavigate(Routes.ACCOUNTS) }, modifier = Modifier.fillMaxWidth()) { Text("Manage accounts & tokens") }
                TextButton(onClick = { onNavigate(Routes.PROFILE_EDIT) }, modifier = Modifier.fillMaxWidth()) { Text("Edit GitHub profile") }
                TextButton(onClick = { onNavigate(Routes.SSH_KEYS) }, modifier = Modifier.fillMaxWidth()) { Text("SSH keys") }
            }

            GhCard {
                Text("Tools", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onNavigate(Routes.SYNC) }, modifier = Modifier.fillMaxWidth()) { Text("Sync jobs") }
                TextButton(onClick = { onNavigate(Routes.PLUGINS) }, modifier = Modifier.fillMaxWidth()) { Text("Plugins") }
                TextButton(onClick = { onNavigate(Routes.DOWNLOADS) }, modifier = Modifier.fillMaxWidth()) { Text("Downloads") }
                TextButton(onClick = { onNavigate(Routes.COMMAND) }, modifier = Modifier.fillMaxWidth()) { Text("Command Mode") }
                TextButton(onClick = { onNavigate(Routes.LOGS) }, modifier = Modifier.fillMaxWidth()) { Text("Terminal log") }
                TextButton(onClick = { onNavigate(Routes.CRASHES) }, modifier = Modifier.fillMaxWidth()) { Text("Crash reports") }
                TextButton(onClick = { onNavigate(Routes.HEALTH) }, modifier = Modifier.fillMaxWidth()) { Text("Health & status dashboard") }
            }

            GhCard {
                Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Export saves all your settings AND your account PATs to a JSON file so a new device can log in automatically when you import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "The exported file contains your live GitHub tokens. Store it securely and never share it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showExportWarning = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Import") }
                }
            }

            GhCard {
                Text("Updates", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Check for a newer release on GitHub. The current version is shown on the About screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = !checkingUpdates,
                    onClick = {
                        checkingUpdates = true
                        updateMessage = null
                        scope.launch {
                            val r = UpdateChecker.check()
                            checkingUpdates = false
                            updateMessage = r.fold(
                                onSuccess = {
                                    if (it.newer) "Update available: v${it.latest}"
                                    else "You're on the latest version (v${it.current})."
                                },
                                onFailure = { e -> "Couldn't check: ${e.message}" }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (checkingUpdates) "Checking…" else "Check for updates") }
                updateMessage?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            GhCard {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Review every permission the app uses and grant the ones that are missing — one tap per permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onNavigate(Routes.PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open permissions hub")
                }
            }

            GhCard {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { onNavigate(Routes.ABOUT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("App info, developer, libraries, links")
                }
            }

            GhCard {
                Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { wipeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe all accounts and data") }
            }
        }
    }

    // ---- Export security-warning dialog ----
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Export includes live tokens") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This backup file will contain all your GitHub Personal Access Tokens in plain text.",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "• Keep the file encrypted or in a secure location.\n" +
                        "• Never share it or upload it to a public service.\n" +
                        "• Anyone with this file can access your GitHub accounts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "When imported on another device, all accounts will be logged in automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportWarning = false
                        scope.launch {
                            val backup = SettingsBackup.snapshot(main.accountManager)
                            lastBackupJson = SettingsBackup.encode(backup)
                            exportLauncher.launch("github-control-backup.json")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("I understand — Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) { Text("Cancel") }
            }
        )
    }

    // ---- Wipe dialog ----
    if (wipeDialog) {
        com.githubcontrol.ui.components.ConfirmTypeDialog(
            title = "Wipe everything?",
            explanation = "This removes all stored accounts, tokens, settings, and local data. This cannot be undone.",
            requiredText = "WIPE",
            confirmLabel = "Wipe",
            onDismiss = { wipeDialog = false },
            onConfirm = { main.wipeAll(); wipeDialog = false }
        )
    }
}
