package com.githubcontrol.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.viewmodel.NotificationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, vm: NotificationsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val s by vm.state.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Notifications") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { FilterChip(selected = s.all, onClick = { vm.toggleAll() }, label = { Text(if (s.all) "All" else "Unread") }) })
    }) { pad ->
        if (s.loading) {
            Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
        } else {
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.items, key = { it.id }) { n ->
                GhCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(n.subject.title, style = MaterialTheme.typography.titleSmall)
                            Text("${n.repository.fullName} • ${n.subject.type}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (n.unread) GhBadge("new", MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { vm.markRead(n.id) }) { Icon(Icons.Filled.MarkEmailRead, null) }
                    }
                }
            }
        }
        }
    }
}
