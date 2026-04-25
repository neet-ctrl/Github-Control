package com.githubcontrol.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(main: MainViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val theme by main.accountManager.themeFlow.collectAsState(initial = "system")
    val biometric by main.accountManager.biometricEnabledFlow.collectAsState(initial = false)
    val dangerous by main.accountManager.dangerousModeFlow.collectAsState(initial = false)
    val autoLock by main.accountManager.autoLockMinutesFlow.collectAsState(initial = 5)
    val scope = rememberCoroutineScope()
    var wipeDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GhCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system", "light", "dark").forEach { mode ->
                        FilterChip(selected = theme == mode, onClick = { scope.launch { main.accountManager.setTheme(mode) } }, label = { Text(mode) })
                    }
                }
            }
            GhCard {
                Text("Security", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ListItem(headlineContent = { Text("Biometric unlock") }, trailingContent = { Switch(biometric, { scope.launch { main.accountManager.setBiometric(it) } }) })
                ListItem(headlineContent = { Text("Dangerous mode (destructive ops)") }, trailingContent = { Switch(dangerous, { scope.launch { main.accountManager.setDangerous(it) } }) })
                ListItem(headlineContent = { Text("Auto-lock (minutes)") }, trailingContent = {
                    OutlinedTextField(autoLock.toString(), { v -> v.toIntOrNull()?.let { scope.launch { main.accountManager.setAutoLockMinutes(it) } } }, modifier = Modifier.width(80.dp), singleLine = true)
                })
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
                Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { wipeDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Text("Wipe all accounts and data")
                }
            }
        }
    }
    if (wipeDialog) {
        AlertDialog(
            onDismissRequest = { wipeDialog = false },
            title = { Text("Wipe everything?") },
            text = { Text("This removes all stored accounts, tokens, and local data. Cannot be undone.") },
            confirmButton = { TextButton(onClick = { main.wipeAll(); wipeDialog = false }) { Text("Wipe", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { wipeDialog = false }) { Text("Cancel") } }
        )
    }
}
