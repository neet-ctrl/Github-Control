package com.githubcontrol.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.data.auth.AccountManager
import com.githubcontrol.ui.components.PatternLock
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.security.MessageDigest

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private enum class LockMethod { PIN, PASSWORD, PATTERN }
private enum class SetupStep  { CHOOSE, ENTER, CONFIRM, DONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSetupScreen(
    onBack: () -> Unit,
    main: MainViewModel = hiltViewModel()
) {
    val am    = main.accountManager
    val scope = rememberCoroutineScope()

    var step        by remember { mutableStateOf(SetupStep.CHOOSE) }
    var method      by remember { mutableStateOf<LockMethod?>(null) }
    var firstInput  by remember { mutableStateOf("") }
    var secondInput by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // PIN pad state
    var pin         by remember { mutableStateOf("") }

    // Password state
    var pwVisible   by remember { mutableStateOf(false) }

    // Pattern reset signals
    var enterReset   by remember { mutableIntStateOf(0) }
    var confirmReset by remember { mutableIntStateOf(0) }

    val appLockEnabled  by am.appLockEnabledFlow.collectAsState(initial = false)
    val appLockMethod   by am.appLockMethodFlow.collectAsState(initial = "pin")

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (step == SetupStep.DONE) "App Lock" else "Set up App Lock") },
            navigationIcon = {
                IconButton(onClick = {
                    if (step == SetupStep.ENTER || step == SetupStep.CONFIRM) {
                        step = SetupStep.CHOOSE; firstInput = ""; secondInput = ""; pin = ""; errorMsg = null
                    } else onBack()
                }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            }
        )
    }) { pad ->
        AnimatedContent(targetState = step, label = "step") { s ->
            when (s) {

                // ── Step 0: Choose method ────────────────────────────────────
                SetupStep.CHOOSE -> {
                    Column(
                        Modifier.padding(pad).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (appLockEnabled) {
                            // Already set up — show current config
                            DoneCard(method = appLockMethod, onDisable = {
                                scope.launch { am.clearAppLock(); onBack() }
                            }, onChange = {
                                step = SetupStep.CHOOSE
                                method = null
                            })
                            HorizontalDivider()
                            Text("Change lock method:", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(
                                "Choose how you want to lock the app",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "You will be asked for this every time you open the app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        MethodCard(
                            icon = Icons.Filled.Pin, title = "PIN",
                            description = "4-digit numeric code — fastest to enter.",
                            onClick = { method = LockMethod.PIN; step = SetupStep.ENTER; pin = ""; errorMsg = null }
                        )
                        MethodCard(
                            icon = Icons.Filled.Lock, title = "Password",
                            description = "Text password with letters, numbers, and symbols.",
                            onClick = { method = LockMethod.PASSWORD; step = SetupStep.ENTER; firstInput = ""; errorMsg = null }
                        )
                        MethodCard(
                            icon = Icons.Filled.GridView, title = "Pattern",
                            description = "Draw a pattern connecting at least 4 dots.",
                            onClick = { method = LockMethod.PATTERN; step = SetupStep.ENTER; firstInput = ""; enterReset++; errorMsg = null }
                        )
                    }
                }

                // ── Step 1: Enter ────────────────────────────────────────────
                SetupStep.ENTER -> {
                    Column(
                        Modifier.padding(pad).fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            when (method) {
                                LockMethod.PIN      -> "Enter a 4-digit PIN"
                                LockMethod.PASSWORD -> "Enter a password"
                                LockMethod.PATTERN  -> "Draw your pattern"
                                null -> ""
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "You will be asked for this each time you open the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        errorMsg?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        when (method) {
                            LockMethod.PIN -> PinInput(
                                pin = pin,
                                onDigit = { if (pin.length < 4) pin += it },
                                onBack  = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                onDone  = {
                                    if (pin.length == 4) { firstInput = pin; pin = ""; step = SetupStep.CONFIRM; errorMsg = null }
                                    else errorMsg = "PIN must be 4 digits"
                                }
                            )
                            LockMethod.PASSWORD -> {
                                OutlinedTextField(
                                    value = firstInput,
                                    onValueChange = { firstInput = it },
                                    label = { Text("Password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { pwVisible = !pwVisible }) {
                                            Icon(if (pwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                                        }
                                    }
                                )
                                Button(
                                    onClick = {
                                        if (firstInput.length < 4) errorMsg = "Password must be at least 4 characters"
                                        else { step = SetupStep.CONFIRM; secondInput = ""; errorMsg = null }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Next") }
                            }
                            LockMethod.PATTERN -> PatternLock(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                resetSignal = enterReset,
                                onPatternComplete = {
                                    firstInput = it.joinToString(""); step = SetupStep.CONFIRM
                                    confirmReset++; errorMsg = null
                                }
                            )
                            null -> {}
                        }
                    }
                }

                // ── Step 2: Confirm ──────────────────────────────────────────
                SetupStep.CONFIRM -> {
                    Column(
                        Modifier.padding(pad).fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            when (method) {
                                LockMethod.PIN      -> "Confirm your PIN"
                                LockMethod.PASSWORD -> "Confirm your password"
                                LockMethod.PATTERN  -> "Draw the pattern again"
                                null -> ""
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        errorMsg?.let {
                            Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        when (method) {
                            LockMethod.PIN -> PinInput(
                                pin = pin,
                                onDigit = { if (pin.length < 4) pin += it },
                                onBack  = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                onDone  = {
                                    if (pin == firstInput) {
                                        scope.launch {
                                            am.setAppLock(true, "pin", sha256(pin))
                                            step = SetupStep.DONE
                                        }
                                    } else { pin = ""; errorMsg = "PINs don't match — try again" }
                                }
                            )
                            LockMethod.PASSWORD -> {
                                OutlinedTextField(
                                    value = secondInput,
                                    onValueChange = { secondInput = it },
                                    label = { Text("Confirm password") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { pwVisible = !pwVisible }) {
                                            Icon(if (pwVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                                        }
                                    }
                                )
                                Button(
                                    onClick = {
                                        if (secondInput == firstInput) {
                                            scope.launch {
                                                am.setAppLock(true, "password", sha256(firstInput))
                                                step = SetupStep.DONE
                                            }
                                        } else { secondInput = ""; errorMsg = "Passwords don't match — try again" }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Confirm") }
                            }
                            LockMethod.PATTERN -> PatternLock(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                resetSignal = confirmReset,
                                onPatternComplete = { confirm ->
                                    val confirmStr = confirm.joinToString("")
                                    if (confirmStr == firstInput) {
                                        scope.launch {
                                            am.setAppLock(true, "pattern", sha256(firstInput))
                                            step = SetupStep.DONE
                                        }
                                    } else { confirmReset++; errorMsg = "Pattern doesn't match — try again" }
                                }
                            )
                            null -> {}
                        }
                    }
                }

                // ── Step 3: Done ─────────────────────────────────────────────
                SetupStep.DONE -> {
                    Column(
                        Modifier.padding(pad).fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(Modifier.height(24.dp))
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LockPerson, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp))
                        }
                        Text("App lock is active!", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            "Next time you open the app you will be asked for your " +
                            when (method) {
                                LockMethod.PIN      -> "PIN."
                                LockMethod.PASSWORD -> "password."
                                LockMethod.PATTERN  -> "pattern."
                                null -> "lock."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                        TextButton(onClick = {
                            step = SetupStep.CHOOSE; method = null; firstInput = ""; secondInput = ""; pin = ""; errorMsg = null
                        }) { Text("Change method") }
                        TextButton(onClick = { scope.launch { am.clearAppLock(); onBack() } }) {
                            Text("Disable app lock", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DoneCard(method: String, onDisable: () -> Unit, onChange: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LockPerson, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("App lock is active", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text("Method: ${method.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDisable) {
                    Text("Disable", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onChange) { Text("Change") }
            }
        }
    }
}

@Composable
private fun PinInput(
    pin: String,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Dot indicators
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }

        // Numpad
        val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { d ->
                        val enabled = d.isNotEmpty()
                        FilledTonalButton(
                            onClick = {
                                when (d) {
                                    "⌫"  -> onBack()
                                    else -> if (pin.length < 4) onDigit(d)
                                }
                            },
                            enabled = enabled,
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) {
                            Text(d, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Confirm") }
    }
}
