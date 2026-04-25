package com.githubcontrol.ui.screens.pulls

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
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.viewmodel.PullsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullDetailScreen(owner: String, name: String, number: Int, onBack: () -> Unit, vm: PullsViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, number) { vm.loadDetail(owner, name, number) }
    val s by vm.detail.collectAsState()
    var menu by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("PR #$number") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        val pr = s.pull
        when {
            s.loading -> Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
            pr == null -> Box(Modifier.padding(pad).fillMaxSize()) { Text("Loading…", modifier = Modifier.padding(16.dp)) }
            else ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhCard {
                Text(pr.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhBadge(pr.state, when (pr.state) { "open" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.error })
                    if (pr.draft == true) GhBadge("draft")
                    if (pr.merged == true) GhBadge("merged", MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(8.dp))
                Text("${pr.user?.login} wants to merge ${pr.head.label} into ${pr.base.label}")
                Spacer(Modifier.height(8.dp))
                if (pr.body != null) Text(pr.body!!)
            }
            GhCard {
                Text("Changed files (${s.files.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                s.files.forEach { f ->
                    Text("• ${f.filename}  +${f.additions}/−${f.deletions}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.merge(owner, name, number, "merge") }, enabled = pr.state == "open") { Text("Merge") }
                OutlinedButton(onClick = { vm.merge(owner, name, number, "squash") }, enabled = pr.state == "open") { Text("Squash") }
                OutlinedButton(onClick = { vm.merge(owner, name, number, "rebase") }, enabled = pr.state == "open") { Text("Rebase") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pr.state == "open") OutlinedButton(onClick = { vm.close(owner, name, number) }) { Text("Close") }
                else OutlinedButton(onClick = { vm.reopen(owner, name, number) }) { Text("Reopen") }
            }
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        }
    }
}
