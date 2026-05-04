package com.githubcontrol.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhFileTreeItem
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TreeNodeUi(
    val path: String,
    val name: String,
    val depth: Int,
    val type: String, // tree/blob
    val sha: String,
    val expanded: Boolean = false,
    val visible: Boolean = true,
    val size: Long = 0,
    val selected: Boolean = false
)

data class TreeState(
    val loading: Boolean = false,
    val truncated: Boolean = false,
    val items: List<TreeNodeUi> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class TreeViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(TreeState())
    val state: StateFlow<TreeState> = _state

    fun load(owner: String, name: String, ref: String?) {
        viewModelScope.launch {
            _state.value = TreeState(loading = true)
            try {
                val branchRef = ref?.takeIf { it.isNotBlank() } ?: "HEAD"
                val branch = runCatching { repo.api.branch(owner, name, branchRef) }.getOrNull()
                val sha = branch?.commit?.sha ?: repo.api.repo(owner, name).defaultBranch.let { db ->
                    repo.api.branch(owner, name, db).commit.sha
                }
                val tree = repo.api.gitTree(owner, name, sha, recursive = 1)
                val ui = tree.tree.map { it.toUi() }.sortedBy { it.path.lowercase() }
                val depthZero = ui.map { it.copy(depth = it.path.count { c -> c == '/' }) }
                _state.value = TreeState(loading = false, truncated = tree.truncated, items = computeVisibility(depthZero, mutableSetOf()))
            } catch (t: Throwable) { _state.value = TreeState(loading = false, error = t.message) }
        }
    }

    fun toggle(path: String) {
        val items = _state.value.items.toMutableList()
        val idx = items.indexOfFirst { it.path == path }
        if (idx < 0 || items[idx].type != "tree") return
        items[idx] = items[idx].copy(expanded = !items[idx].expanded)
        val expanded = items.filter { it.expanded && it.type == "tree" }.map { it.path }.toMutableSet()
        _state.value = _state.value.copy(items = computeVisibility(items, expanded))
    }

    fun toggleSelect(path: String) {
        _state.value = _state.value.copy(items = _state.value.items.map {
            if (it.path == path) it.copy(selected = !it.selected) else it
        })
    }

    fun selectAllBlobs() {
        _state.value = _state.value.copy(items = _state.value.items.map {
            if (it.type == "blob") it.copy(selected = true) else it
        })
    }

    fun clearSelection() {
        _state.value = _state.value.copy(items = _state.value.items.map { it.copy(selected = false) })
    }

    fun selectedPaths(): List<String> = _state.value.items.filter { it.selected && it.type == "blob" }.map { it.path }

    /** Stream the repo zipball from GitHub directly into a SAF Uri. */
    fun downloadRepoZip(owner: String, name: String, ref: String, ctx: Context, output: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Logger.i("Tree", "downloading zipball $owner/$name@$ref")
                val resp = repo.zipball(owner, name, ref)
                if (!resp.isSuccessful) {
                    Logger.e("Tree", "zipball HTTP ${resp.code()}")
                    return@launch
                }
                val body = resp.body() ?: return@launch
                ctx.contentResolver.openOutputStream(output)?.use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
                Logger.i("Tree", "zipball saved to $output")
            } catch (t: Throwable) {
                Logger.e("Tree", "zipball failed", t)
            }
        }
    }

    /** Bulk delete selected blob paths using the Contents DELETE endpoint (one call per file). */
    suspend fun deleteSelected(owner: String, name: String, branch: String) {
        val selected = _state.value.items.filter { it.selected && it.type == "blob" }
        if (selected.isEmpty()) return
        Logger.w("Tree", "deleting ${selected.size} file(s) from $owner/$name@$branch")
        try {
            withContext(Dispatchers.IO) {
                for (item in selected) {
                    repo.api.deleteFile(
                        owner, name, item.path,
                        com.githubcontrol.data.api.DeleteFileRequest(
                            message = "Delete ${selected.size} file(s) from device",
                            sha = item.sha,
                            branch = branch.takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            Logger.i("Tree", "delete ok (${selected.size} files)")
            clearSelection()
            load(owner, name, branch)
        } catch (t: Throwable) {
            Logger.e("Tree", "delete failed", t)
            _state.value = _state.value.copy(error = t.message)
        }
    }

    private fun computeVisibility(items: List<TreeNodeUi>, expanded: MutableSet<String>): List<TreeNodeUi> {
        return items.map { node ->
            val parentPath = node.path.substringBeforeLast('/', "")
            val visible = if (parentPath.isEmpty()) true else {
                var p = parentPath
                var ok = true
                while (p.isNotEmpty()) {
                    if (!expanded.contains(p)) { ok = false; break }
                    p = p.substringBeforeLast('/', "")
                }
                ok
            }
            node.copy(visible = visible)
        }
    }

    private fun GhFileTreeItem.toUi() = TreeNodeUi(
        path = path, name = path.substringAfterLast('/'), depth = path.count { it == '/' },
        type = type, sha = sha, size = size ?: 0
    )
}
