package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.db.SnippetDao
import com.githubcontrol.data.db.SnippetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetsState(
    val all: List<SnippetEntity> = emptyList(),
    val filter: String = "",
    /** Non-null for a brief moment to show "saved" snackbar feedback. */
    val justSavedLabel: String? = null
)

@HiltViewModel
class SnippetsViewModel @Inject constructor(
    private val dao: SnippetDao
) : ViewModel() {

    private val _state = MutableStateFlow(SnippetsState())
    val state: StateFlow<SnippetsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { list ->
                _state.update { it.copy(all = list) }
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Flow of snippets visible while viewing a specific repo (repo + global). */
    fun forRepo(owner: String, repo: String): Flow<List<SnippetEntity>> =
        dao.observeForRepo(owner, repo)

    val filtered: StateFlow<List<SnippetEntity>> = _state
        .map { s ->
            val q = s.filter.trim().lowercase()
            if (q.isEmpty()) s.all
            else s.all.filter { sn ->
                sn.label.lowercase().contains(q) ||
                sn.command.lowercase().contains(q) ||
                sn.description.lowercase().contains(q) ||
                (sn.owner?.lowercase()?.contains(q) == true) ||
                (sn.repo?.lowercase()?.contains(q) == true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun save(
        label: String,
        command: String,
        description: String,
        owner: String?,
        repo: String?
    ) {
        viewModelScope.launch {
            dao.insert(
                SnippetEntity(
                    label = label.trim(),
                    command = command.trim(),
                    description = description.trim(),
                    owner = owner?.takeIf { it.isNotBlank() },
                    repo = repo?.takeIf { it.isNotBlank() }
                )
            )
            _state.update { it.copy(justSavedLabel = label.trim()) }
        }
    }

    fun update(entity: SnippetEntity) {
        viewModelScope.launch { dao.update(entity) }
    }

    fun delete(entity: SnippetEntity) {
        viewModelScope.launch { dao.delete(entity) }
    }

    fun setFilter(q: String) = _state.update { it.copy(filter = q) }

    fun clearJustSaved() = _state.update { it.copy(justSavedLabel = null) }
}
