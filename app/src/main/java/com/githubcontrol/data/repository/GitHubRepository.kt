package com.githubcontrol.data.repository

import com.githubcontrol.data.api.*
import com.githubcontrol.data.auth.AccountManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val client: RetrofitClient,
    private val accountManager: AccountManager
) {
    val api: GitHubApi get() = client.api

    val cachedRepos = MutableStateFlow<List<GhRepo>>(emptyList())
    val cachedBranches = MutableStateFlow<Map<String, List<GhBranch>>>(emptyMap())

    suspend fun me(): GhUser = withContext(Dispatchers.IO) { api.me() }

    suspend fun listMyRepos(page: Int, perPage: Int = 30, sort: String, direction: String, visibility: String?): List<GhRepo> =
        withContext(Dispatchers.IO) {
            api.myRepos(visibility = visibility, sort = sort, direction = direction, perPage = perPage, page = page)
        }

    suspend fun listStarred(page: Int, perPage: Int = 30): List<GhRepo> = withContext(Dispatchers.IO) {
        val me = me()
        api.myStarred(me.login, perPage, page)
    }

    suspend fun user(login: String): GhUser = withContext(Dispatchers.IO) { api.user(login) }

    suspend fun userRepos(login: String, page: Int = 1, perPage: Int = 30): List<GhRepo> =
        withContext(Dispatchers.IO) { api.userRepos(login, perPage = perPage, page = page) }

    suspend fun repo(owner: String, name: String) = withContext(Dispatchers.IO) { api.repo(owner, name) }

    suspend fun createRepo(req: CreateRepoRequest) = withContext(Dispatchers.IO) { api.createRepo(req) }
    suspend fun updateRepo(owner: String, name: String, req: UpdateRepoRequest) = withContext(Dispatchers.IO) { api.updateRepo(owner, name, req) }
    suspend fun deleteRepo(owner: String, name: String) = withContext(Dispatchers.IO) { api.deleteRepo(owner, name) }
    suspend fun forkRepo(owner: String, name: String) = withContext(Dispatchers.IO) { api.forkRepo(owner, name) }
    suspend fun star(owner: String, name: String) = withContext(Dispatchers.IO) { api.star(owner, name) }
    suspend fun unstar(owner: String, name: String) = withContext(Dispatchers.IO) { api.unstar(owner, name) }
    suspend fun isStarred(owner: String, name: String) = withContext(Dispatchers.IO) {
        runCatching { api.isStarred(owner, name).code() == 204 }.getOrDefault(false)
    }
    suspend fun watch(owner: String, name: String, subscribed: Boolean) = withContext(Dispatchers.IO) {
        api.watch(owner, name, mapOf("subscribed" to subscribed, "ignored" to false))
    }
    suspend fun unwatch(owner: String, name: String) = withContext(Dispatchers.IO) { api.unwatch(owner, name) }
    suspend fun transfer(owner: String, name: String, newOwner: String) = withContext(Dispatchers.IO) {
        api.transfer(owner, name, TransferRepoRequest(newOwner))
    }

    suspend fun contents(owner: String, name: String, path: String, ref: String? = null): List<GhContent> = withContext(Dispatchers.IO) {
        if (path.isBlank()) {
            api.rootContents(owner, name, ref)
        } else {
            val resp = api.contents(owner, name, path, ref)
            resp.body() ?: emptyList()
        }
    }

    suspend fun fileContent(owner: String, name: String, path: String, ref: String? = null): GhContent =
        withContext(Dispatchers.IO) { api.fileContent(owner, name, path, ref) }

    suspend fun branches(owner: String, name: String): List<GhBranch> = withContext(Dispatchers.IO) {
        api.branches(owner, name).also { cachedBranches.value = cachedBranches.value + (("$owner/$name") to it) }
    }

    suspend fun commits(owner: String, name: String, branch: String?, page: Int, perPage: Int = 30) =
        withContext(Dispatchers.IO) { api.commits(owner, name, sha = branch, perPage = perPage, page = page) }

    suspend fun commitDetail(owner: String, name: String, sha: String) = withContext(Dispatchers.IO) { api.commitDetail(owner, name, sha) }
    suspend fun compare(owner: String, name: String, base: String, head: String) = withContext(Dispatchers.IO) { api.compare(owner, name, base, head) }

    suspend fun pulls(owner: String, name: String, state: String, page: Int) =
        withContext(Dispatchers.IO) { api.pullRequests(owner, name, state = state, page = page) }

    suspend fun pull(owner: String, name: String, number: Int) = withContext(Dispatchers.IO) { api.pullRequest(owner, name, number) }

    suspend fun createPull(owner: String, name: String, req: CreatePRRequest) = withContext(Dispatchers.IO) { api.createPullRequest(owner, name, req) }
    suspend fun updatePull(owner: String, name: String, number: Int, req: UpdatePRRequest) = withContext(Dispatchers.IO) { api.updatePullRequest(owner, name, number, req) }
    suspend fun mergePull(owner: String, name: String, number: Int, req: MergePRRequest) = withContext(Dispatchers.IO) { api.mergePullRequest(owner, name, number, req) }
    suspend fun pullFiles(owner: String, name: String, number: Int) = withContext(Dispatchers.IO) { api.pullRequestFiles(owner, name, number) }

    suspend fun issues(owner: String, name: String, state: String, page: Int) = withContext(Dispatchers.IO) { api.issues(owner, name, state = state, page = page) }
    suspend fun issue(owner: String, name: String, number: Int) = withContext(Dispatchers.IO) { api.issue(owner, name, number) }
    suspend fun createIssue(owner: String, name: String, req: CreateIssueRequest) = withContext(Dispatchers.IO) { api.createIssue(owner, name, req) }
    suspend fun updateIssue(owner: String, name: String, number: Int, req: UpdateIssueRequest) = withContext(Dispatchers.IO) { api.updateIssue(owner, name, number, req) }
    suspend fun issueComments(owner: String, name: String, number: Int) = withContext(Dispatchers.IO) { api.issueComments(owner, name, number) }
    suspend fun addIssueComment(owner: String, name: String, number: Int, body: String) = withContext(Dispatchers.IO) {
        api.addIssueComment(owner, name, number, CreateCommentRequest(body))
    }

    // ---------- Releases ----------
    suspend fun releases(owner: String, name: String): List<GhRelease> = withContext(Dispatchers.IO) {
        val all = mutableListOf<GhRelease>()
        var page = 1
        while (true) {
            val page_releases = api.releases(owner, name, perPage = 100, page = page)
            all.addAll(page_releases)
            if (page_releases.size < 100) break
            page++
        }
        all
    }

    suspend fun deleteRelease(owner: String, name: String, releaseId: Long) = withContext(Dispatchers.IO) {
        val r = api.deleteRelease(owner, name, releaseId)
        if (!r.isSuccessful) throw IllegalStateException("Delete release failed: HTTP ${r.code()}")
    }

    suspend fun workflows(owner: String, name: String) = withContext(Dispatchers.IO) { api.workflows(owner, name) }
    suspend fun workflowRuns(owner: String, name: String, page: Int = 1) = withContext(Dispatchers.IO) { api.workflowRuns(owner, name, page = page) }
    suspend fun dispatchWorkflow(owner: String, name: String, id: Long, ref: String, inputs: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        api.dispatchWorkflow(owner, name, id, mapOf("ref" to ref, "inputs" to inputs))
    }

    suspend fun searchRepos(q: String, page: Int = 1) = withContext(Dispatchers.IO) { api.searchRepos(q, page = page) }
    suspend fun searchCode(q: String, page: Int = 1) = withContext(Dispatchers.IO) { api.searchCode(q, page = page) }
    suspend fun searchUsers(q: String, page: Int = 1) = withContext(Dispatchers.IO) { api.searchUsers(q, page = page) }

    suspend fun rateLimit() = withContext(Dispatchers.IO) { api.rateLimit() }
    suspend fun notifications(all: Boolean = false) = withContext(Dispatchers.IO) { api.notifications(all) }
    suspend fun markNotification(id: String) = withContext(Dispatchers.IO) { api.markNotificationRead(id) }
    suspend fun contributors(owner: String, name: String) = withContext(Dispatchers.IO) { api.contributors(owner, name) }
    suspend fun languages(owner: String, name: String) = withContext(Dispatchers.IO) { api.languages(owner, name) }

    // ---------- High-level branch helpers ----------

    /**
     * Copies the latest snapshot of [sourceBranch] from [sourceOwner]/[sourceRepo] into a new
     * branch [newBranch] in [targetOwner]/[targetRepo] as a single commit.
     * Fast (seconds) and avoids 422 errors from complex tree/commit chains.
     */
    suspend fun importBranchFromRepo(
        sourceOwner: String,
        sourceRepo: String,
        sourceBranch: String,
        targetOwner: String,
        targetRepo: String,
        newBranch: String,
        onProgress: (String, Float?) -> Unit = { _, _ -> }
    ): GhRef = withContext(Dispatchers.IO) {

        val BLOB_CONCURRENCY = 8
        val semaphore        = Semaphore(BLOB_CONCURRENCY)

        // ── 1. Resolve source HEAD tree ───────────────────────────────────────
        onProgress("Fetching source branch…", null)
        val headCommit = api.commits(sourceOwner, sourceRepo, sha = sourceBranch, perPage = 1, page = 1)
            .firstOrNull() ?: error("No commits on $sourceOwner/$sourceRepo@$sourceBranch")
        val treeSha = headCommit.commit.tree.sha

        // ── 2. Fetch full recursive file tree ────────────────────────────────
        onProgress("Reading file tree…", 0.1f)
        val srcTree = api.gitTree(sourceOwner, sourceRepo, treeSha, recursive = 1)
        val blobs   = srcTree.tree.filter { it.type == "blob" }
        val total   = blobs.size.coerceAtLeast(1)

        // ── 3. Upload all blobs in parallel ──────────────────────────────────
        val uploaded = AtomicInteger(0)
        val treeNodes: List<TreeNode> = coroutineScope {
            blobs.map { item ->
                async {
                    semaphore.withPermit {
                        val blob = api.getBlob(sourceOwner, sourceRepo, item.sha)
                        val newSha = api.createBlob(
                            targetOwner, targetRepo,
                            CreateBlobRequest(blob.content.replace("\n", ""), "base64")
                        ).sha
                        val done = uploaded.incrementAndGet()
                        val pct  = done.toFloat() / total
                        onProgress("Uploading files… ($done/$total)", 0.1f + pct * 0.8f)
                        TreeNode(path = item.path, mode = item.mode, type = "blob", sha = newSha)
                    }
                }
            }.awaitAll()
        }

        // ── 4. Create tree + single commit ───────────────────────────────────
        onProgress("Creating commit…", 0.9f)
        val newTree   = api.createTree(targetOwner, targetRepo,
            CreateTreeRequest(baseTree = null, tree = treeNodes))
        val newCommit = api.createCommit(targetOwner, targetRepo,
            CreateCommitRequest(
                message   = "Import $sourceBranch from $sourceOwner/$sourceRepo",
                tree      = newTree.sha,
                parents   = emptyList(),
                author    = null,
                committer = null
            ))

        // ── 5. Create or force-update the branch ref ─────────────────────────
        onProgress("Finalising branch…", 1f)
        runCatching {
            api.createRef(targetOwner, targetRepo,
                CreateRefRequest("refs/heads/$newBranch", newCommit.sha))
        }.getOrElse { e ->
            if (e is HttpException && e.code() == 422)
                api.updateRef(targetOwner, targetRepo, "heads/$newBranch",
                    UpdateRefRequest(sha = newCommit.sha, force = true))
            else throw e
        }
    }

    suspend fun createBranch(owner: String, name: String, newRef: String, fromBranch: String): GhRef {
        val src = api.branch(owner, name, fromBranch)
        return api.createRef(owner, name, CreateRefRequest("refs/heads/$newRef", src.commit.sha))
    }

    suspend fun deleteBranch(owner: String, name: String, branch: String) {
        api.deleteRef(owner, name, "heads/$branch")
    }

    /** Force-update a branch ref to point at [sha] — equivalent to a hard reset. */
    suspend fun hardResetBranch(owner: String, name: String, branch: String, sha: String): GhRef =
        withContext(Dispatchers.IO) {
            api.updateRef(owner, name, "heads/$branch", UpdateRefRequest(sha = sha, force = true))
        }

    /** Create a new branch [newBranch] pointing at the existing [sha]. */
    suspend fun createBranchAtSha(owner: String, name: String, newBranch: String, sha: String): GhRef =
        withContext(Dispatchers.IO) {
            api.createRef(owner, name, CreateRefRequest("refs/heads/$newBranch", sha))
        }

    /** Update the repository's default branch via the repo PATCH endpoint. */
    suspend fun setDefaultBranch(owner: String, name: String, branch: String): GhRepo =
        withContext(Dispatchers.IO) {
            api.updateRepo(owner, name, UpdateRepoRequest(defaultBranch = branch))
        }

    /**
     * Use GitHub's dedicated branch-rename endpoint when possible (single, atomic call,
     * also moves protections, PR head refs, etc.). Falls back to the legacy create+delete
     * dance only if the dedicated endpoint refuses (e.g. older GHES instance).
     */
    suspend fun renameBranch(owner: String, name: String, oldName: String, newName: String) =
        withContext(Dispatchers.IO) {
            runCatching { api.renameBranch(owner, name, oldName, RenameBranchRequest(newName)) }
                .getOrElse {
                    val src = api.branch(owner, name, oldName)
                    api.createRef(owner, name, CreateRefRequest("refs/heads/$newName", src.commit.sha))
                    api.deleteRef(owner, name, "heads/$oldName")
                    api.branch(owner, name, newName)
                }
        }

    // ---------- Branch protection ----------
    suspend fun branchProtection(owner: String, name: String, branch: String): BranchProtection? = withContext(Dispatchers.IO) {
        val r = api.branchProtection(owner, name, branch)
        if (r.isSuccessful) r.body() else null
    }

    suspend fun setBranchProtection(owner: String, name: String, branch: String, body: kotlinx.serialization.json.JsonObject) =
        withContext(Dispatchers.IO) { api.setBranchProtection(owner, name, branch, body) }

    suspend fun removeBranchProtection(owner: String, name: String, branch: String) =
        withContext(Dispatchers.IO) { api.removeBranchProtection(owner, name, branch) }

    // ---------- SSH / GPG keys ----------
    suspend fun sshKeys() = withContext(Dispatchers.IO) { api.sshKeys() }
    suspend fun addSshKey(title: String, key: String) = withContext(Dispatchers.IO) { api.addSshKey(CreateSshKeyRequest(title, key)) }
    suspend fun deleteSshKey(id: Long) = withContext(Dispatchers.IO) { api.deleteSshKey(id) }
    suspend fun gpgKeys() = withContext(Dispatchers.IO) { api.gpgKeys() }
    suspend fun deleteGpgKey(id: Long) = withContext(Dispatchers.IO) { api.deleteGpgKey(id) }

    // ---------- Profile ----------
    suspend fun updateMe(body: UpdateUserRequest) = withContext(Dispatchers.IO) { api.updateMe(body) }

    // ---------- Collaborators ----------
    suspend fun collaborators(owner: String, name: String) = withContext(Dispatchers.IO) { api.collaborators(owner, name) }
    suspend fun addCollaborator(owner: String, name: String, login: String, permission: String) =
        withContext(Dispatchers.IO) { api.addCollaborator(owner, name, login, AddCollaboratorRequest(permission)) }
    suspend fun removeCollaborator(owner: String, name: String, login: String) =
        withContext(Dispatchers.IO) { api.removeCollaborator(owner, name, login) }

    // ---------- Archive download ----------
    suspend fun zipball(owner: String, name: String, ref: String) = withContext(Dispatchers.IO) { api.zipball(owner, name, ref) }
    suspend fun tarball(owner: String, name: String, ref: String) = withContext(Dispatchers.IO) { api.tarball(owner, name, ref) }
    suspend fun rawDownload(url: String) = withContext(Dispatchers.IO) { api.rawDownload(url) }

    // ---------- Tree-based multi-file commit (true Git engine via REST) ----------
    /**
     * Commit multiple file changes atomically using the Git Data API.
     * Each entry: path -> bytes (null deletes).
     */
    suspend fun commitFiles(
        owner: String, name: String, branch: String,
        files: List<Pair<String, ByteArray?>>,
        message: String,
        authorName: String? = null, authorEmail: String? = null
    ): GhCommit = withContext(Dispatchers.IO) {
        val branchInfo = api.branch(owner, name, branch)
        val parentSha = branchInfo.commit.sha
        val parentCommit = api.commitDetail(owner, name, parentSha)
        val baseTreeSha = parentCommit.commit.tree.sha

        val nodes = mutableListOf<TreeNode>()
        for ((path, bytes) in files) {
            if (bytes == null) {
                nodes += TreeNode(path = path, mode = "100644", type = "blob", sha = null)
            } else {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val blob = api.createBlob(owner, name, CreateBlobRequest(b64, "base64"))
                nodes += TreeNode(path = path, mode = "100644", type = "blob", sha = blob.sha)
            }
        }
        val tree = api.createTree(owner, name, CreateTreeRequest(baseTree = baseTreeSha, tree = nodes))

        val author = if (authorName != null && authorEmail != null)
            GhCommitAuthor(authorName, authorEmail, java.time.Instant.now().toString())
        else null

        val commit = api.createCommit(owner, name, CreateCommitRequest(
            message = message, tree = tree.sha, parents = listOf(parentSha),
            author = author, committer = author
        ))
        api.updateRef(owner, name, "heads/$branch", UpdateRefRequest(commit.sha, false))
        commit
    }

    // ---------- Codespaces ----------

    suspend fun listRepoCodespaces(owner: String, name: String, page: Int = 1, perPage: Int = 30): GhCodespacesPage =
        withContext(Dispatchers.IO) { api.repoCodespaces(owner, name, perPage, page) }

    suspend fun listRepoCodespaceMachines(owner: String, name: String, ref: String? = null): List<GhCodespaceMachine> =
        withContext(Dispatchers.IO) {
            runCatching { api.repoCodespaceMachines(owner, name, ref = ref).machines }.getOrDefault(emptyList())
        }

    suspend fun createCodespace(owner: String, name: String, body: CreateCodespaceRequest): GhCodespace =
        withContext(Dispatchers.IO) { api.createCodespace(owner, name, body) }

    suspend fun codespace(name: String): GhCodespace = withContext(Dispatchers.IO) { api.codespace(name) }
    suspend fun startCodespace(name: String): GhCodespace = withContext(Dispatchers.IO) { api.startCodespace(name) }
    suspend fun stopCodespace(name: String): GhCodespace = withContext(Dispatchers.IO) { api.stopCodespace(name) }
    suspend fun deleteCodespace(name: String) = withContext(Dispatchers.IO) {
        val r = api.deleteCodespace(name)
        if (!r.isSuccessful) throw IllegalStateException("Delete codespace failed: HTTP ${r.code()}")
    }
}
