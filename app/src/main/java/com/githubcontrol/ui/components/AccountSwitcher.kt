package com.githubcontrol.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.githubcontrol.data.auth.Account

/**
 * Modern multi-account switcher shown in the corner of a top app bar.
 *
 * Renders nothing when there is only one account (or none) — there's nothing to
 * switch to. With 2+ accounts we render a small circular avatar (with a ring
 * accent + tiny switch glyph) that opens a Material 3 dropdown listing every
 * account; tapping a row makes it active, and a "Manage accounts" footer opens
 * the full Accounts screen.
 */
@Composable
fun AccountSwitcher(
    accounts: List<Account>,
    activeId: String?,
    onSwitch: (String) -> Unit,
    onManage: () -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    if (accounts.size <= 1) return

    var open by remember { mutableStateOf(false) }
    val active = accounts.firstOrNull { it.id == activeId } ?: accounts.first()

    Box {
        IconButton(onClick = { open = true }) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            CircleShape
                        )
                ) {
                    if (active.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = active.avatarUrl,
                            contentDescription = "Switch account",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                active.login.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                // Tiny "switch" glyph in the bottom-right corner so the icon's
                // purpose is obvious at a glance.
                Box(
                    Modifier
                        .size(14.dp)
                        .offset(x = 2.dp, y = 2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SwitchAccount,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "Switch account",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider()
            for (acc in accounts) {
                val isActive = acc.id == active.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                acc.name?.takeIf { it.isNotBlank() } ?: acc.login,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                "@${acc.login}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (acc.avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = acc.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        acc.login.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    },
                    trailingIcon = if (isActive) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    onClick = {
                        open = false
                        if (!isActive) onSwitch(acc.id)
                    }
                )
            }
            HorizontalDivider()
            if (onAdd != null) {
                DropdownMenuItem(
                    text = { Text("Add account") },
                    leadingIcon = { Icon(Icons.Filled.Add, null) },
                    onClick = { open = false; onAdd() }
                )
            }
            DropdownMenuItem(
                text = { Text("Manage accounts") },
                leadingIcon = { Icon(Icons.Filled.Group, null) },
                onClick = { open = false; onManage() }
            )
        }
    }
}
