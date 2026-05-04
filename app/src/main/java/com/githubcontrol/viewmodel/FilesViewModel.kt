package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.DeleteFileRequest
import com.githubcontrol.data.api.GhBranch
import com.githubcontrol.data.api.GhContent
import com.githubcontrol.data.api.PutFileRequest
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.fromBase64
import com.githubcontrol.utils.toBase64
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilesState(
    val loading: Boolean = false,
    val owner: String = "",
    val name: String = "",
    val path: String = "",
    val ref: String = "",
    val items: List<GhContent> = emptyList(),
    val branches: List<GhBranch> = emptyList(),
    val selection: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FilesViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(FilesState())
    val state: StateFlow<FilesState> = _state

    fun load(owner: String, name: String, path: String, ref: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, owner = owner, name = name, path = path, ref = ref, error = null)
            try {
                val items = repo.contents(owner, name, path, ref.ifBlank { null })
                    .sortedWith(compareByDescending<GhContent> { it.type == "dir" }.thenBy { it.name.lowercase() })
                val branches = if (_state.value.branches.isEmpty()) runCatching { repo.branches(owner, name) }.getOrDefault(emptyList()) else _state.value.branches
                _state.value = _state.value.copy(loading = false, items = items, branches = branches)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun toggleMultiSelect() { _state.value = _state.value.copy(multiSelect = !_state.value.multiSelect, selection = emptySet()) }
    fun toggleSelect(path: String) {
        val cur = _state.value.selection
        _state.value = _state.value.copy(selection = if (cur.contains(path)) cur - path else cur + path)
    }
    fun clearSelection() { _state.value = _state.value.copy(selection = emptySet()) }

    fun setRef(branch: String) {
        val s = _state.value
        load(s.owner, s.name, s.path, branch)
    }

    fun deletePath(path: String, sha: String, message: String, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            try {
                val branch = s.ref.ifBlank { null }
                android.util.Log.d("FilesVM", "deleteFile path=$path sha=$sha branch=$branch")
                repo.api.deleteFile(s.owner, s.name, path, DeleteFileRequest(message, sha, branch))
                load(s.owner, s.name, s.path, s.ref); onDone()
            } catch (t: Throwable) {
                val detail = if (t is retrofit2.HttpException)
                    runCatching { t.response()?.errorBody()?.string() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "HTTP ${t.code()}"
                else t.message
                android.util.Log.e("FilesVM", "deleteFile failed: $detail", t)
                _state.value = _state.value.copy(error = "Delete failed: $detail")
            }
        }
    }

    fun renamePath(oldPath: String, sha: String, newPath: String, message: String, contentBytes: ByteArray, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            try {
                repo.api.putFile(s.owner, s.name, newPath, PutFileRequest(message, contentBytes.toBase64(), null, s.ref.ifBlank { null }))
                repo.api.deleteFile(s.owner, s.name, oldPath, DeleteFileRequest(message, sha, s.ref.ifBlank { null }))
                load(s.owner, s.name, s.path, s.ref); onDone()
            } catch (t: Throwable) { _state.value = _state.value.copy(error = t.message) }
        }
    }

    /**
     * Convenience overload: fetches the file's bytes from GitHub itself, then commits a
     * new path + deletes the old one. Used by the swipe-rename UI in [FilesScreen].
     */
    fun renamePath(item: GhContent, newPath: String, message: String, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            try {
                val full = repo.fileContent(s.owner, s.name, item.path, s.ref.ifBlank { null })
                val bytes = full.content?.fromBase64() ?: ByteArray(0)
                repo.api.putFile(s.owner, s.name, newPath, PutFileRequest(message, bytes.toBase64(), null, s.ref.ifBlank { null }))
                repo.api.deleteFile(s.owner, s.name, item.path, DeleteFileRequest(message, item.sha, s.ref.ifBlank { null }))
                load(s.owner, s.name, s.path, s.ref); onDone()
            } catch (t: Throwable) { _state.value = _state.value.copy(error = t.message) }
        }
    }

    fun deleteSelected(message: String, onDone: () -> Unit) {
        val s = _state.value
        val selectedItems = s.items.filter { s.selection.contains(it.path) }
        if (selectedItems.isEmpty()) { onDone(); return }
        viewModelScope.launch {
            try {
                val branch = s.ref.ifBlank { "HEAD" }
                val paths = expandPaths(s.owner, s.name, s.ref, selectedItems)
                android.util.Log.d("FilesVM", "deleteSelected ${paths.size} paths on branch $branch")
                val ops: List<Pair<String, ByteArray?>> = paths.map { it to null }
                repo.commitFiles(s.owner, s.name, branch, ops, message, null, null)
                clearSelection()
                load(s.owner, s.name, s.path, s.ref); onDone()
            } catch (t: Throwable) {
                val detail = if (t is retrofit2.HttpException)
                    runCatching { t.response()?.errorBody()?.string() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "HTTP ${t.code()}"
                else t.message
                android.util.Log.e("FilesVM", "deleteSelected failed: $detail", t)
                _state.value = _state.value.copy(error = "Delete failed: $detail")
            }
        }
    }

    /**
     * Delete a folder and everything inside it as one atomic commit.
     * GitHub has no "delete folder" REST endpoint — we have to enumerate the
     * folder via the Git Tree API and emit a tree commit that removes every
     * blob under that path.
     */
    fun deleteFolder(folderPath: String, message: String, onDone: () -> Unit) {
        val s = _state.value
        viewModelScope.launch {
            try {
                val branchName = s.ref.ifBlank { "HEAD" }
                val branchInfo = repo.api.branch(s.owner, s.name, branchName)
                val parent = repo.api.commitDetail(s.owner, s.name, branchInfo.commit.sha)
                val ft = repo.api.gitTree(s.owner, s.name, parent.commit.tree.sha, recursive = 1)
                val prefix = folderPath.trim('/') + "/"
                val toDelete = ft.tree
                    .filter { it.type == "blob" && it.path.startsWith(prefix) }
                    .map { it.path }
                android.util.Log.d("FilesVM", "deleteFolder $folderPath: ${toDelete.size} blobs on branch $branchName")
                if (toDelete.isEmpty()) {
                    _state.value = _state.value.copy(error = "Folder is empty or already deleted")
                    onDone(); return@launch
                }
                val ops: List<Pair<String, ByteArray?>> = toDelete.map { it to null }
                repo.commitFiles(s.owner, s.name, branchName, ops, message, null, null)
                load(s.owner, s.name, s.path, s.ref); onDone()
            } catch (t: Throwable) {
                val detail = if (t is retrofit2.HttpException)
                    runCatching { t.response()?.errorBody()?.string() }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "HTTP ${t.code()}"
                else t.message
                android.util.Log.e("FilesVM", "deleteFolder failed: $detail", t)
                _state.value = _state.value.copy(error = "Delete folder failed: $detail")
            }
        }
    }

    /**
     * Resolve a mixed selection of files + directories into a flat list of
     * blob paths (so a single tree commit can delete everything atomically).
     */
    private suspend fun expandPaths(
        owner: String, name: String, ref: String, items: List<GhContent>
    ): List<String> {
        val files = items.filter { it.type == "file" }.map { it.path }
        val dirs = items.filter { it.type == "dir" }
        if (dirs.isEmpty()) return files
        val branchInfo = repo.api.branch(owner, name, ref.ifBlank { "HEAD" })
        val parent = repo.api.commitDetail(owner, name, branchInfo.commit.sha)
        val ft = repo.api.gitTree(owner, name, parent.commit.tree.sha, recursive = 1)
        val dirPaths = dirs.map { it.path.trim('/') + "/" }
        val nested = ft.tree.filter { it.type == "blob" && dirPaths.any { p -> it.path.startsWith(p) } }
            .map { it.path }
        return (files + nested).distinct()
    }
}
