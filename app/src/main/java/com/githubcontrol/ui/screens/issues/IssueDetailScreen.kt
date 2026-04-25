package com.githubcontrol.ui.screens.issues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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
fun IssueDetailScreen(owner: String, name: String, number: Int, onBack: () -> Unit, vm: IssuesViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, number) { vm.loadDetail(owner, name, number) }
    val s by vm.detail.collectAsState()
    var comment by remember { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Issue #$number") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        val i = s.issue
        when {
            s.loading -> Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
            i == null -> Box(Modifier.padding(pad).fillMaxSize()) { Text("Loading…", modifier = Modifier.padding(16.dp)) }
            else ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhCard {
                Text(i.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GhBadge(i.state, if (i.state == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    i.labels.forEach { GhBadge(it.name) }
                }
                Spacer(Modifier.height(8.dp))
                Text("${i.user?.login} • ${RelativeTime.ago(i.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                if (i.body != null) { Spacer(Modifier.height(8.dp)); Text(i.body!!) }
            }
            s.comments.forEach { c ->
                GhCard {
                    Text("${c.user?.login ?: "?"} • ${RelativeTime.ago(c.createdAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(c.body)
                }
            }
            OutlinedTextField(
                comment, { comment = it }, label = { Text("Comment") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                trailingIcon = { IconButton(onClick = { if (comment.isNotBlank()) { vm.comment(owner, name, number, comment); comment = "" } }) { Icon(Icons.Filled.Send, null) } }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (i.state == "open") OutlinedButton(onClick = { vm.close(owner, name, number) }) { Text("Close issue") }
                else OutlinedButton(onClick = { vm.reopen(owner, name, number) }) { Text("Reopen") }
            }
            s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        }
    }
}
