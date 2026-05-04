package com.githubcontrol.data.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhUser(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    val name: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("type") val type: String = "User",
    @SerialName("twitter_username") val twitterUsername: String? = null,
    val hireable: Boolean? = null
)

@Serializable
data class GhRepo(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: GhUser,
    val description: String? = null,
    val private: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    val disabled: Boolean = false,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_wiki") val hasWiki: Boolean = true,
    @SerialName("has_projects") val hasProjects: Boolean = true,
    val language: String? = null,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("forks_count") val forks: Int = 0,
    @SerialName("watchers_count") val watchers: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    val size: Int = 0,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("ssh_url") val sshUrl: String? = null,
    val visibility: String? = null,
    val topics: List<String> = emptyList(),
    val license: GhLicense? = null
)

@Serializable
data class GhLicense(val key: String? = null, val name: String? = null, @SerialName("spdx_id") val spdxId: String? = null)

@Serializable
data class GhContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long = 0,
    val type: String, // "file" | "dir" | "symlink" | "submodule"
    val content: String? = null,
    val encoding: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("git_url") val gitUrl: String? = null,
    @SerialName("_links") val links: GhLinks? = null
)

@Serializable
data class GhLinks(val self: String? = null, val git: String? = null, val html: String? = null)

@Serializable
data class GhBranch(
    val name: String,
    val commit: GhBranchCommit,
    val protected: Boolean = false
)

@Serializable
data class GhBranchCommit(val sha: String, val url: String)

@Serializable
data class GhCommit(
    val sha: String,
    val commit: GhCommitDetail,
    val author: GhUser? = null,
    val committer: GhUser? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val parents: List<GhCommitParent> = emptyList(),
    val stats: GhCommitStats? = null,
    val files: List<GhCommitFile> = emptyList()
)

@Serializable
data class GhCommitParent(val sha: String, val url: String)

@Serializable
data class GhCommitDetail(
    val message: String,
    val author: GhCommitAuthor,
    val committer: GhCommitAuthor,
    val tree: GhTreeRef
)

@Serializable
data class GhCommitAuthor(val name: String, val email: String, val date: String)

@Serializable
data class GhTreeRef(val sha: String, val url: String)

@Serializable
data class GhCommitStats(val additions: Int = 0, val deletions: Int = 0, val total: Int = 0)

@Serializable
data class GhCommitFile(
    val sha: String? = null,
    val filename: String,
    val status: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: Int = 0,
    val patch: String? = null,
    @SerialName("blob_url") val blobUrl: String? = null,
    @SerialName("raw_url") val rawUrl: String? = null
)

@Serializable
data class GhPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    val draft: Boolean = false,
    @SerialName("user") val user: GhUser,
    val head: GhPRRef,
    val base: GhPRRef,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("merged_at") val mergedAt: String? = null,
    val merged: Boolean = false,
    val mergeable: Boolean? = null,
    @SerialName("html_url") val htmlUrl: String,
    val comments: Int = 0,
    val commits: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    @SerialName("changed_files") val changedFiles: Int = 0
)

@Serializable
data class GhPRRef(val ref: String, val sha: String, val label: String? = null)

@Serializable
data class GhIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    @SerialName("user") val user: GhUser,
    val labels: List<GhLabel> = emptyList(),
    val assignees: List<GhUser> = emptyList(),
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("pull_request") val pullRequest: GhPullLink? = null
)

@Serializable
data class GhPullLink(val url: String? = null)

@Serializable
data class GhLabel(val id: Long = 0, val name: String, val color: String = "888888", val description: String? = null)

@Serializable
data class GhWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GhWorkflowsResponse(@SerialName("total_count") val totalCount: Int = 0, val workflows: List<GhWorkflow> = emptyList())

@Serializable
data class GhWorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("head_branch") val headBranch: String? = null,
    @SerialName("head_sha") val headSha: String,
    val status: String,
    val conclusion: String? = null,
    @SerialName("workflow_id") val workflowId: Long,
    @SerialName("run_number") val runNumber: Int,
    val event: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String
)

