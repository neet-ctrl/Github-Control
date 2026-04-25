package com.githubcontrol.ui.screens.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.db.AppDatabase
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.ByteFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(val db: AppDatabase) : androidx.lifecycle.ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val items by vm.db.downloads().observe().collectAsState(initial = emptyList())
    Scaffold(topBar = {
        TopAppBar(title = { Text("Downloads") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        if (items.isEmpty()) {
            Text("No downloads yet.", modifier = Modifier.padding(pad).padding(24.dp))
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(items, key = { it.id }) { d ->
                    GhCard {
                        Text(d.path.substringAfterLast('/').ifEmpty { d.path }, style = MaterialTheme.typography.titleSmall)
                        Text("${d.owner}/${d.repo} • ${ByteFormat.human(d.sizeBytes)} • ${d.localPath}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
