package com.githubcontrol.ui.screens.commits

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.CommitsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitsScreen(owner: String, name: String, branch: String, onBack: () -> Unit, onOpenCommit: (String) -> Unit, vm: CommitsViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, branch) { vm.load(owner, name, branch) }
    val s by vm.state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= s.commits.size - 3) vm.loadMore(owner, name) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Commits • ${branch.ifBlank { "default" }}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (s.loading && s.commits.isEmpty()) {
                LoadingIndicator()
            } else {
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                LazyColumn(state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(s.commits, key = { it.sha }) { c ->
                        GhCard(onClick = { onOpenCommit(c.sha) }) {
                            Text(c.commit.message.lineSequence().firstOrNull() ?: c.commit.message, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text("${c.commit.author.name} • ${RelativeTime.ago(c.commit.author.date)} • ${c.sha.take(7)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (s.loading) item { LoadingIndicator() }
                }
            }
        }
    }
}
