package com.githubcontrol.ui.screens.repos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.ByteFormat
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.RepoFilter
import com.githubcontrol.viewmodel.RepoListViewModel
import com.githubcontrol.viewmodel.RepoSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(onBack: () -> Unit, onOpen: (String, String) -> Unit, onCreate: () -> Unit, vm: RepoListViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()
    var showSort by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (last >= s.repos.size - 3) vm.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repositories") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showFilter = true }) { Icon(Icons.Filled.FilterList, null) }
                    IconButton(onClick = { showSort = true }) { Icon(Icons.Filled.Sort, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Filled.Add, null) }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = s.search, onValueChange = { vm.setSearch(it) },
                placeholder = { Text("Filter loaded repos…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            val filtered = s.repos.filter {
                s.search.isBlank() ||
                    it.fullName.contains(s.search, true) ||
                    (it.description?.contains(s.search, true) ?: false)
            }
            if (s.loading && s.repos.isEmpty()) {
                LoadingIndicator()
            } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { r ->
                    GhCard(onClick = { onOpen(r.owner.login, r.name) }) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(r.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (r.description != null) Text(r.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (r.private) GhBadge("Private", MaterialTheme.colorScheme.tertiary) else GhBadge("Public")
                                    if (r.fork) GhBadge("Fork")
                                    if (r.archived) GhBadge("Archived", Color(0xFFD29922))
                                    r.language?.let { GhBadge(it, Color(0xFF3FB950)) }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("★ ${r.stars}")
                                Text("⑂ ${r.forks}", style = MaterialTheme.typography.labelSmall)
                                Text(ByteFormat.human(r.size * 1024L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(RelativeTime.ago(r.pushedAt ?: r.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (s.loading) item { LoadingIndicator() }
                if (s.endReached && filtered.isNotEmpty()) item { Text("End of list", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
            }
            }
        }

        if (showSort) {
            ModalBottomSheet(onDismissRequest = { showSort = false }) {
                Text("Sort by", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                RepoSort.values().forEach { srt ->
                    ListItem(
                        headlineContent = { Text(srt.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        trailingContent = { if (srt == s.sort) RadioButton(selected = true, onClick = null) },
                        modifier = Modifier.clickable { vm.setSort(srt); showSort = false }
                    )
                }
            }
        }
        if (showFilter) {
            ModalBottomSheet(onDismissRequest = { showFilter = false }) {
                Text("Filter", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                RepoFilter.values().forEach { f ->
                    ListItem(
                        headlineContent = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        trailingContent = { if (f == s.filter) RadioButton(selected = true, onClick = null) },
                        modifier = Modifier.clickable { vm.setFilter(f); showFilter = false }
                    )
                }
            }
        }
    }
}

