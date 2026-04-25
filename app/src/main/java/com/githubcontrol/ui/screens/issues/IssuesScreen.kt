package com.githubcontrol.ui.screens.issues

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
import com.githubcontrol.viewmodel.IssuesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(owner: String, name: String, onBack: () -> Unit, onOpen: (Int) -> Unit, onCreate: () -> Unit, vm: IssuesViewModel = hiltViewModel()) {
    var tab by remember { mutableStateOf("open") }
    LaunchedEffect(tab) { vm.load(owner, name, tab) }
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Issues") },
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
                items(s.list, key = { it.id }) { i ->
                    GhCard(onClick = { onOpen(i.number) }) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("#${i.number}  ${i.title}", style = MaterialTheme.typography.titleMedium)
                                Text("${i.user?.login ?: "?"} • ${RelativeTime.ago(i.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            GhBadge(i.state, if (i.state == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (i.labels.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                i.labels.take(4).forEach { l -> GhBadge(l.name) }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
