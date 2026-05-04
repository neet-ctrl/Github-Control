package com.githubcontrol.data.api

import retrofit2.Response
import retrofit2.http.*

interface GitHubApi {

    // ---------- User ----------
    @GET("user") suspend fun me(): GhUser
    @GET("user/emails") suspend fun myEmails(): List<GhEmail>
    @GET("users/{user}") suspend fun user(@Path("user") user: String): GhUser
    @GET("rate_limit") suspend fun rateLimit(): GhRateLimit

    // ---------- Repos ----------
    @GET("user/repos")
    suspend fun myRepos(
        @Query("visibility") visibility: String? = null,
        @Query("affiliation") affiliation: String = "owner,collaborator,organization_member",
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhRepo>

    @GET("users/{user}/starred")
    suspend fun myStarred(
        @Path("user") user: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhRepo>

    @GET("users/{user}/repos")
    suspend fun userRepos(
        @Path("user") user: String,
        @Query("type") type: String = "owner",
        @Query("sort") sort: String = "updated",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhRepo>

    @GET("repos/{owner}/{repo}")
    suspend fun repo(@Path("owner") owner: String, @Path("repo") repo: String): GhRepo

    @POST("user/repos")
    suspend fun createRepo(@Body body: CreateRepoRequest): GhRepo

    @PATCH("repos/{owner}/{repo}")
    suspend fun updateRepo(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: UpdateRepoRequest): GhRepo

    @DELETE("repos/{owner}/{repo}")
    suspend fun deleteRepo(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>

    @POST("repos/{owner}/{repo}/forks")
    suspend fun forkRepo(@Path("owner") owner: String, @Path("repo") repo: String): GhRepo

    @PUT("user/starred/{owner}/{repo}")
    suspend fun star(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>

    @DELETE("user/starred/{owner}/{repo}")
    suspend fun unstar(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>

    @GET("user/starred/{owner}/{repo}")
    suspend fun isStarred(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>

    @PUT("repos/{owner}/{repo}/subscription")
    suspend fun watch(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: Map<String, Boolean>): Response<Unit>

    @DELETE("repos/{owner}/{repo}/subscription")
    suspend fun unwatch(@Path("owner") owner: String, @Path("repo") repo: String): Response<Unit>

    @POST("repos/{owner}/{repo}/transfer")
    suspend fun transfer(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: TransferRepoRequest): Response<Unit>

    @GET("repos/{owner}/{repo}/contributors")
    suspend fun contributors(@Path("owner") owner: String, @Path("repo") repo: String, @Query("per_page") perPage: Int = 30): List<GhContributor>

    @GET("repos/{owner}/{repo}/languages")
    suspend fun languages(@Path("owner") owner: String, @Path("repo") repo: String): Map<String, Long>

    @GET("repos/{owner}/{repo}/stats/commit_activity")
    suspend fun commitActivity(@Path("owner") owner: String, @Path("repo") repo: String): Response<List<Map<String, kotlinx.serialization.json.JsonElement>>>

    // ---------- Contents ----------
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun contents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): Response<List<GhContent>>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun fileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): GhContent

    @GET("repos/{owner}/{repo}/contents")
    suspend fun rootContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null
    ): List<GhContent>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun putFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: PutFileRequest
    ): PutFileResponse

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
        @Body body: DeleteFileRequest
    ): PutFileResponse

    // ---------- Branches & Refs ----------
    @GET("repos/{owner}/{repo}/branches")
    suspend fun branches(@Path("owner") owner: String, @Path("repo") repo: String, @Query("per_page") perPage: Int = 100): List<GhBranch>

    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun branch(@Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String): GhBranch

    /** GitHub's dedicated branch-rename endpoint (single, atomic operation). */
    @POST("repos/{owner}/{repo}/branches/{branch}/rename")
    suspend fun renameBranch(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String,
        @Body body: RenameBranchRequest
    ): GhBranch

    @GET("repos/{owner}/{repo}/git/ref/{ref}")
    suspend fun ref(@Path("owner") owner: String, @Path("repo") repo: String, @Path("ref", encoded = true) ref: String): GhRef

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreateRefRequest): GhRef

    @PATCH("repos/{owner}/{repo}/git/refs/{ref}")
    suspend fun updateRef(@Path("owner") owner: String, @Path("repo") repo: String, @Path("ref", encoded = true) ref: String, @Body body: UpdateRefRequest): GhRef

    @DELETE("repos/{owner}/{repo}/git/refs/{ref}")
    suspend fun deleteRef(@Path("owner") owner: String, @Path("repo") repo: String, @Path("ref", encoded = true) ref: String): Response<Unit>

    @GET("repos/{owner}/{repo}/git/blobs/{sha}")
    suspend fun getBlob(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String): GhBlob

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreateBlobRequest): GhBlob

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreateTreeRequest): GhTreeRef

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreateCommitRequest): GhGitCommit

    @GET("repos/{owner}/{repo}/git/trees/{sha}")
    suspend fun gitTree(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String, @Query("recursive") recursive: Int = 0): GhFileTree

    // ---------- Commits ----------
    @GET("repos/{owner}/{repo}/commits")
    suspend fun commits(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("sha") sha: String? = null,
        @Query("path") path: String? = null,
        @Query("author") author: String? = null,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhCommit>

    @GET("repos/{owner}/{repo}/commits/{sha}")
    suspend fun commitDetail(@Path("owner") owner: String, @Path("repo") repo: String, @Path("sha") sha: String): GhCommit

    @GET("repos/{owner}/{repo}/compare/{base}...{head}")
    suspend fun compare(@Path("owner") owner: String, @Path("repo") repo: String, @Path("base") base: String, @Path("head") head: String): GhCommitCompare

    // ---------- Pull Requests ----------
    @GET("repos/{owner}/{repo}/pulls")
    suspend fun pullRequests(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhPullRequest>

    @GET("repos/{owner}/{repo}/pulls/{number}")
    suspend fun pullRequest(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): GhPullRequest

    @POST("repos/{owner}/{repo}/pulls")
    suspend fun createPullRequest(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreatePRRequest): GhPullRequest

    @PATCH("repos/{owner}/{repo}/pulls/{number}")
    suspend fun updatePullRequest(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Body body: UpdatePRRequest): GhPullRequest

    @PUT("repos/{owner}/{repo}/pulls/{number}/merge")
    suspend fun mergePullRequest(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Body body: MergePRRequest): Response<Unit>

    @GET("repos/{owner}/{repo}/pulls/{number}/files")
    suspend fun pullRequestFiles(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): List<GhCommitFile>

    @GET("repos/{owner}/{repo}/pulls/{number}/commits")
    suspend fun pullRequestCommits(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): List<GhCommit>

    // ---------- Issues ----------
    @GET("repos/{owner}/{repo}/issues")
    suspend fun issues(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GhIssue>

    @GET("repos/{owner}/{repo}/issues/{number}")
    suspend fun issue(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): GhIssue

    @POST("repos/{owner}/{repo}/issues")
    suspend fun createIssue(@Path("owner") owner: String, @Path("repo") repo: String, @Body body: CreateIssueRequest): GhIssue

    @PATCH("repos/{owner}/{repo}/issues/{number}")
    suspend fun updateIssue(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Body body: UpdateIssueRequest): GhIssue

    @GET("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun issueComments(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int): List<IssueComment>

    @POST("repos/{owner}/{repo}/issues/{number}/comments")
    suspend fun addIssueComment(@Path("owner") owner: String, @Path("repo") repo: String, @Path("number") number: Int, @Body body: CreateCommentRequest): IssueComment

    // ---------- Workflows / Actions ----------
    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun workflows(@Path("owner") owner: String, @Path("repo") repo: String): GhWorkflowsResponse

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun workflowRuns(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GhWorkflowRunsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{id}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("id") id: Long,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/logs")
    suspend fun workflowRunLogs(@Path("owner") owner: String, @Path("repo") repo: String, @Path("run_id") runId: Long): Response<okhttp3.ResponseBody>

    // ---------- Releases ----------
    @GET("repos/{owner}/{repo}/releases")
    suspend fun releases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<GhRelease>

    @DELETE("repos/{owner}/{repo}/releases/{release_id}")
    suspend fun deleteRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("release_id") releaseId: Long
    ): Response<Unit>

    // ---------- Notifications ----------
    @GET("notifications")
    suspend fun notifications(@Query("all") all: Boolean = false, @Query("per_page") perPage: Int = 50): List<GhNotification>

    @PUT("notifications/threads/{id}")
    suspend fun markNotificationRead(@Path("id") id: String): Response<Unit>

    // ---------- Search ----------
    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") q: String,
        @Query("sort") sort: String? = null,
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GhSearchReposResponse

    @GET("search/code")
    suspend fun searchCode(
        @Query("q") q: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GhSearchCodeResponse

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") q: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GhSearchUsersResponse

    // ---------- Branch protection ----------

    @GET("repos/{owner}/{repo}/branches/{branch}/protection")
    suspend fun branchProtection(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String
    ): Response<BranchProtection>

    /**
     * GitHub's PUT branch-protection endpoint requires `required_status_checks`,
     * `enforce_admins`, `required_pull_request_reviews`, and `restrictions` to
     * be present in the body — even when their value is `null` — otherwise it
     * responds with HTTP 422. Our global Json instance has `explicitNulls = false`
     * which strips nullable fields from serialized objects, so we send a raw
     * [JsonObject] here that the caller assembles with [buildJsonObject] to keep
     * those keys explicit.
     */
    @PUT("repos/{owner}/{repo}/branches/{branch}/protection")
    suspend fun setBranchProtection(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String,
        @Body body: kotlinx.serialization.json.JsonObject
    ): BranchProtection

    @DELETE("repos/{owner}/{repo}/branches/{branch}/protection")
    suspend fun removeBranchProtection(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("branch") branch: String
    ): Response<Unit>

    // ---------- SSH & GPG keys ----------

    @GET("user/keys") suspend fun sshKeys(): List<GhSshKey>
    @POST("user/keys") suspend fun addSshKey(@Body body: CreateSshKeyRequest): GhSshKey
    @DELETE("user/keys/{id}") suspend fun deleteSshKey(@Path("id") id: Long): Response<Unit>

    @GET("user/gpg_keys") suspend fun gpgKeys(): List<GhGpgKey>
    @DELETE("user/gpg_keys/{id}") suspend fun deleteGpgKey(@Path("id") id: Long): Response<Unit>

    // ---------- Profile editor ----------

    @PATCH("user")
    suspend fun updateMe(@Body body: UpdateUserRequest): GhUser

    // ---------- Collaborators ----------

    @GET("repos/{owner}/{repo}/collaborators")
    suspend fun collaborators(@Path("owner") owner: String, @Path("repo") repo: String): List<GhCollaborator>

    @PUT("repos/{owner}/{repo}/collaborators/{username}")
    suspend fun addCollaborator(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("username") username: String,
        @Body body: AddCollaboratorRequest
    ): Response<Unit>

    @DELETE("repos/{owner}/{repo}/collaborators/{username}")
    suspend fun removeCollaborator(
        @Path("owner") owner: String, @Path("repo") repo: String, @Path("username") username: String
    ): Response<Unit>

    // ---------- Archive download (zip / tarball) ----------

    @GET("repos/{owner}/{repo}/zipball/{ref}")
    @Streaming
    suspend fun zipball(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Path("ref", encoded = true) ref: String
    ): Response<okhttp3.ResponseBody>

    @GET("repos/{owner}/{repo}/tarball/{ref}")
    @Streaming
    suspend fun tarball(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Path("ref", encoded = true) ref: String
    ): Response<okhttp3.ResponseBody>

    // ---------- Codespaces ----------

    /** List codespaces the authenticated user has in [owner]/[repo]. */
    @GET("repos/{owner}/{repo}/codespaces")
    suspend fun repoCodespaces(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): GhCodespacesPage

    /** List the machine types available for a codespace in this repo (optionally pinned to a ref). */
    @GET("repos/{owner}/{repo}/codespaces/machines")
    suspend fun repoCodespaceMachines(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Query("location") location: String? = null,
        @Query("client_ip") clientIp: String? = null,
        @Query("ref") ref: String? = null
    ): GhCodespaceMachinesPage

    /** Create a codespace in the repository — accepts a branch name or commit SHA in `ref`. */
    @POST("repos/{owner}/{repo}/codespaces")
    suspend fun createCodespace(
        @Path("owner") owner: String, @Path("repo") repo: String,
        @Body body: CreateCodespaceRequest
    ): GhCodespace

    /** Single codespace by name (the `name` field on [GhCodespace], not the display name). */
    @GET("user/codespaces/{name}")
    suspend fun codespace(@Path("name") name: String): GhCodespace

    @POST("user/codespaces/{name}/start")
    suspend fun startCodespace(@Path("name") name: String): GhCodespace

    @POST("user/codespaces/{name}/stop")
    suspend fun stopCodespace(@Path("name") name: String): GhCodespace

    @DELETE("user/codespaces/{name}")
    suspend fun deleteCodespace(@Path("name") name: String): Response<Unit>

    // ---------- Raw download (for blob bytes) ----------

    @GET
    @Streaming
    suspend fun rawDownload(@Url url: String): Response<okhttp3.ResponseBody>
}
