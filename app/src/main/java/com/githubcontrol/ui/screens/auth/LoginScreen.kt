package com.githubcontrol.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.githubcontrol.R
import com.githubcontrol.data.auth.ScopeCatalog
import com.githubcontrol.data.auth.TokenValidator
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.utils.SettingsBackup
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(vm: MainViewModel, onSignedIn: () -> Unit) {
    var token   by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val busy    by vm.loginBusy.collectAsState()
    val err     by vm.loginError.collectAsState()
    val ctx     = LocalContext.current
    val scope   = rememberCoroutineScope()

    var preview  by remember { mutableStateOf<TokenValidator.Result?>(null) }
    var checking by remember { mutableStateOf(false) }
    val tokenType = remember(token) { if (token.isNotBlank()) TokenValidator.classify(token) else null }

    // Backup import launcher
    var importBusy by remember { mutableStateOf(false) }
    var importMsg  by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importBusy = true
                importMsg  = null
                runCatching {
                    val text   = SettingsBackup.readFromUri(ctx, uri) ?: error("empty file")
                    val backup = SettingsBackup.decode(text)
                    SettingsBackup.apply(vm.accountManager, backup)
                }.onSuccess { added ->
                    if (added > 0) {
                        vm.refresh()
                        importMsg = null
                        onSignedIn()           // go straight to the app
                    } else {
                        importMsg = "No new accounts found in the backup file."
                    }
                }.onFailure {
                    importMsg = "Import failed: ${it.message}"
                }
                importBusy = false
            }
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("GitHub Control") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GhCard {
                OutlinedTextField(
                    value = token, onValueChange = { token = it.trim(); preview = null },
                    label = { Text("Personal Access Token") },
                    placeholder = { Text(stringResource(R.string.pat_hint)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    }
                )
                if (tokenType != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GhBadge(tokenType.uppercase(), color = when (tokenType) {
                            "fine-grained" -> MaterialTheme.colorScheme.primary
                            "classic"      -> MaterialTheme.colorScheme.tertiary
                            "unknown"      -> MaterialTheme.colorScheme.error
                            else           -> MaterialTheme.colorScheme.secondary
                        })
                        Text("${token.length} chars", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { checking = true; preview = vm.previewValidate(token); checking = false }
                        },
                        enabled = token.isNotBlank() && !checking,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (checking) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Text("Validate")
                    }
                    Button(
                        onClick = { vm.signInWithToken(token) { onSignedIn() } },
                        enabled = !busy && token.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Text(stringResource(R.string.sign_in))
                    }
                }
            }

            if (err != null) { Text(err!!, color = MaterialTheme.colorScheme.error) }

            preview?.let { p ->
                GhCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (p.ok) Icons.Filled.CheckCircle else Icons.Filled.Error, null,
                            tint = if (p.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(if (p.ok) "Token is valid" else "Token rejected",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            color = if (p.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    if (p.ok) {
                        Spacer(Modifier.height(6.dp))
                        Text("Signed in as @${p.login}  ·  ${p.name ?: "no display name"}")
                        if (p.publicRepos > 0 || p.followers > 0)
                            Text("public repos: ${p.publicRepos}  ·  followers: ${p.followers}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val rl = p.validation.rateLimit; val rm = p.validation.rateMax
                    val expiry = p.validation.tokenExpiry; val type = p.validation.tokenType
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (type != null)   GhBadge(type.uppercase())
                        if (rl   != null)   GhBadge("rate $rl${if (rm != null) "/$rm" else ""}")
                        if (expiry != null) GhBadge("expires $expiry", MaterialTheme.colorScheme.tertiary)
                    }
                    if (p.error != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(p.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (p.scopes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Granted permissions (${p.scopes.size})", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        p.scopes.forEach { sc ->
                            val info = ScopeCatalog.describe(sc)
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
                                GhBadge(sc, color = when (info.risk) {
                                    ScopeCatalog.Risk.CRITICAL, ScopeCatalog.Risk.HIGH -> MaterialTheme.colorScheme.error
                                    ScopeCatalog.Risk.MEDIUM -> MaterialTheme.colorScheme.tertiary
                                    ScopeCatalog.Risk.LOW    -> MaterialTheme.colorScheme.primary
                                })
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(info.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else if (p.ok) {
                        Spacer(Modifier.height(6.dp))
                        Text("GitHub did not return any scope headers — this is normal for fine-grained tokens.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val recommended = ScopeCatalog.recommended.filterNot { req -> p.scopes.any { it == req || it.startsWith("$req:") } }
                    if (p.ok && recommended.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(6.dp))
                            Text("Missing recommended scopes:", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            recommended.forEach { GhBadge(it, MaterialTheme.colorScheme.tertiary) }
                        }
                    }
                }
            }

            // ── Import from backup ─────────────────────────────────────────
            GhCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Restore, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Already have a backup?", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Import a backup file to sign in to all your accounts instantly — no PAT pasting required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                importMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !importBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    if (importBusy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Importing…")
                    } else {
                        Icon(Icons.Filled.Restore, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Import backup file")
                    }
                }
            }

            PatGuideCard()

            EmbeddedTerminal(section = "TokenValidator")
        }
    }
}

@Composable
private fun PatGuideCard() {
    val ctx = LocalContext.current
    var classicOpen     by remember { mutableStateOf(true) }
    var fineGrainedOpen by remember { mutableStateOf(false) }
    val recommended = ScopeCatalog.recommended
    val classicUrl  = "https://github.com/settings/tokens/new" +
        "?scopes=${recommended.joinToString(",")}" +
        "&description=GitHub%20Control%20Android"
    val fineGrainedUrl = "https://github.com/settings/personal-access-tokens/new"

    GhCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Help, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("How to create a Personal Access Token",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text("A PAT is a long string GitHub gives you that lets this app act on your behalf. It is stored encrypted on this device and never sent anywhere else.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(10.dp))
        Button(onClick = { ShareUtils.openInBrowser(ctx, classicUrl) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.OpenInBrowser, null); Spacer(Modifier.width(6.dp))
            Text("Open GitHub with all scopes pre-selected")
        }
        Spacer(Modifier.height(4.dp))
        Text("Opens github.com/settings/tokens/new with every recommended scope already ticked — just set an expiry, click \"Generate token\", copy the token, and paste it above.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(14.dp))
        SectionHeader("Option A — Classic token (easiest)", "recommended", classicOpen) { classicOpen = !classicOpen }
        AnimatedVisibility(visible = classicOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Step(1, "On a browser sign in to your GitHub account.")
                Step(2, "Tap the button above (or go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)).")
                Step(3, "Give it a name like \"GitHub Control on my phone\".")
                Step(4, "Pick an expiry. 90 days is a good balance.")
                Step(5, "Tick every scope listed below. The pre-filled link does this automatically.")
                Step(6, "Click Generate token. GitHub shows the token only once.")
                Step(7, "Copy the token and paste it in the field above.")
                Spacer(Modifier.height(4.dp))
                Text("Scopes you need:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                recommended.forEach { sc ->
                    val info = ScopeCatalog.describe(sc)
                    ScopeRow(sc, info.title, info.description, info.risk)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { copyToClipboard(ctx, "scopes", recommended.joinToString(", ")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Copy the scope list") }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader("Option B — Fine-grained token (more secure)", null, fineGrainedOpen) { fineGrainedOpen = !fineGrainedOpen }
        AnimatedVisibility(visible = fineGrainedOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Step(1, "GitHub → Settings → Developer settings → Fine-grained tokens → Generate new token (or tap below).")
                Step(2, "Token name + expiry.")
                Step(3, "Repository access: All repositories.")
                Step(4, "Repository permissions: Contents, Pull requests, Issues, Workflows, Actions → Read and write.")
                Step(5, "Account permissions: Email addresses, Followers, SSH signing keys → Read.")
                Step(6, "Generate token, copy it, paste it above.")
                OutlinedButton(onClick = { ShareUtils.openInBrowser(ctx, fineGrainedUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.OpenInBrowser, null); Spacer(Modifier.width(6.dp)); Text("Open fine-grained token page")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(6.dp))
            Text("Where the token lives", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
        Text("Stored only on this device using AndroidX EncryptedSharedPreferences (AES-256-GCM). Sent to api.github.com over HTTPS only, never to anyone else.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(title: String, badge: String?, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onToggle, modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (badge != null) { GhBadge(badge, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(6.dp)) }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary)) {
            Text("$number", modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ScopeRow(scope: String, title: String, description: String, risk: ScopeCatalog.Risk) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 3.dp)) {
        GhBadge(scope, color = when (risk) {
            ScopeCatalog.Risk.CRITICAL, ScopeCatalog.Risk.HIGH -> MaterialTheme.colorScheme.error
            ScopeCatalog.Risk.MEDIUM -> MaterialTheme.colorScheme.tertiary
            ScopeCatalog.Risk.LOW    -> MaterialTheme.colorScheme.primary
        })
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
