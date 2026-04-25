package com.githubcontrol.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.components.SectionHeader
import com.githubcontrol.ui.components.StatPill
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.viewmodel.DashboardViewModel
import com.githubcontrol.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    main: MainViewModel,
    onNavigate: (String) -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { vm.load() }
    val s by vm.state.collectAsState()
    val authState by main.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SEARCH) }) { Icon(Icons.Filled.Search, null) }
                    IconButton(onClick = { onNavigate(Routes.NOTIFICATIONS) }) {
                        BadgedBox(badge = { if (s.unreadNotifications > 0) Badge { Text(s.unreadNotifications.toString()) } }) {
                            Icon(Icons.Filled.Notifications, null)
                        }
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, null) }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { onNavigate(Routes.CREATE_REPO) }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New repo") })
        }
    ) { pad ->
        if (s.loading && s.user == null) {
            Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator("Loading dashboard") }
        } else {
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GhCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = authState.activeAvatar, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(s.user?.name ?: s.user?.login ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("@${s.user?.login ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onNavigate(Routes.ACCOUNTS) }) { Icon(Icons.Filled.Group, null) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatPill("repos", s.totalRepos.toString())
                        StatPill("followers", (s.user?.followers ?: 0).toString())
                        StatPill("following", (s.user?.following ?: 0).toString())
                        Spacer(Modifier.weight(1f))
                        s.rateRemaining?.let { GhBadge("API: $it") }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.AutoMirrored.Filled.List, "Repos") { onNavigate(Routes.REPOS) }
                    QuickAction(Icons.Filled.Search, "Search") { onNavigate(Routes.SEARCH) }
                    QuickAction(Icons.Filled.Terminal, "Command") { onNavigate(Routes.COMMAND) }
                    QuickAction(Icons.Filled.Bolt, "Sync") { onNavigate(Routes.SYNC) }
                }
            }
            item { SectionHeader("Recent repositories") { TextButton(onClick = { onNavigate(Routes.REPOS) }) { Text("View all") } } }
            items(s.recent, key = { it.id }) { r ->
                GhCard(onClick = { onNavigate(Routes.repoDetail(r.owner.login, r.name)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (r.description != null) Text(r.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (r.private) GhBadge("Private", MaterialTheme.colorScheme.tertiary)
                                if (r.archived) GhBadge("Archived", Color(0xFFD29922))
                                if (r.fork) GhBadge("Fork")
                                r.language?.let { GhBadge(it, Color(0xFF3FB950)) }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("★ ${r.stars}", style = MaterialTheme.typography.labelMedium)
                            Text(RelativeTime.ago(r.pushedAt ?: r.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
        }
    }
}

@Composable
private fun RowScope.QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.weight(1f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
