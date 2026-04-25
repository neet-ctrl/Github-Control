package com.githubcontrol.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.utils.RelativeTime
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.UserProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    login: String,
    onBack: () -> Unit,
    onOpenRepo: (String, String) -> Unit,
    vm: UserProfileViewModel = hiltViewModel()
) {
    LaunchedEffect(login) { vm.load(login) }
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState, s.repos.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (s.repos.isNotEmpty() && last >= s.repos.size - 3) vm.loadMore(login) }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("@$login", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { ShareUtils.openInBrowser(ctx, "https://github.com/$login") }) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser")
                }
            }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                s.loading && s.user == null -> Box(Modifier.fillMaxSize()) { LoadingIndicator("Loading profile") }
                s.user == null -> Box(Modifier.fillMaxSize()) {
                    Text(
                        s.error ?: "User not found.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    val u = s.user!!
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            GhCard {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AsyncImage(
                                        model = u.avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(CircleShape)
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(u.name ?: u.login, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("@${u.login}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!u.bio.isNullOrBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(u.bio!!, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GhBadge("repos: ${u.publicRepos}")
                                    GhBadge("followers: ${u.followers}")
                                    GhBadge("following: ${u.following}")
                                }
                                if (!u.company.isNullOrBlank() || !u.location.isNullOrBlank() || !u.blog.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    if (!u.company.isNullOrBlank()) Text("🏢 ${u.company}", style = MaterialTheme.typography.bodySmall)
                                    if (!u.location.isNullOrBlank()) Text("📍 ${u.location}", style = MaterialTheme.typography.bodySmall)
                                    if (!u.blog.isNullOrBlank()) Text("🔗 ${u.blog}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        item {
                            Text(
                                "Repositories",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, start = 4.dp)
                            )
                        }
                        s.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        if (s.repos.isEmpty() && !s.loading) item {
                            Text(
                                "No public repositories.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        items(s.repos, key = { it.id }) { r ->
                            GhCard(onClick = { onOpenRepo(r.owner.login, r.name) }) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (r.description != null) {
                                    Text(r.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (r.private) GhBadge("Private", MaterialTheme.colorScheme.tertiary)
                                    if (r.fork) GhBadge("Fork")
                                    if (r.archived) GhBadge("Archived", Color(0xFFD29922))
                                    r.language?.let { GhBadge(it, Color(0xFF3FB950)) }
                                    Spacer(Modifier.weight(1f))
                                    Text("★ ${r.stars}", style = MaterialTheme.typography.labelMedium)
                                }
                                Text(
                                    "updated ${RelativeTime.ago(r.pushedAt ?: r.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (s.loading && s.repos.isNotEmpty()) item { LoadingIndicator() }
                    }
                }
            }
        }
    }
}
