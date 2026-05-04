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
        val branch = s.ref.ifBlank { null }
        viewModelScope.launch {
            try {
                // Files: sha is already known from the directory listing.
                val fileItems = selectedItems.filter { it.type == "file" }
                for (item in fileItems) {
                    repo.api.deleteFile(s.owner, s.name, item.path,
                        DeleteFileRequest(message, item.sha, branch))
                }
                // Dirs: enumerate every blob inside via git-tree, then delete each file.
                val dirItems = selectedItems.filter { it.type == "dir" }
                if (dirItems.isNotEmpty()) {
                    val branchInfo = repo.api.branch(s.owner, s.name, s.ref.ifBlank { "HEAD" })
                    val parent = repo.api.commitDetail(s.owner, s.name, branchInfo.commit.sha)
                    val ft = repo.api.gitTree(s.owner, s.name, parent.commit.tree.sha, recursive = 1)
                    val prefixes = dirItems.map { it.path.trim('/') + "/" }
                    val nested = ft.tree.filter { item ->
                        item.type == "blob" && prefixes.any { item.path.startsWith(it) }
                    }
                    for (item in nested) {
                        repo.api.deleteFile(s.owner, s.name, item.path,
                            DeleteFileRequest(message, item.sha, branch))
                    }
                }
                clearSelection()
                load(s.owner, s.name, s.path, s.ref)
                onDone()
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
     * Delete every file inside a folder using GitHub's Contents DELETE endpoint
     * (one call per file). Avoids the Git Trees API entirely so there are no
     * mode / sha-content 422 errors.
     */
    fun deleteFolder(folderPath: String, message: String, onDone: () -> Unit) {
        val s = _state.value
        val branch = s.ref.ifBlank { null }
        viewModelScope.launch {
            try {
                val branchName = s.ref.ifBlank { "HEAD" }
                val branchInfo = repo.api.branch(s.owner, s.name, branchName)
                val parent = repo.api.commitDetail(s.owner, s.name, branchInfo.commit.sha)
                val ft = repo.api.gitTree(s.owner, s.name, parent.commit.tree.sha, recursive = 1)
                val prefix = folderPath.trim('/') + "/"
                val blobs = ft.tree.filter { it.type == "blob" && it.path.startsWith(prefix) }
                android.util.Log.d("FilesVM", "deleteFolder $folderPath: ${blobs.size} blobs")
                if (blobs.isEmpty()) {
                    _state.value = _state.value.copy(error = "Folder is empty or already deleted")
                    onDone(); return@launch
                }
                for (blob in blobs) {
                    repo.api.deleteFile(s.owner, s.name, blob.path,
                        DeleteFileRequest(message, blob.sha, branch))
                }
                load(s.owner, s.name, s.path, s.ref)
                onDone()
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

}
