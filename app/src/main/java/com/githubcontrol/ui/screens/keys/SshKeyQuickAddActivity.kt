package com.githubcontrol.ui.screens.keys

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SshKeyQuickAddViewModel @Inject constructor(
    private val repo: GitHubRepository
) : ViewModel() {
    var saving by mutableStateOf(false)
    var keyCount by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            runCatching { keyCount = repo.sshKeys().size }
        }
    }

    fun add(
        title: String,
        key: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            saving = true
            runCatching {
                repo.addSshKey(title, key.trim())
                Logger.i("SshKeyQuickAdd", "added key '$title'")
                keyCount++
                onSuccess()
            }.onFailure {
                Logger.e("SshKeyQuickAdd", "add failed", it)
                onError(it.message ?: "Unknown error")
            }
            saving = false
        }
    }
}

@AndroidEntryPoint
class SshKeyQuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            MaterialTheme {
                SshKeyQuickAddContent(
                    onDismiss = { finish() },
                    onToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Composable
private fun SshKeyQuickAddContent(
    vm: SshKeyQuickAddViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onToast: (String) -> Unit
) {
    var keyField   by remember { mutableStateOf("") }
    var titleField by remember { mutableStateOf("") }
    var addAnother by remember { mutableStateOf(false) }

    fun resetFields() {
        keyField   = ""
        titleField = ""
        addAnother = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Key, null) },
        title = { Text("Add SSH Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = titleField,
                    onValueChange = { titleField = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Auto: Key #${vm.keyCount + 1}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keyField,
                    onValueChange = { keyField = it },
                    label = { Text("Public key (ssh-rsa AAAA…)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
                if (addAnother) {
                    Text(
                        "Key added! Fill in the next key or tap Done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                TextButton(
                    enabled = keyField.isNotBlank() && !vm.saving,
                    onClick = {
                        val resolvedTitle = titleField.ifBlank { "Key #${vm.keyCount + 1}" }
                        vm.add(
                            title = resolvedTitle,
                            key   = keyField,
                            onSuccess = {
                                onToast("SSH key added successfully")
                                resetFields()
                                addAnother = true
                            },
                            onError = { err ->
                                onToast("Failed: $err")
                            }
                        )
                    }
                ) { Text("Add") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
