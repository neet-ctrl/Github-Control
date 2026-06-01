package com.githubcontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.githubcontrol.notifications.DeepLinkBus
import com.githubcontrol.notifications.SshKeyNotificationService
import com.githubcontrol.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

// FragmentActivity (which extends ComponentActivity) is required so that
// androidx.biometric.BiometricPrompt can attach its dialog fragment for the
// unlock flow used by [BiometricScreen]. Plain ComponentActivity silently
// falls through to "auto-unlock", defeating the security gate.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleDeepLinkIntent(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val route = intent?.getStringExtra(SshKeyNotificationService.EXTRA_ROUTE) ?: return
        lifecycleScope.launch { DeepLinkBus.pendingRoute.emit(route) }
    }
}
