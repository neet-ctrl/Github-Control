package com.githubcontrol.ui.screens.actions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.ActionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(owner: String, name: String, onBack: () -> Unit, vm: ActionsViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()
    var dispatchTarget by remember { mutableStateOf<Long?>(null) }
    var ref by remember { mutableStateOf("main") }

    Scaffold(topBar = { TopAppBar(title = { Text("Actions") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        if (s.loading) {
            Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
        } else {
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            Text("Workflows", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 240.dp)) {
                items(s.workflows, key = { it.id }) { w ->
                    GhCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleSmall)
                                Text(w.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { dispatchTarget = w.id }) { Icon(Icons.Filled.PlayArrow, null) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Recent runs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.runs, key = { it.id }) { r ->
                    GhCard {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(r.name ?: "Run #${r.runNumber}", style = MaterialTheme.typography.titleSmall)
                                Text("${r.headBranch ?: ""} • ${RelativeTime.ago(r.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GhBadge(r.conclusion ?: r.status, when (r.conclusion) {
                                "success" -> Color(0xFF3FB950); "failure" -> MaterialTheme.colorScheme.error; "cancelled" -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.primary
                            })
                        }
                    }
                }
            }
        }
        if (dispatchTarget != null) {
            AlertDialog(
                onDismissRequest = { dispatchTarget = null },
                title = { Text("Dispatch workflow") },
                text = { OutlinedTextField(ref, { ref = it }, label = { Text("Ref/branch") }) },
                confirmButton = { TextButton(onClick = { vm.dispatch(owner, name, dispatchTarget!!, ref); dispatchTarget = null }) { Text("Run") } },
                dismissButton = { TextButton(onClick = { dispatchTarget = null }) { Text("Cancel") } }
            )
        }
        }
    }
}
