package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhBranch
import com.githubcontrol.data.api.GhCommit
import com.githubcontrol.data.api.GhCommitCompare
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommitsState(
    val loading: Boolean = false,
    val commits: List<GhCommit> = emptyList(),
    val branch: String = "",
    val branches: List<GhBranch> = emptyList(),
    val defaultBranch: String = "",
    val page: Int = 1,
    val endReached: Boolean = false,
    val error: String? = null
)

data class CommitDetailState(
    val loading: Boolean = false,
    val commit: GhCommit? = null,
    val defaultBranch: String = "",
    val branches: List<GhBranch> = emptyList(),
    val actionInFlight: Boolean = false,
    val actionMessage: String? = null,
    val ignoreWhitespace: Boolean = false,
    val sideBySide: Boolean = false,
    val error: String? = null
)

data class CompareState(
    val loading: Boolean = false,
    val compare: GhCommitCompare? = null,
    val error: String? = null
)

@HiltViewModel
class CommitsViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(CommitsState())
    val state: StateFlow<CommitsState> = _state

    private val _detail = MutableStateFlow(CommitDetailState())
    val detail: StateFlow<CommitDetailState> = _detail

    private val _compare = MutableStateFlow(CompareState())
    val compare: StateFlow<CompareState> = _compare

    fun load(owner: String, name: String, branch: String) {
        viewModelScope.launch {
            val prev = _state.value
            _state.value = CommitsState(
                loading = true,
                branch = branch,
                branches = prev.branches,
                defaultBranch = prev.defaultBranch,
            )
            try {
                val list = repo.commits(owner, name, branch.ifBlank { null }, 1)
                _state.value = _state.value.copy(
                    loading = false,
                    commits = list,
                    page = 1,
                    endReached = list.size < 30,
                )
            } catch (t: Throwable) { _state.value = _state.value.copy(loading = false, error = t.message) }
        }
        // Lazily fetch the branch list and default branch once per repo so the
        // user can switch between branches from the commits screen.
        if (_state.value.branches.isEmpty() || _state.value.defaultBranch.isBlank()) {
            viewModelScope.launch {
                try {
                    val brs = repo.branches(owner, name)
                    val def = runCatching { repo.repo(owner, name).defaultBranch }.getOrNull().orEmpty()
                    _state.value = _state.value.copy(branches = brs, defaultBranch = def)
                } catch (_: Throwable) { /* non-fatal */ }
            }
        }
    }

    fun selectBranch(owner: String, name: String, branch: String) {
        if (branch == _state.value.branch) return
        load(owner, name, branch)
    }

    fun loadMore(owner: String, name: String) {
        val s = _state.value
        if (s.loading || s.endReached) return
        viewModelScope.launch {
            _state.value = s.copy(loading = true)
            try {
                val nextPage = s.page + 1
                val list = repo.commits(owner, name, s.branch.ifBlank { null }, nextPage)
                _state.value = s.copy(loading = false, commits = (s.commits + list).distinctBy { it.sha }, page = nextPage, endReached = list.size < 30)
            } catch (t: Throwable) { _state.value = s.copy(loading = false, error = t.message) }
        }
    }

    fun loadDetail(owner: String, name: String, sha: String) {
        viewModelScope.launch {
            _detail.value = CommitDetailState(loading = true)
            try {
                val c = repo.commitDetail(owner, name, sha)
                _detail.value = CommitDetailState(loading = false, commit = c)
            } catch (t: Throwable) { _detail.value = _detail.value.copy(loading = false, error = t.message) }
        }
        // Fetch default branch + branch list separately so the detail screen can
        // expose the "hard reset default branch" and "create branch from this commit" actions.
        viewModelScope.launch {
            try {
                val def = runCatching { repo.repo(owner, name).defaultBranch }.getOrNull().orEmpty()
                val brs = runCatching { repo.branches(owner, name) }.getOrNull().orEmpty()
                _detail.value = _detail.value.copy(defaultBranch = def, branches = brs)
            } catch (_: Throwable) { /* non-fatal */ }
        }
    }

    fun clearDetailMessage() {
        _detail.value = _detail.value.copy(actionMessage = null)
    }

    /** Force-update the repository's default branch ref to point at the given commit. */
    fun hardResetDefaultBranch(owner: String, name: String, sha: String, onDone: (Boolean) -> Unit = {}) {
        val def = _detail.value.defaultBranch
        if (def.isBlank()) { onDone(false); return }
        viewModelScope.launch {
            _detail.value = _detail.value.copy(actionInFlight = true, actionMessage = null)
            try {
                repo.hardResetBranch(owner, name, def, sha)
                _detail.value = _detail.value.copy(
                    actionInFlight = false,
                    actionMessage = "Hard-reset $def to ${sha.take(7)}",
                )
                onDone(true)
            } catch (t: Throwable) {
                _detail.value = _detail.value.copy(
                    actionInFlight = false,
                    actionMessage = "Hard reset failed: ${t.message}",
                )
                onDone(false)
            }
        }
    }

    /** Create a new branch [newBranch] pointing at [sha]. */
    fun createBranchAtSha(owner: String, name: String, sha: String, newBranch: String, onDone: (Boolean) -> Unit = {}) {
        if (newBranch.isBlank()) { onDone(false); return }
        viewModelScope.launch {
            _detail.value = _detail.value.copy(actionInFlight = true, actionMessage = null)
            try {
                repo.createBranchAtSha(owner, name, newBranch, sha)
                // Reflect the new branch locally so the picker on the commits screen sees it
                // even before a manual refresh.
                val updated = (_detail.value.branches + com.githubcontrol.data.api.GhBranch(
                    name = newBranch,
                    commit = com.githubcontrol.data.api.GhBranchCommit(sha = sha, url = "")
                )).distinctBy { it.name }
                _detail.value = _detail.value.copy(
                    actionInFlight = false,
                    branches = updated,
                    actionMessage = "Created branch '$newBranch' at ${sha.take(7)}",
                )
                onDone(true)
            } catch (t: Throwable) {
                _detail.value = _detail.value.copy(
                    actionInFlight = false,
                    actionMessage = "Create branch failed: ${t.message}",
                )
                onDone(false)
            }
        }
    }

    fun toggleSideBySide() { _detail.value = _detail.value.copy(sideBySide = !_detail.value.sideBySide) }
    fun toggleIgnoreWs() { _detail.value = _detail.value.copy(ignoreWhitespace = !_detail.value.ignoreWhitespace) }

    fun loadCompare(owner: String, name: String, base: String, head: String) {
        viewModelScope.launch {
            _compare.value = CompareState(loading = true)
            try {
                val c = repo.compare(owner, name, base, head)
                _compare.value = CompareState(loading = false, compare = c)
            } catch (t: Throwable) { _compare.value = _compare.value.copy(loading = false, error = t.message) }
        }
    }
}
