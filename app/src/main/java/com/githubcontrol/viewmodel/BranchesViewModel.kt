package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhBranch
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.service.BranchImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class BranchesState(
    val loading: Boolean = false,
    val items: List<GhBranch> = emptyList(),
    val defaultBranch: String = "",
    val message: String? = null,
    val error: String? = null,
    val renaming: String? = null,
    val deleting: String? = null,
    val settingDefault: String? = null,
    // Live import state mirrored from BranchImportService
    val importing: Boolean = false,
    val importPaused: Boolean = false,
    val importProgress: String? = null,
    val importResult: String? = null,
    val importError: String? = null
)

@HiltViewModel
class BranchesViewModel @Inject constructor(
    private val repo: GitHubRepository,
    val importService: BranchImportService
) : ViewModel() {

    private val _local = MutableStateFlow(BranchesState())
    val state: StateFlow<BranchesState>

    init {
        // Combine local state with live import service state
        val combined = MutableStateFlow(BranchesState())
        viewModelScope.launch {
            combine(_local, importService.state) { local, imp ->
                local.copy(
                    importing      = imp.active,
                    importPaused   = imp.paused,
                    importProgress = imp.progress,
                    importResult   = imp.lastResult,
                    importError    = imp.lastError
                )
            }.collect { combined.value = it }
        }
        state = combined
    }

    fun clearMessages() {
        _local.value = _local.value.copy(message = null, error = null)
        importService.clearResult()
    }

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _local.value = _local.value.copy(loading = true, error = null)
            try {
                val items = repo.branches(owner, name)
                val def   = runCatching { repo.repo(owner, name).defaultBranch }.getOrNull().orEmpty()
                _local.value = _local.value.copy(loading = false, items = items, defaultBranch = def)
            } catch (t: Throwable) {
                _local.value = _local.value.copy(loading = false, error = friendly(t, "load branches"))
            }
        }
    }

    fun setDefault(owner: String, name: String, branch: String) {
        if (branch == _local.value.defaultBranch) return
        viewModelScope.launch {
            _local.value = _local.value.copy(settingDefault = branch, error = null)
            runCatching { repo.setDefaultBranch(owner, name, branch) }
                .onSuccess { r ->
                    _local.value = _local.value.copy(
                        settingDefault = null,
                        defaultBranch  = r.defaultBranch,
                        message        = "Default branch set to '${r.defaultBranch}'"
                    )
                }
                .onFailure {
                    _local.value = _local.value.copy(settingDefault = null, error = friendly(it, "set '$branch' as default"))
                }
        }
    }

    fun create(owner: String, name: String, newBranch: String, fromBranch: String) {
        viewModelScope.launch {
            runCatching { repo.createBranch(owner, name, newBranch, fromBranch) }
                .onSuccess { _local.value = _local.value.copy(message = "Created $newBranch"); load(owner, name) }
                .onFailure { _local.value = _local.value.copy(error = friendly(it, "create branch")) }
        }
    }

    fun delete(owner: String, name: String, branch: String) {
        viewModelScope.launch {
            _local.value = _local.value.copy(deleting = branch, error = null)
            runCatching { repo.deleteBranch(owner, name, branch) }
                .onSuccess { _local.value = _local.value.copy(deleting = null, message = "Deleted $branch"); load(owner, name) }
                .onFailure { _local.value = _local.value.copy(deleting = null, error = friendly(it, "delete '$branch'")) }
        }
    }

    fun rename(owner: String, name: String, oldBranch: String, newBranch: String) {
        viewModelScope.launch {
            _local.value = _local.value.copy(renaming = oldBranch, error = null, message = "Renaming '$oldBranch' → '$newBranch'…")
            runCatching { repo.renameBranch(owner, name, oldBranch, newBranch) }
                .onSuccess { _local.value = _local.value.copy(renaming = null, message = "Renamed '$oldBranch' → '$newBranch'"); load(owner, name) }
                .onFailure { _local.value = _local.value.copy(renaming = null, message = null, error = friendly(it, "rename '$oldBranch'")) }
        }
    }

    /**
     * Parse a GitHub repo URL in any of these formats and return Pair(owner, repo):
     *   https://github.com/owner/repo
     *   https://github.com/owner/repo.git
     *   github.com/owner/repo
     *   owner/repo
     */
    fun parseRepoUrl(raw: String): Pair<String, String>? {
        val cleaned = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/")
        if (parts.size < 2) return null
        val owner = parts[0].ifBlank { return null }
        val repo  = parts[1].ifBlank { return null }
        return Pair(owner, repo)
    }

    /**
     * Delegates the branch import to [BranchImportService] — a singleton whose coroutine
     * scope is tied to the application, not to this ViewModel. The import continues even if
     * the user navigates away from BranchesScreen. Network losses are automatically handled
     * with a retry loop.
     */
    fun importBranch(
        targetOwner: String,
        targetName: String,
        sourceUrl: String,
        sourceBranch: String,
        newBranchName: String
    ) {
        val (srcOwner, srcRepo) = parseRepoUrl(sourceUrl)
            ?: run {
                _local.value = _local.value.copy(
                    error = "Could not parse repo URL — use 'https://github.com/owner/repo' or 'owner/repo'"
                )
                return
            }

        importService.startImport(
            sourceOwner  = srcOwner,
            sourceRepo   = srcRepo,
            sourceBranch = sourceBranch,
            targetOwner  = targetOwner,
            targetRepo   = targetName,
            newBranch    = newBranchName
        )
    }

    fun cancelImport() = importService.cancel()

    private fun friendly(t: Throwable, action: String): String {
        if (t is HttpException) {
            return when (t.code()) {
                401 -> "Unauthorized — your token may have expired. Sign in again."
                403 -> "Forbidden — your token is missing 'repo' scope, or the branch is protected."
                404 -> "Not found — the branch may already be gone, or the repo path is wrong."
                409 -> "Conflict — the branch is in use by an open pull request or another rename."
                422 -> "Couldn't $action: GitHub rejected the request. The target name may already exist, the branch may be protected, or it may be the default branch."
                else -> "Couldn't $action (HTTP ${t.code()}): ${t.message()}"
            }
        }
        return t.message ?: "Couldn't $action."
    }
}
