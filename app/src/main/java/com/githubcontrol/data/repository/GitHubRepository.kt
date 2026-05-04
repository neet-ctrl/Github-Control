package com.githubcontrol.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.githubcontrol.data.api.*
import com.githubcontrol.data.auth.AccountManager
import com.githubcontrol.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubRepository @Inject constructor(
    private val client: RetrofitClient,
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context
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
                        val done = uploaded.incrementAndGet()
                        val pct  = done.toFloat() / total
                        onProgress("Uploading files… ($done/$total)", 0.1f + pct * 0.8f)
                        runCatching {
                            val blob = api.getBlob(sourceOwner, sourceRepo, item.sha)
                            if (blob.encoding == "none") {
                                // GitHub won't serve the content (file > 100 MB) — skip it
                                Logger.w("Import", "Skipping ${item.path}: blob too large for API (encoding=none)")
                                return@runCatching null
                            }
                            val rawContent = blob.content.replace("\n", "")
                            val newSha = api.createBlob(
                                targetOwner, targetRepo,
                                CreateBlobRequest(rawContent, "base64")
                            ).sha
                            // Always use "100644" — GitHub's createTree is strict about
                            // valid modes and some source repos have unusual values
                            // (100664, 100775, 120000, etc.) that cause HTTP 422.
                            TreeNode(path = item.path, mode = "100644", type = "blob", sha = newSha)
                        }.getOrElse { err ->
                            val detail = if (err is HttpException)
                                err.response()?.errorBody()?.string() ?: err.message()
                            else err.message
                            Logger.e("Import", "Skipping ${item.path}: $detail", err)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
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
     * Each entry: path -> bytes (null = delete that path).
     *
     * Deletion strategy: fetch the full current tree, drop deleted paths, re-reference
     * the remaining blobs with their real SHAs.  This avoids sha=null tree nodes
     * entirely — GitHub's createTree rejects requests where both sha AND content are
     * present (even as null).
     */
    suspend fun commitFiles(
        owner: String, name: String, branch: String,
        files: List<Pair<String, ByteArray?>>,
        message: String,
        authorName: String? = null, authorEmail: String? = null
    ): GhGitCommit = withContext(Dispatchers.IO) {
        val branchInfo   = api.branch(owner, name, branch)
        val parentSha    = branchInfo.commit.sha
        val parentCommit = api.commitDetail(owner, name, parentSha)
        val baseTreeSha  = parentCommit.commit.tree.sha

        val toDelete = files.filter { it.second == null }.map { it.first }.toSet()
        val toUpsert = files.filter { it.second != null }

        val treeNodes = mutableListOf<TreeNode>()

        if (toDelete.isNotEmpty()) {
            // Fetch the complete current tree so we can re-reference every surviving
            // blob with its real SHA — no sha=null nodes are ever sent.
            Logger.i("commitFiles", "Deletion mode: fetching full tree for $owner/$name@$branch")
            val currentTree = api.gitTree(owner, name, baseTreeSha, recursive = 1)
            currentTree.tree
                .filter { it.type == "blob" && it.path !in toDelete }
                .forEach { item ->
                    treeNodes += TreeNode(path = item.path, mode = "100644", type = "blob", sha = item.sha)
                }
            Logger.i("commitFiles", "Keeping ${treeNodes.size} blobs, deleting ${toDelete.size}")
        }

        // Upload new / modified blobs
        for ((path, bytes) in toUpsert) {
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val blob = api.createBlob(owner, name, CreateBlobRequest(b64, "base64"))
            // Remove any stale entry for this path then add the new one
            treeNodes.removeAll { it.path == path }
            treeNodes += TreeNode(path = path, mode = "100644", type = "blob", sha = blob.sha)
        }

        // When there are deletions we send a COMPLETE tree (baseTree=null).
        // When it is uploads-only we use baseTree to inherit unchanged files efficiently.
        val treeReq = if (toDelete.isNotEmpty())
            CreateTreeRequest(baseTree = null, tree = treeNodes)
        else
            CreateTreeRequest(baseTree = baseTreeSha, tree = treeNodes)

        val tree = api.createTree(owner, name, treeReq)

        val author = if (authorName != null && authorEmail != null)
            GhCommitAuthor(authorName, authorEmail, java.time.Instant.now().toString())
        else null

        val commit = api.createCommit(owner, name, CreateCommitRequest(
            message = message, tree = tree.sha, parents = listOf(parentSha),
            author = author, committer = author
        ))
        Logger.i("commitFiles", "Updating ref heads/$branch → ${commit.sha}")
        api.updateRef(owner, name, "heads/$branch", UpdateRefRequest(commit.sha, force = true))
        commit
    }

    // ---------- Large-file streaming upload ----------

    /**
     * Creates a Git blob for [uri] using a **streaming** OkHttp request body.
     *
     * Unlike [commitFiles] (which base64-encodes the full byte array into a String),
     * this function pipes the raw bytes through a Base64 encoder directly into the
     * OkHttp I/O buffer — so the peak heap usage is just one 48 KB chunk at a time
     * regardless of file size.  This allows uploading files up to GitHub's hard limit
     * (~100 MB for the Git Blobs API) without triggering OutOfMemoryError on Android.
     *
     * @return The SHA-1 of the newly created blob.
     */
    suspend fun createBlobStreaming(
        owner: String,
        repoName: String,
        uri: Uri,
        fileSizeBytes: Long = -1
    ): String = withContext(Dispatchers.IO) {
        // GitHub's API hard limit is 100 MB. Fail immediately with a clear message
        // instead of uploading for 2+ minutes and receiving a cryptic 401/422.
        val GITHUB_BLOB_MAX = 100L * 1024 * 1024
        if (fileSizeBytes > 0 && fileSizeBytes > GITHUB_BLOB_MAX) {
            throw IOException(
                "File is ${fileSizeBytes / 1_048_576} MB — GitHub's API maximum is 100 MB. " +
                "GitHub cannot accept files larger than 100 MB via any upload method."
            )
        }

        // NOTE: Do NOT add Authorization/Accept headers here manually.
        // rawClient already has authInterceptor which adds all required headers
        // (Authorization, Accept, X-GitHub-Api-Version, User-Agent).
        // Adding them again causes duplicate headers that result in HTTP 401.

        val streamBody = object : RequestBody() {
            override fun contentType() = "application/json; charset=utf-8".toMediaType()

            // Provide a known Content-Length so OkHttp sends a fixed-length request
            // instead of chunked transfer encoding.  GitHub's blob API works reliably
            // with Content-Length but may reject chunked uploads of large files.
            // prefix = {"encoding":"base64","content":"  (28 chars)
            // suffix = "}                                  ( 2 chars)
            override fun contentLength(): Long {
                if (fileSizeBytes <= 0) return -1
                val base64Size = ((fileSizeBytes + 2) / 3) * 4
                return 28L + base64Size + 2L
            }

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8("{\"encoding\":\"base64\",\"content\":\"")
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open URI for streaming: $uri")
                // Read in 48 KB chunks (exactly divisible by 3) so each intermediate
                // flush produces clean base64 output with no intra-stream padding.
                val accumulator = ByteArray(3 * 16384) // 48 KB
                input.use { ins ->
                    var pending = 0
                    val readBuf = ByteArray(8 * 1024)
                    while (true) {
                        val n = ins.read(readBuf)
                        if (n < 0) break
                        var src = 0
                        while (src < n) {
                            val copy = minOf(n - src, accumulator.size - pending)
                            System.arraycopy(readBuf, src, accumulator, pending, copy)
                            pending += copy
                            src += copy
                            if (pending == accumulator.size) {
                                sink.write(Base64.encode(accumulator, Base64.NO_WRAP))
                                pending = 0
                            }
                        }
                    }
                    if (pending > 0) {
                        sink.write(Base64.encode(accumulator, 0, pending, Base64.NO_WRAP))
                    }
                }
                sink.writeUtf8("\"}")
            }
        }

        // rawClient interceptors add Accept, X-GitHub-Api-Version, User-Agent, Authorization —
        // do NOT add them again here or they will be duplicated and GitHub returns HTTP 401.
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repoName/git/blobs")
            .post(streamBody)
            .build()

        // Use a 10-minute write timeout: 120 s (the base client default) is too short
        // for 50–100 MB files over a mobile connection.
        val blobClient = client.rawClient.newBuilder()
            .writeTimeout(600, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val response = blobClient.newCall(request).execute()
        response.use { resp ->
            val bodyStr = resp.body?.string()
                ?: throw IOException("Empty response from blob creation endpoint")
            if (!resp.isSuccessful)
                throw IOException("Create blob failed (HTTP ${resp.code}): $bodyStr")
            client.json.decodeFromString<GhBlob>(bodyStr).sha
        }
    }

    /**
     * Uploads a single file to [branch] using the Git Data API with a streaming blob.
     *
     * This is the fallback path for files that are too large for the Contents API
     * (`PUT /repos/{owner}/{repo}/contents/{path}` is limited to ~100 MB and requires
     * the full base64 payload in memory).  This method:
     *   1. Creates a blob via [createBlobStreaming] — no OOM even for 200 MB files.
     *   2. Creates a new tree that inherits the branch's existing files via `base_tree`.
     *   3. Creates a commit and updates the branch ref atomically.
     *
     * Because [runJob] in [UploadManager] processes files sequentially, each call here
     * parents off the live HEAD so commits chain correctly.
     */
    suspend fun commitSingleLargeFile(
        owner: String,
        repoName: String,
        branch: String,
        targetPath: String,
        uri: Uri,
        message: String,
        fileSizeBytes: Long = -1,
        authorName: String? = null,
        authorEmail: String? = null
    ): GhGitCommit = withContext(Dispatchers.IO) {
        val blobSha      = createBlobStreaming(owner, repoName, uri, fileSizeBytes)
        val branchInfo   = api.branch(owner, repoName, branch)
        val parentSha    = branchInfo.commit.sha
        val parentCommit = api.commitDetail(owner, repoName, parentSha)
        val baseTreeSha  = parentCommit.commit.tree.sha

        val treeNode = TreeNode(path = targetPath, mode = "100644", type = "blob", sha = blobSha)
        val tree     = api.createTree(owner, repoName,
            CreateTreeRequest(baseTree = baseTreeSha, tree = listOf(treeNode)))

        val author = if (authorName != null && authorEmail != null)
            GhCommitAuthor(authorName, authorEmail, java.time.Instant.now().toString()) else null
        val commit = api.createCommit(owner, repoName, CreateCommitRequest(
            message   = message,
            tree      = tree.sha,
            parents   = listOf(parentSha),
            author    = author,
            committer = author
        ))
        Logger.i("LargeUpload", "Committed $targetPath via streaming blob → ${commit.sha}")
        api.updateRef(owner, repoName, "heads/$branch", UpdateRefRequest(commit.sha, force = true))
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
