package com.githubcontrol.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.utils.SettingsBackup
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Multi-page first-launch tutorial. On the last page the user can either tap
 * "Let's go" to proceed to the login screen or "Import backup" to import a
 * JSON backup file and sign in to all their accounts automatically.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    main: MainViewModel = hiltViewModel()
) {
    val pages      = remember { onboardingPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope      = rememberCoroutineScope()
    val ctx        = LocalContext.current

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
                    SettingsBackup.apply(main.accountManager, backup)
                }.onSuccess { added ->
                    main.accountManager.setOnboardingCompleted(true)
                    if (added > 0) {
                        main.refresh()
                        onFinish()              // jump straight into the app
                    } else {
                        importMsg = "No new accounts found in the backup. Please sign in manually."
                        main.accountManager.setOnboardingCompleted(true)
                        onFinish()
                    }
                }.onFailure {
                    importMsg = "Import failed: ${it.message}"
                }
                importBusy = false
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Top bar — skip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GitHub Control", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (pagerState.currentPage < pages.lastIndex) {
                    TextButton(onClick = {
                        scope.launch { main.accountManager.setOnboardingCompleted(true); onFinish() }
                    }) { Text("Skip") }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { i ->
                OnboardingPage(pages[i])
            }

            // Indicator dots
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                pages.indices.forEach { i ->
                    val active = i == pagerState.currentPage
                    val width by animateFloatAsState(
                        targetValue = if (active) 22f else 8f,
                        animationSpec = tween(220), label = "dot"
                    )
                    Box(
                        Modifier.padding(horizontal = 4.dp).height(8.dp).width(width.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                    )
                }
            }

            // Bottom action bar
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                // Import message
                importMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }

                val isLast = pagerState.currentPage == pages.lastIndex

                // On last page: show import button above the main actions
                if (isLast) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        enabled = !importBusy,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        if (importBusy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Importing backup…")
                        } else {
                            Icon(Icons.Filled.Restore, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import backup & sign in automatically")
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) } },
                        enabled = pagerState.currentPage > 0
                    ) { Text("Back") }

                    Button(
                        onClick = {
                            if (isLast) {
                                scope.launch { main.accountManager.setOnboardingCompleted(true); onFinish() }
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (isLast) "Let's go" else "Next")
                        Spacer(Modifier.width(6.dp))
                        Icon(if (isLast) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPageData) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(page.color.copy(alpha = 0.85f), page.color.copy(alpha = 0.35f))))
                .border(2.dp, page.color.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(56.dp)) }

        Text(page.title, style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(page.subtitle, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            page.bullets.forEach { (icon, text) -> BulletCard(icon, text, page.color) }
        }

        AnimatedVisibility(visible = page.tip != null) {
            page.tip?.let {
                Surface(shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletCard(icon: ImageVector, text: String, accent: Color) {
    Surface(shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

private data class OnboardingPageData(
    val title: String, val subtitle: String, val icon: ImageVector,
    val color: Color, val bullets: List<Pair<ImageVector, String>>, val tip: String? = null
)

private fun onboardingPages(): List<OnboardingPageData> = listOf(
    OnboardingPageData(
        title = "Welcome to GitHub Control",
        subtitle = "A native Android client that puts every GitHub feature in your pocket — no browser required.",
        icon = Icons.Filled.VerifiedUser, color = Color(0xFF388BFD),
        bullets = listOf(
            Icons.Filled.CloudUpload to "Upload entire folders to any repo, preserving the full structure",
            Icons.Filled.Speed       to "Browse repos, commits, PRs, issues, and run GitHub Actions",
            Icons.Filled.Shield      to "Multi-account, biometric-locked, encrypted token vault"
        ),
        tip = "Everything happens directly between your phone and api.github.com — no third-party server."
    ),
    OnboardingPageData(
        title = "Sign in with a token",
        subtitle = "GitHub uses Personal Access Tokens (PATs). The next screen has a step-by-step guide and a one-tap link that opens GitHub with all the right scopes pre-selected.",
        icon = Icons.Filled.Security, color = Color(0xFF8957E5),
        bullets = listOf(
            Icons.Filled.CheckCircle to "Tap \"Open GitHub with all scopes pre-selected\"",
            Icons.Filled.CheckCircle to "Pick an expiry, click Generate, copy the token",
            Icons.Filled.CheckCircle to "Paste it back into the field — done."
        ),
        tip = "Your token is stored in AndroidX EncryptedSharedPreferences (AES-256-GCM) on this device only."
    ),
    OnboardingPageData(
        title = "Permissions, your way",
        subtitle = "Settings → Permissions shows every permission the app could use, with a one-tap Grant button per row. Nothing is requested up front.",
        icon = Icons.Filled.Shield, color = Color(0xFF3FB950),
        bullets = listOf(
            Icons.Filled.CheckCircle to "Notifications — see upload + sync progress",
            Icons.Filled.CheckCircle to "Storage — pick files / folders to upload",
            Icons.Filled.CheckCircle to "Battery exemption — keep big uploads alive"
        )
    ),
    OnboardingPageData(
        title = "Make it look like you",
        subtitle = "Settings → Appearance has light/dark/AMOLED, Material You dynamic colors, 11 accent palettes, density, corner radius, text + monospace scale, and 8 terminal palettes.",
        icon = Icons.Filled.Palette, color = Color(0xFFD29922),
        bullets = listOf(
            Icons.Filled.CheckCircle to "Per-screen theme that follows your system or stays fixed",
            Icons.Filled.CheckCircle to "Larger text + bigger touch targets in one tap",
            Icons.Filled.CheckCircle to "Reset everything to defaults any time"
        )
    ),
    OnboardingPageData(
        title = "Home-screen widgets",
        subtitle = "Long-press your home screen → Widgets → GitHub Control. Three sizes: small, medium, and a full dashboard with live upload progress.",
        icon = Icons.Filled.Widgets, color = Color(0xFFEC6547),
        bullets = listOf(
            Icons.Filled.CheckCircle to "Small (2×1) — status pill + Sync button",
            Icons.Filled.CheckCircle to "Medium (4×2) — repo, last commit, Upload / Sync / Open",
            Icons.Filled.CheckCircle to "Large (4×3) — live progress bar, recent activity, full action grid"
        ),
        tip = "Tapping any widget button opens the app and runs the action — uploads use the system file picker for safety."
    ),
    OnboardingPageData(
        title = "A few power tips",
        subtitle = "Things that aren't obvious but make the app fly.",
        icon = Icons.Filled.TouchApp, color = Color(0xFF388BFD),
        bullets = listOf(
            Icons.Filled.CheckCircle to "Pull down on any list to refresh from GitHub",
            Icons.Filled.CheckCircle to "Long-press a file in the explorer for the multi-select bar",
            Icons.Filled.CheckCircle to "Tap the version 5× on the About screen to enable debug",
            Icons.Filled.CheckCircle to "Settings → Health & status shows API rate, errors, last sync",
            Icons.Filled.CheckCircle to "Settings → Backup & restore exports ALL settings + accounts with PATs"
        )
    ),
    OnboardingPageData(
        title = "You're ready",
        subtitle = "Tap \"Let's go\" to sign in with a PAT, or \"Import backup\" below to restore all your accounts at once from a previous backup.",
        icon = Icons.Filled.CheckCircle, color = Color(0xFF3FB950),
        bullets = listOf(
            Icons.Filled.Restore     to "Already have a backup? Import it below — all accounts sign in automatically",
            Icons.Filled.CheckCircle to "Made by Shakti Kumar — coder847402@gmail.com",
            Icons.Filled.CheckCircle to "Open source, no telemetry, no ads"
        )
    )
)
