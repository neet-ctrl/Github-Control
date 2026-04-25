package com.githubcontrol.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.viewmodel.SearchKind
import com.githubcontrol.viewmodel.SearchViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onNavigate: (String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val s by vm.state.collectAsState()

    // Debounced auto-search: re-runs 400ms after the user stops typing or switches kind.
    // Skips empty queries and trims to ≥2 chars to avoid hammering the API.
    LaunchedEffect(s.q, s.kind) {
        val q = s.q.trim()
        if (q.length < 2) return@LaunchedEffect
        delay(400)
        vm.search()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Search") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp)) {
            OutlinedTextField(s.q, { vm.setQ(it) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Type at least 2 characters — auto-searches as you type") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), trailingIcon = { TextButton(onClick = { vm.search() }) { Text("Go") } })
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = SearchKind.values().indexOf(s.kind)) {
                SearchKind.values().forEach { k -> Tab(selected = k == s.kind, onClick = { vm.setKind(k) }, text = { Text(k.name) }) }
            }
            Spacer(Modifier.height(8.dp))
            if (s.loading) {
                LoadingIndicator()
            } else {
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (s.total > 0) Text("${s.total} matches", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (s.kind) {
                    SearchKind.REPOS -> items(s.repos) { r ->
                        GhCard(onClick = { onNavigate(Routes.repoDetail(r.owner.login, r.name)) }) {
                            Text(r.fullName, style = MaterialTheme.typography.titleMedium)
                            if (r.description != null) Text(r.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Text("★ ${r.stars}  ⑂ ${r.forks}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    SearchKind.CODE -> items(s.code) { c ->
                        GhCard(onClick = { onNavigate(Routes.preview(c.repository.owner.login, c.repository.name, c.path)) }) {
                            Text("${c.repository.fullName}: ${c.path}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    SearchKind.USERS -> items(s.users) { u ->
                        GhCard {
                            Text(u.login, style = MaterialTheme.typography.titleMedium)
                            Text(u.name ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                }
            }
        }
    }
}
