package com.githubcontrol.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton service that runs branch-import jobs in a scope tied to the application,
 * not to any individual screen. The import survives navigation (back/forward) and is
 * automatically paused then retried when the network drops and comes back.
 */
@Singleton
class BranchImportService @Inject constructor(
    private val repo: GitHubRepository,
    @ApplicationContext private val context: Context
) {
    data class ImportState(
        val active:           Boolean = false,
        val paused:           Boolean = false,
        val progress:         String? = null,
        val progressFraction: Float?  = null,
        val lastResult:       String? = null,
        val lastError:        String? = null,
        val targetOwner:      String  = "",
        val targetRepo:       String  = "",
        val newBranch:        String  = "",
        val sourceOwner:      String  = "",
        val sourceRepo:       String  = "",
        val sourceBranch:     String  = ""
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state

    @Volatile private var networkAvailable = true

    init {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkAvailable = cm.activeNetwork != null

        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(req, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkAvailable = true
                Logger.d("BranchImport", "Network available")
                if (_state.value.paused && _state.value.active) {
                    _state.value = _state.value.copy(paused = false, progress = "Network restored — continuing…")
                }
            }
            override fun onLost(network: Network) {
                networkAvailable = false
                Logger.d("BranchImport", "Network lost")
                if (_state.value.active) {
                    _state.value = _state.value.copy(paused = true, progress = "Waiting for network connection…")
                }
            }
        })
    }

    fun startImport(
        sourceOwner: String, sourceRepo: String, sourceBranch: String,
        targetOwner: String, targetRepo: String, newBranch: String
    ) {
        currentJob?.cancel()
        _state.value = ImportState(
            active = true,
            progress = "Starting import…",
            sourceOwner = sourceOwner, sourceRepo = sourceRepo, sourceBranch = sourceBranch,
            targetOwner = targetOwner, targetRepo = targetRepo, newBranch = newBranch
        )

        currentJob = scope.launch {
            var attempt = 0
            while (isActive) {
                attempt++
                if (attempt > 1) {
                    _state.value = _state.value.copy(
                        paused = false,
                        progress = "Retrying import (attempt $attempt)…"
                    )
                }

                val result = runCatching {
                    repo.importBranchFromRepo(
                        sourceOwner  = sourceOwner,
                        sourceRepo   = sourceRepo,
                        sourceBranch = sourceBranch,
                        targetOwner  = targetOwner,
                        targetRepo   = targetRepo,
                        newBranch    = newBranch,
                        onProgress   = { msg, fraction ->
                            ensureActive()
                            _state.value = _state.value.copy(
                                progress         = msg,
                                progressFraction = fraction
                            )
                        }
                    )
                }

                when {
                    result.isSuccess -> {
                        _state.value = ImportState(
                            lastResult = "Branch '$newBranch' imported from $sourceOwner/$sourceRepo"
                        )
                        Logger.i("BranchImport", "Import succeeded after $attempt attempt(s)")
                        return@launch
                    }
                    else -> {
                        val err = result.exceptionOrNull()
                        if (!isActive) return@launch

                        val isNetwork = err is IOException || err is SocketException ||
                            err?.cause is IOException || err?.cause is SocketException
                        if (isNetwork) {
                            Logger.w("BranchImport", "Network error on attempt $attempt: ${err?.message}")
                            _state.value = _state.value.copy(
                                paused   = true,
                                progress = "Network lost — waiting to reconnect…"
                            )
                            // Wait until network is back
                            while (!networkAvailable && isActive) { delay(1500) }
                            if (!isActive) return@launch
                            // Brief pause before retry
                            delay(1000)
                        } else {
                            Logger.e("BranchImport", "Non-retryable error: ${err?.message}", err)
                            _state.value = ImportState(
                                lastError = "Import failed: ${err?.message ?: "unknown error"}"
                            )
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        _state.value = ImportState()
        Logger.i("BranchImport", "Import cancelled")
    }

    fun clearResult() {
        _state.value = _state.value.copy(lastResult = null, lastError = null)
    }
}
