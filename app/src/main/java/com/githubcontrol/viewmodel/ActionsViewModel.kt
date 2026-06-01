package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhWorkflow
import com.githubcontrol.data.api.GhWorkflowRun
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ActionsState(
    val loading: Boolean = false,
    val workflows: List<GhWorkflow> = emptyList(),
    val runs: List<GhWorkflowRun> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val bulkDeleting: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val actionInFlight: Set<Long> = emptySet()
)

@HiltViewModel
class ActionsViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(ActionsState())
    val state: StateFlow<ActionsState> = _state

    private var currentOwner = ""
    private var currentName  = ""

    fun load(owner: String, name: String) {
        currentOwner = owner
        currentName  = name
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val w = repo.workflows(owner, name).workflows
                val r = repo.allWorkflowRuns(owner, name)
                _state.value = _state.value.copy(loading = false, workflows = w, runs = r)
            } catch (t: Throwable) {
                Logger.e("Actions", "load failed", t)
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun reload() = load(currentOwner, currentName)

    // ---- Dispatch ----

    fun dispatch(owner: String, name: String, id: Long, ref: String) {
        viewModelScope.launch {
            runCatching { repo.dispatchWorkflow(owner, name, id, ref) }
                .onSuccess { _state.value = _state.value.copy(message = if (it.isSuccessful) "Dispatched" else "Failed ${it.code()}") }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            reload()
        }
    }

    // ---- Per-run actions ----

    fun deleteRun(runId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight + runId)
            runCatching { repo.deleteWorkflowRun(currentOwner, currentName, runId) }
                .onSuccess {
                    Logger.i("Actions", "deleted run #$runId")
                    _state.value = _state.value.copy(
                        runs = _state.value.runs.filter { it.id != runId },
                        message = "Run deleted"
                    )
                }
                .onFailure {
                    Logger.e("Actions", "delete run failed", it)
                    _state.value = _state.value.copy(error = it.message)
                }
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight - runId)
        }
    }

    fun cancelRun(runId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight + runId)
            runCatching { repo.cancelWorkflowRun(currentOwner, currentName, runId) }
                .onSuccess { _state.value = _state.value.copy(message = "Run cancelled"); reload() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight - runId)
        }
    }

    fun rerunRun(runId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight + runId)
            runCatching { repo.rerunWorkflowRun(currentOwner, currentName, runId) }
                .onSuccess { _state.value = _state.value.copy(message = "Run re-queued"); reload() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
            _state.value = _state.value.copy(actionInFlight = _state.value.actionInFlight - runId)
        }
    }

    // ---- Selection mode ----

    fun toggleSelectionMode() {
        _state.value = _state.value.copy(
            selectionMode = !_state.value.selectionMode,
            selectedIds   = emptySet()
        )
    }

    fun toggleSelection(id: Long) {
        val cur = _state.value.selectedIds
        _state.value = _state.value.copy(selectedIds = if (id in cur) cur - id else cur + id)
    }

    // ---- Bulk delete variants ----

    fun deleteSelected() {
        val ids = _state.value.selectedIds.toList()
        bulkDelete(ids, "Deleted ${ids.size} run(s)")
    }

    fun deleteNewest(n: Int) {
        val ids = _state.value.runs
            .filter { it.status == "completed" }
            .sortedByDescending { parseDate(it.createdAt) }
            .take(n).map { it.id }
        bulkDelete(ids, "Deleted $n newest run(s)")
    }

    fun deleteOldest(n: Int) {
        val ids = _state.value.runs
            .filter { it.status == "completed" }
            .sortedBy { parseDate(it.createdAt) }
            .take(n).map { it.id }
        bulkDelete(ids, "Deleted $n oldest run(s)")
    }

    fun deleteByDateRange(fromDate: String?, toDate: String?) {
        val from = fromDate?.let { runCatching { Instant.parse("${it}T00:00:00Z") }.getOrNull() }
        val to   = toDate?.let   { runCatching { Instant.parse("${it}T23:59:59Z") }.getOrNull() }
        val ids = _state.value.runs.filter { run ->
            if (run.status != "completed") return@filter false
            val t = parseDate(run.createdAt) ?: return@filter false
            val afterFrom = from == null || !t.isBefore(from)
            val beforeTo  = to   == null || !t.isAfter(to)
            afterFrom && beforeTo
        }.map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} run(s) in date range")
    }

    fun deleteByConclusion(conclusion: String) {
        val ids = _state.value.runs
            .filter { it.status == "completed" && it.conclusion == conclusion }
            .map { it.id }
        bulkDelete(ids, "Deleted ${ids.size} '$conclusion' run(s)")
    }

    fun deleteAll() {
        val ids = _state.value.runs.filter { it.status == "completed" }.map { it.id }
        bulkDelete(ids, "Deleted all ${ids.size} completed run(s)")
    }

    private fun parseDate(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

    private fun bulkDelete(ids: List<Long>, successMsg: String) {
        if (ids.isEmpty()) {
            _state.value = _state.value.copy(message = "No matching completed runs")
            return
        }
        val idsSet = ids.toSet()
        viewModelScope.launch {
            _state.value = _state.value.copy(bulkDeleting = true, error = null)
            var deleted = 0
            val errors  = mutableListOf<String>()
            for (id in ids) {
                runCatching {
                    repo.deleteWorkflowRun(currentOwner, currentName, id)
                    Logger.i("Actions", "bulk-deleted run #$id")
                    deleted++
                }.onFailure {
                    Logger.e("Actions", "bulk-delete #$id failed", it)
                    errors.add(it.message ?: "error")
                }
            }
            _state.value = _state.value.copy(
                bulkDeleting  = false,
                runs          = _state.value.runs.filter { it.id !in idsSet },
                selectionMode = false,
                selectedIds   = emptySet(),
                message       = if (errors.isEmpty()) successMsg else "$deleted deleted, ${errors.size} failed",
                error         = errors.firstOrNull()
            )
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
