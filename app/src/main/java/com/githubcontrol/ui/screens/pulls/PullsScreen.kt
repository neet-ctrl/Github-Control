package com.githubcontrol.ui.screens.pulls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.PullsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullsScreen(owner: String, name: String, onBack: () -> Unit, onOpen: (Int) -> Unit, onCreate: () -> Unit, vm: PullsViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf("open") }
    LaunchedEffect(tab) { vm.load(owner, name, tab) }
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pull Requests") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Filled.Add, null) } }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = listOf("open", "closed", "all").indexOf(tab)) {
                listOf("open", "closed", "all").forEach { Tab(selected = tab == it, onClick = { tab = it }, text = { Text(it.replaceFirstChar { c -> c.uppercase() }) }) }
            }
            if (s.loading) {
                LoadingIndicator()
            } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.list, key = { it.id }) { pr ->
                    GhCard(onClick = { onOpen(pr.number) }) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("#${pr.number}  ${pr.title}", style = MaterialTheme.typography.titleMedium)
                                Text("${pr.user?.login ?: "?"} • ${RelativeTime.ago(pr.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GhBadge(pr.state, when (pr.state) { "open" -> MaterialTheme.colorScheme.primary; "closed" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary })
                        }
                    }
                }
            }
            }
        }
    }
}
