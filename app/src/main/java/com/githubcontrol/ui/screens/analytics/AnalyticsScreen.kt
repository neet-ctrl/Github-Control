package com.githubcontrol.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.viewmodel.AnalyticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(owner: String, name: String, onBack: () -> Unit, vm: AnalyticsViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name) { vm.load(owner, name) }
    val s by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Analytics") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }) }) { pad ->
        if (s.loading) {
            Box(Modifier.padding(pad).fillMaxSize()) { LoadingIndicator() }
        } else {
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GhCard {
                Text("API Rate Limit", style = MaterialTheme.typography.titleMedium)
                Text("${s.rateRemaining ?: "?"} requests remaining")
            }
            GhCard {
                Text("Languages", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val total = s.languages.values.sum().coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
                    val palette = listOf(Color(0xFF3FB950), Color(0xFF2F81F7), Color(0xFFD2A8FF), Color(0xFFD29922), Color(0xFFF85149), Color(0xFF8B949E))
                    s.languages.entries.sortedByDescending { it.value }.forEachIndexed { idx, (_, v) ->
                        Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(palette[idx % palette.size]))
                    }
                }
                Spacer(Modifier.height(8.dp))
                s.languages.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                    Text("$k — ${(v * 100 / total).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            GhCard {
                Text("Top contributors", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                s.contributors.take(10).forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.login ?: "?", modifier = Modifier.weight(1f))
                        Text("${c.contributions} commits", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        }
    }
}
