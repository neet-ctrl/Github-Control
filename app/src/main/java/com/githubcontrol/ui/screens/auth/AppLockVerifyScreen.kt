package com.githubcontrol.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.PatternLock
import com.githubcontrol.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.security.MessageDigest

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockVerifyScreen(
    onVerified: () -> Unit,
    main: MainViewModel = hiltViewModel()
) {
    val am     = main.accountManager
    val scope  = rememberCoroutineScope()
    val method by am.appLockMethodFlow.collectAsState(initial = "pin")

    var pin        by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var pwVisible  by remember { mutableStateOf(false) }
    var patReset   by remember { mutableIntStateOf(0) }
    var errorMsg   by remember { mutableStateOf<String?>(null) }
    var attempts   by remember { mutableIntStateOf(0) }
    var securityDialog  by remember { mutableStateOf(false) }
    var securityAnswer  by remember { mutableStateOf("") }
    var securityError   by remember { mutableStateOf<String?>(null) }

    suspend fun verify(input: String): Boolean {
        val stored = am.getAppLockHash() ?: return false
        return sha256(input) == stored
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Lock", fontWeight = FontWeight.Bold) }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Lock icon
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp))
            }

            Text(
                when (method) {
                    "pin"      -> "Enter your PIN"
                    "password" -> "Enter your password"
                    "pattern"  -> "Draw your pattern"
                    else       -> "Verify app lock"
                },
                style     = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )

            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center)
            }

            when (method) {

                "pin" -> PinVerifyInput(
                    pin    = pin,
                    onDigit = { if (pin.length < 4) pin += it },
                    onBack  = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onDone  = {
                        scope.launch {
                            if (verify(pin)) {
                                main.unlock(); onVerified()
                            } else {
                                attempts++; pin = ""
                                errorMsg = "Incorrect PIN. Attempt $attempts."
                            }
                        }
                    }
                )

                "password" -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
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
                            scope.launch {
                                if (verify(password)) {
                                    main.unlock(); onVerified()
                                } else {
                                    attempts++; password = ""
                                    errorMsg = "Incorrect password. Attempt $attempts."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = password.isNotBlank()
                    ) { Text("Unlock") }
                }

                "pattern" -> {
                    PatternLock(
                        modifier  = Modifier.fillMaxWidth(0.8f),
                        resetSignal = patReset,
                        onPatternComplete = { indices ->
                            scope.launch {
                                val input = indices.joinToString("")
                                if (verify(input)) {
                                    main.unlock(); onVerified()
                                } else {
                                    attempts++; patReset++
                                    errorMsg = "Incorrect pattern. Attempt $attempts."
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = { securityDialog = true; securityAnswer = ""; securityError = null }) {
                Text("I forgot my ${method.replaceFirstChar { it.uppercase() }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Security question dialog
    if (securityDialog) {
        AlertDialog(
            onDismissRequest = { securityDialog = false },
            icon = { Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Security Question") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tell Your Best Friend Name",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = securityAnswer,
                        onValueChange = { securityAnswer = it; securityError = null },
                        label = { Text("Your answer") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = securityError != null,
                        supportingText = securityError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val stored = am.getSecurityAnswer()
                        if (securityAnswer.trim().equals(stored.trim(), ignoreCase = true)) {
                            am.clearAppLock()
                            main.unlock()
                            securityDialog = false
                            onVerified()
                        } else {
                            securityError = "Incorrect answer. Try again."
                        }
                    }
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { securityDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PinVerifyInput(
    pin: String,
    onDigit: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
        val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { d ->
                        FilledTonalButton(
                            onClick = { when (d) { "⌫" -> onBack(); else -> onDigit(d) } },
                            enabled = d.isNotEmpty(),
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape
                        ) { Text(d, fontSize = 22.sp, fontWeight = FontWeight.Medium) }
                    }
                }
            }
        }
        Button(
            onClick = onDone,
            enabled = pin.length == 4,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }
    }
}
