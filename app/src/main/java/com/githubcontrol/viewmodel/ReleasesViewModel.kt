package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhCommit
import com.githubcontrol.data.api.GhRelease
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ReleasesState(
    val loading: Boolean = false,
    val releases: List<GhRelease> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val bulkDeleting: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val commits: List<GhCommit> = emptyList(),
    val commitsLoading: Boolean = false
)

@HiltViewModel
class ReleasesViewModel @Inject constructor(
    private val repo: GitHubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReleasesState())
    val state: StateFlow<ReleasesState> = _state.asStateFlow()

    private var owner = ""
    private var repoName = ""

    fun load(owner: String, name: String) {
        this.owner = owner
        this.repoName = name
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val releases = repo.releases(owner, name)
                _state.value = _state.value.copy(loading = false, releases = releases)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun deleteRelease(releaseId: Long) {
        viewModelScope.launch {
            try {
                repo.deleteRelease(owner, repoName, releaseId)
                _state.value = _state.value.copy(
                    releases = _state.value.releases.filter { it.id != releaseId },
                    successMessage = "Release deleted"
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message)
            }
        }
    }

    fun toggleSelection(id: Long) {
        val current = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (id in current) current - id else current + id
        )
    }

    fun toggleSelectionMode() {
        _state.value = _state.value.copy(
            selectionMode = !_state.value.selectionMode,
            selectedIds = emptySet()
        )
    }

    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        bulkDelete(ids, "Deleted ${ids.size} release(s)")
    }

    fun deleteNewest(n: Int) {
        val ids = _state.value.releases
            .sortedByDescending { it.publishedAt ?: it.createdAt }
            .take(n)
            .map { it.id }
        bulkDelete(ids, "Deleted $n newest release(s)")
    }

    fun deleteOldest(n: Int) {
        val ids = _state.value.releases
            .sortedBy { it.publishedAt ?: it.createdAt }
            .take(n)
            .map { it.id }
        bulkDelete(ids, "Deleted $n oldest release(s)")
    }

    fun deleteByDateRange(fromDate: String?, toDate: String?) {
        val from = fromDate?.let { runCatching { Instant.parse("${it}T00:00:00Z") }.getOrNull() }
        val to = toDate?.let { runCatching { Instant.parse("${it}T23:59:59Z") }.getOrNull() }
        val ids = _state.value.releases.filter { release ->
            val publishedInstant = (release.publishedAt ?: release.createdAt).let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@filter false
            val afterFrom = from == null || !publishedInstant.isBefore(from)
            val beforeTo = to == null || !publishedInstant.isAfter(to)
            afterFrom && beforeTo
        }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} release(s) in date range")
    }

    fun deleteAllReleases() {
        val ids = _state.value.releases.map { it.id }
        bulkDelete(ids, "Deleted all ${ids.size} release(s)")
    }

    fun deleteReleasesAfterCommit(commitDate: String) {
        val cutoff = runCatching { Instant.parse(commitDate) }.getOrNull() ?: return
        val ids = _state.value.releases.filter { release ->
            val publishedInstant = (release.publishedAt ?: release.createdAt).let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@filter false
            publishedInstant.isAfter(cutoff)
        }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} release(s) published after the selected commit")
    }

    fun deleteReleasesBeforeCommit(commitDate: String) {
        val cutoff = runCatching { Instant.parse(commitDate) }.getOrNull() ?: return
        val ids = _state.value.releases.filter { release ->
            val publishedInstant = (release.publishedAt ?: release.createdAt).let {
                runCatching { Instant.parse(it) }.getOrNull()
            } ?: return@filter false
            publishedInstant.isBefore(cutoff)
        }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} release(s) published before the selected commit")
    }

    private fun bulkDelete(ids: List<Long>, successMsg: String) {
        if (ids.isEmpty()) {
            _state.value = _state.value.copy(successMessage = "No releases matched the criteria")
            return
        }
        val idsSet = ids.toSet()
        viewModelScope.launch {
            _state.value = _state.value.copy(bulkDeleting = true, error = null)
            var deletedCount = 0
            val errors = mutableListOf<String>()
            for (id in ids) {
                try {
                    repo.deleteRelease(owner, repoName, id)
                    deletedCount++
                } catch (t: Throwable) {
                    errors.add(t.message ?: "Unknown error")
                }
            }
            _state.value = _state.value.copy(
                bulkDeleting = false,
                releases = _state.value.releases.filter { it.id !in idsSet },
                selectionMode = false,
                selectedIds = emptySet(),
                successMessage = if (errors.isEmpty()) successMsg
                    else "$deletedCount deleted, ${errors.size} failed",
                error = errors.firstOrNull()
            )
        }
    }

    fun loadCommits() {
        viewModelScope.launch {
            _state.value = _state.value.copy(commitsLoading = true)
            try {
                val commits = repo.commits(owner, repoName, null, 1, 50)
                _state.value = _state.value.copy(commits = commits, commitsLoading = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(commitsLoading = false, error = t.message)
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, successMessage = null)
    }
}