@Serializable
data class GhWorkflowRunsResponse(@SerialName("total_count") val totalCount: Int = 0, @SerialName("workflow_runs") val runs: List<GhWorkflowRun> = emptyList())

@Serializable
data class GhSearchReposResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<GhRepo> = emptyList()
)

@Serializable
data class GhSearchCodeResponse(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<GhCodeItem> = emptyList()
)

@Serializable
data class GhCodeItem(
    val name: String,
    val path: String,
    val sha: String,
    @SerialName("html_url") val htmlUrl: String,
    val repository: GhRepo
)

@Serializable
data class GhSearchUsersResponse(
    @SerialName("total_count") val totalCount: Int,
    val items: List<GhUser> = emptyList()
)

@Serializable
data class GhContributor(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    val contributions: Int
)

@Serializable
data class GhFileTree(
    val sha: String,
    val url: String,
    val tree: List<GhFileTreeItem> = emptyList(),
    val truncated: Boolean = false
)

@Serializable
data class GhFileTreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String,
    val size: Long? = null,
    val url: String? = null
)

@Serializable
data class GhCommitCompare(
    val status: String,
    @SerialName("ahead_by") val aheadBy: Int,
    @SerialName("behind_by") val behindBy: Int,
    @SerialName("total_commits") val totalCommits: Int,
    val commits: List<GhCommit> = emptyList(),
    val files: List<GhCommitFile> = emptyList()
)

@Serializable
data class GhRelease(
    val id: Long,
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean,
    val prerelease: Boolean,
    @SerialName("created_at") val createdAt: String,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("target_commitish") val targetCommitish: String? = null,
    val author: GhUser? = null,
    val assets: List<GhReleaseAsset> = emptyList()
)

@Serializable
data class GhReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
    val state: String? = null,
    @SerialName("download_count") val downloadCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String? = null
)

@Serializable
data class GhNotification(
    val id: String,
    val unread: Boolean,
    val reason: String,
    @SerialName("updated_at") val updatedAt: String,
    val subject: GhNotificationSubject,
    val repository: GhRepo
)

@Serializable
data class GhNotificationSubject(val title: String, val type: String, val url: String? = null)

// ---------- Request bodies ----------

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    @SerialName("auto_init") val autoInit: Boolean = true,
    @SerialName("gitignore_template") val gitignoreTemplate: String? = null,
    @SerialName("license_template") val licenseTemplate: String? = null,
    @SerialName("has_issues") val hasIssues: Boolean = true,
    @SerialName("has_wiki") val hasWiki: Boolean = true,
    @SerialName("has_projects") val hasProjects: Boolean = true
)

@Serializable
data class UpdateRepoRequest(
    val name: String? = null,
    val description: String? = null,
    val private: Boolean? = null,
    val visibility: String? = null,
    val archived: Boolean? = null,
    @SerialName("has_issues") val hasIssues: Boolean? = null,
    @SerialName("has_wiki") val hasWiki: Boolean? = null,
    @SerialName("has_projects") val hasProjects: Boolean? = null,
    @SerialName("default_branch") val defaultBranch: String? = null
)

@Serializable
data class TransferRepoRequest(@SerialName("new_owner") val newOwner: String)

@Serializable
data class PutFileRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null,
    val author: GhCommitAuthor? = null,
    val committer: GhCommitAuthor? = null
)

@Serializable
data class DeleteFileRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

/**
 * The PUT /contents endpoint returns a *trimmed* commit object whose author/committer
 * use the {name, email, date} shape (GhCommitAuthor) — NOT the full GhUser shape.
 * Using GhCommit here causes MissingFieldException ("login", "id", "avatar_url").
 */
@Serializable
data class PutFileCommit(
    val sha: String,
    @SerialName("node_id") val nodeId: String? = null,
    val message: String? = null,
    val author: GhCommitAuthor? = null,
    val committer: GhCommitAuthor? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val tree: GhTreeRef? = null,
    val parents: List<GhCommitParent> = emptyList()
)

