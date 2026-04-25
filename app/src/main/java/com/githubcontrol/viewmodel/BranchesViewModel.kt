package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhBranch
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
)

@HiltViewModel
class BranchesViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(BranchesState())
    val state: StateFlow<BranchesState> = _state

    fun clearMessages() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    fun load(owner: String, name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val items = repo.branches(owner, name)
                val def = runCatching { repo.repo(owner, name).defaultBranch }.getOrNull().orEmpty()
                _state.value = _state.value.copy(loading = false, items = items, defaultBranch = def)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = friendly(t, "load branches"))
            }
        }
    }

    fun setDefault(owner: String, name: String, branch: String) {
        if (branch == _state.value.defaultBranch) return
        viewModelScope.launch {
            _state.value = _state.value.copy(settingDefault = branch, error = null)
            runCatching { repo.setDefaultBranch(owner, name, branch) }
                .onSuccess { r ->
                    _state.value = _state.value.copy(
                        settingDefault = null,
                        defaultBranch = r.defaultBranch,
                        message = "Default branch set to '${r.defaultBranch}'",
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        settingDefault = null,
                        error = friendly(it, "set '$branch' as default"),
                    )
                }
        }
    }

    fun create(owner: String, name: String, newBranch: String, fromBranch: String) {
        viewModelScope.launch {
            runCatching { repo.createBranch(owner, name, newBranch, fromBranch) }
                .onSuccess {
                    _state.value = _state.value.copy(message = "Created $newBranch")
                    load(owner, name)
                }
                .onFailure { _state.value = _state.value.copy(error = friendly(it, "create branch")) }
        }
    }

    fun delete(owner: String, name: String, branch: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(deleting = branch, error = null)
            runCatching { repo.deleteBranch(owner, name, branch) }
                .onSuccess {
                    _state.value = _state.value.copy(deleting = null, message = "Deleted $branch")
                    load(owner, name)
                }
                .onFailure {
                    _state.value = _state.value.copy(deleting = null, error = friendly(it, "delete '$branch'"))
                }
        }
    }

    fun rename(owner: String, name: String, oldBranch: String, newBranch: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(renaming = oldBranch, error = null, message = "Renaming '$oldBranch' → '$newBranch'…")
            runCatching { repo.renameBranch(owner, name, oldBranch, newBranch) }
                .onSuccess {
                    _state.value = _state.value.copy(renaming = null, message = "Renamed '$oldBranch' → '$newBranch'")
                    load(owner, name)
                }
                .onFailure {
                    _state.value = _state.value.copy(renaming = null, message = null, error = friendly(it, "rename '$oldBranch'"))
                }
        }
    }

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
