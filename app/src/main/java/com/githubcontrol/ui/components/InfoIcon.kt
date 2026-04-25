package com.githubcontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Small "i" info button that opens a popup with [title] / [body] when tapped.
 * Used throughout the app to explain settings without cluttering the row.
 */
@Composable
fun InfoIcon(title: String, body: String) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "About: $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Got it") } }
        )
    }
}

/**
 * Convenience wrapper that lays out `[Text label] [InfoIcon]` on one line.
 * Use as the headlineContent of a ListItem when each row needs an explanation.
 */
@Composable
fun LabelWithInfo(
    label: String,
    infoTitle: String = label,
    infoBody: String,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(2.dp))
        InfoIcon(infoTitle, infoBody)
    }
}