@Serializable
data class PutFileResponse(val content: GhContent? = null, val commit: PutFileCommit? = null)

@Serializable
data class RenameBranchRequest(@SerialName("new_name") val newName: String)

@Serializable
data class CreateBranchRequest(val ref: String, val sha: String)

@Serializable
data class UpdateRefRequest(val sha: String, val force: Boolean = false)

@Serializable
data class CreatePRRequest(
    val title: String,
    val head: String,
    val base: String,
    val body: String? = null,
    val draft: Boolean = false
)

@Serializable
data class UpdatePRRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val base: String? = null
)

@Serializable
data class MergePRRequest(
    @SerialName("commit_title") val commitTitle: String? = null,
    @SerialName("commit_message") val commitMessage: String? = null,
    @SerialName("merge_method") val mergeMethod: String = "merge"
)

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String? = null,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList()
)

@Serializable
data class UpdateIssueRequest(
    val title: String? = null,
    val body: String? = null,
    val state: String? = null,
    val labels: List<String>? = null,
    val assignees: List<String>? = null
)

@Serializable
data class IssueComment(
    val id: Long,
    val body: String,
    val user: GhUser,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class CreateCommentRequest(val body: String)

@Serializable
data class CreateRefRequest(val ref: String, val sha: String)

@Serializable
data class GhRef(val ref: String, @SerialName("node_id") val nodeId: String? = null, val url: String, val `object`: GhRefObject)

@Serializable
data class GhRefObject(val sha: String, val type: String, val url: String)

@Serializable
data class GhEmail(val email: String, val primary: Boolean = false, val verified: Boolean = false, val visibility: String? = null)

@Serializable
data class GhRateLimit(val resources: Map<String, GhRateLimitEntry> = emptyMap(), val rate: GhRateLimitEntry)

@Serializable
data class GhRateLimitEntry(val limit: Int, val remaining: Int, val reset: Long, val used: Int = 0)

@Serializable
data class GhBlob(
    val sha: String,
    val url: String = "",
    // POST /git/blobs returns only { sha, url }; GET /git/blobs/{sha} returns full body.
    // Make these optional so deserialization works for both shapes.
    val size: Long = 0,
    val content: String = "",
    val encoding: String = ""
)

@Serializable
data class CreateBlobRequest(val content: String, val encoding: String = "base64")

/**
 * Response from POST /repos/{owner}/{repo}/git/commits.
 *
 * This endpoint returns author/committer as {name, email, date} — NOT the full
 * GhUser shape ({login, id, avatar_url}).  Using GhCommit here causes a
 * MissingFieldException ("login", "id", "avatar_url") at path $.author.
 * This dedicated model uses GhCommitAuthor for those fields.
 */
@Serializable
data class GhGitCommit(
    val sha: String,
    val url: String = "",
    val message: String = "",
    val author: GhCommitAuthor? = null,
    val committer: GhCommitAuthor? = null,
    val tree: GhTreeRef? = null,
    val parents: List<GhCommitParent> = emptyList()
)

@Serializable
data class CreateTreeRequest(@SerialName("base_tree") val baseTree: String? = null, val tree: List<TreeNode>)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class TreeNode(
    val path: String,
    // @EncodeDefault(ALWAYS) is required on all fields that have default values because
    // the global Json instance has encodeDefaults=false (the kotlinx.serialization default).
    // Without this annotation, fields equal to their default are silently omitted from the
    // JSON body, causing GitHub's createTree API to return HTTP 422
    // "Must supply a valid tree.mode".
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val mode: String = "100644",
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val type: String = "blob",
    // sha=null signals deletion; always included so GitHub knows this is an explicit null.
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val sha: String? = null
)

@Serializable
data class CreateCommitRequest(
    val message: String,
    val tree: String,
    val parents: List<String>,
    val author: GhCommitAuthor? = null,
    val committer: GhCommitAuthor? = null
)

// ---------- Branch protection ----------

@Serializable
data class BranchProtection(
    val url: String? = null,
    @SerialName("required_status_checks") val requiredStatusChecks: RequiredStatusChecks? = null,
    @SerialName("enforce_admins") val enforceAdmins: BoolWrapper? = null,
    @SerialName("required_pull_request_reviews") val requiredReviews: RequiredReviews? = null,
    val restrictions: BranchRestrictions? = null,
    @SerialName("allow_force_pushes") val allowForcePushes: BoolWrapper? = null,
    @SerialName("allow_deletions") val allowDeletions: BoolWrapper? = null,
    @SerialName("required_linear_history") val requiredLinearHistory: BoolWrapper? = null,
    @SerialName("required_conversation_resolution") val requiredConversationResolution: BoolWrapper? = null,
    @SerialName("lock_branch") val lockBranch: BoolWrapper? = null
)

@Serializable
data class BoolWrapper(val enabled: Boolean = false)

@Serializable
data class RequiredStatusChecks(
    val strict: Boolean = false,
    val contexts: List<String> = emptyList()
)

@Serializable
data class RequiredReviews(
    @SerialName("required_approving_review_count") val requiredApprovingCount: Int = 1,
    @SerialName("dismiss_stale_reviews") val dismissStale: Boolean = false,
    @SerialName("require_code_owner_reviews") val requireCodeOwners: Boolean = false,
    @SerialName("require_last_push_approval") val requireLastPushApproval: Boolean = false
)

@Serializable
data class BranchRestrictions(
    val users: List<GhUser> = emptyList(),
    val teams: List<GhTeam> = emptyList()
)

@Serializable
data class GhTeam(
    val id: Long = 0,
    val name: String,
    val slug: String,
    val description: String? = null
)

@Serializable
data class UpdateBranchProtectionRequest(
    @SerialName("required_status_checks") val requiredStatusChecks: RequiredStatusChecks? = null,
    @SerialName("enforce_admins") val enforceAdmins: Boolean? = null,
    @SerialName("required_pull_request_reviews") val requiredReviews: UpdateRequiredReviews? = null,
    val restrictions: UpdateBranchRestrictions? = null,
    @SerialName("required_linear_history") val requiredLinearHistory: Boolean? = null,
    @SerialName("allow_force_pushes") val allowForcePushes: Boolean? = null,
    @SerialName("allow_deletions") val allowDeletions: Boolean? = null,
    @SerialName("required_conversation_resolution") val requiredConversationResolution: Boolean? = null,
    @SerialName("lock_branch") val lockBranch: Boolean? = null,
    @SerialName("block_creations") val blockCreations: Boolean? = null
)

@Serializable
data class UpdateRequiredReviews(
    @SerialName("required_approving_review_count") val requiredApprovingCount: Int = 1,
    @SerialName("dismiss_stale_reviews") val dismissStale: Boolean = false,
    @SerialName("require_code_owner_reviews") val requireCodeOwners: Boolean = false,
    @SerialName("require_last_push_approval") val requireLastPushApproval: Boolean = false
)

@Serializable
data class UpdateBranchRestrictions(
    val users: List<String> = emptyList(),
    val teams: List<String> = emptyList()
)

// ---------- SSH keys / GPG keys ----------

@Serializable
data class GhSshKey(
    val id: Long,
    val key: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    val verified: Boolean = false,
    @SerialName("read_only") val readOnly: Boolean = false
)

@Serializable
data class CreateSshKeyRequest(val title: String, val key: String)

@Serializable
data class GhGpgKey(
    val id: Long,
    @SerialName("key_id") val keyId: String,
    @SerialName("public_key") val publicKey: String,
    @SerialName("can_sign") val canSign: Boolean = false,
    @SerialName("can_certify") val canCertify: Boolean = false,
    @SerialName("can_encrypt_comms") val canEncryptComms: Boolean = false,
    @SerialName("created_at") val createdAt: String
)

// ---------- Profile updates ----------

@Serializable
data class UpdateUserRequest(
    val name: String? = null,
    val email: String? = null,
    val blog: String? = null,
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val hireable: Boolean? = null,
    @SerialName("twitter_username") val twitterUsername: String? = null
)

// ---------- Repo invitations / collaborators ----------

@Serializable
data class GhCollaborator(
    val login: String,
    val id: Long,
    @SerialName("avatar_url") val avatarUrl: String,
    val permissions: GhCollaboratorPermissions = GhCollaboratorPermissions(),
    @SerialName("role_name") val roleName: String? = null
)

@Serializable
data class GhCollaboratorPermissions(
    val pull: Boolean = false,
    val push: Boolean = false,
    val admin: Boolean = false,
    val triage: Boolean = false,
    val maintain: Boolean = false
)

@Serializable
data class AddCollaboratorRequest(val permission: String = "push")

// ---------- Codespaces ----------

@Serializable
data class GhCodespace(
    val id: Long,
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("environment_id") val environmentId: String? = null,
    val owner: GhUser? = null,
    val repository: GhRepo? = null,
    val machine: GhCodespaceMachine? = null,
    @SerialName("devcontainer_path") val devcontainerPath: String? = null,
    val prebuild: Boolean? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_used_at") val lastUsedAt: String? = null,
    val state: String = "Unknown",
    val url: String? = null,
    @SerialName("git_status") val gitStatus: GhCodespaceGitStatus? = null,
    val location: String? = null,
    @SerialName("idle_timeout_minutes") val idleTimeoutMinutes: Int? = null,
    @SerialName("web_url") val webUrl: String? = null,
    @SerialName("machines_url") val machinesUrl: String? = null,
    @SerialName("start_url") val startUrl: String? = null,
    @SerialName("stop_url") val stopUrl: String? = null,
    @SerialName("pulls_url") val pullsUrl: String? = null,
    @SerialName("recent_folders") val recentFolders: List<String> = emptyList()
)

@Serializable
data class GhCodespaceMachine(
    val name: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("operating_system") val operatingSystem: String? = null,
    @SerialName("storage_in_bytes") val storageInBytes: Long? = null,
    @SerialName("memory_in_bytes") val memoryInBytes: Long? = null,
    @SerialName("cpus") val cpus: Int? = null,
    @SerialName("prebuild_availability") val prebuildAvailability: String? = null
)

@Serializable
data class GhCodespaceGitStatus(
    val ahead: Int = 0,
    val behind: Int = 0,
    @SerialName("has_unpushed_changes") val hasUnpushedChanges: Boolean = false,
    @SerialName("has_uncommitted_changes") val hasUncommittedChanges: Boolean = false,
    val ref: String? = null
)

@Serializable
data class GhCodespacesPage(
    @SerialName("total_count") val totalCount: Int = 0,
    val codespaces: List<GhCodespace> = emptyList()
)

@Serializable
data class GhCodespaceMachinesPage(
    @SerialName("total_count") val totalCount: Int = 0,
    val machines: List<GhCodespaceMachine> = emptyList()
)

/**
 * Body for `POST /repos/{owner}/{repo}/codespaces`. The `ref` field is normally
 * a branch name but the API also accepts a commit SHA — we send the SHA so a
 * codespace can be opened pinned to one specific commit.
 */
@Serializable
data class CreateCodespaceRequest(
    val ref: String? = null,
    val location: String? = null,
    @SerialName("client_ip") val clientIp: String? = null,
    val machine: String? = null,
    @SerialName("devcontainer_path") val devcontainerPath: String? = null,
    @SerialName("multi_repo_permissions_opt_out") val multiRepoPermissionsOptOut: Boolean? = null,
    @SerialName("working_directory") val workingDirectory: String? = null,
    @SerialName("idle_timeout_minutes") val idleTimeoutMinutes: Int? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("retention_period_minutes") val retentionPeriodMinutes: Int? = null
)
