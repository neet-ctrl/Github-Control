package com.githubcontrol.ui.screens.branches

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.InfoIcon
import com.githubcontrol.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

data class BPState(
    val loading: Boolean = true,
    val protected: Boolean = false,
    val requireReviews: Boolean = false,
    val reviewerCount: Int = 1,
    val dismissStale: Boolean = false,
    val requireCodeOwners: Boolean = false,
    val requireStatusChecks: Boolean = false,
    val strictStatusChecks: Boolean = false,
    val statusContexts: String = "",
    val enforceAdmins: Boolean = false,
    val linearHistory: Boolean = false,
    val allowForcePushes: Boolean = false,
    val allowDeletions: Boolean = false,
    val requireConvResolution: Boolean = false,
    val lockBranch: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class BranchProtectionViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    val state = MutableStateFlow(BPState())
    private var owner = ""; private var name = ""; private var branch = ""
    fun load(o: String, n: String, b: String) {
        owner = o; name = n; branch = b
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, error = null)
            try {
                val p = repo.branchProtection(o, n, b)
                if (p == null) {
                    state.value = BPState(loading = false, protected = false)
                    return@launch
                }
                val rev = p.requiredReviews
                val sc = p.requiredStatusChecks
                state.value = BPState(
                    loading = false, protected = true,
                    requireReviews = rev != null,
                    reviewerCount = rev?.requiredApprovingCount ?: 1,
                    dismissStale = rev?.dismissStale ?: false,
                    requireCodeOwners = rev?.requireCodeOwners ?: false,
                    requireStatusChecks = sc != null,
                    strictStatusChecks = sc?.strict ?: false,
                    statusContexts = sc?.contexts.orEmpty().joinToString(", "),
                    enforceAdmins = p.enforceAdmins?.enabled ?: false,
                    linearHistory = p.requiredLinearHistory?.enabled ?: false,
                    allowForcePushes = p.allowForcePushes?.enabled ?: false,
                    allowDeletions = p.allowDeletions?.enabled ?: false,
                    requireConvResolution = p.requiredConversationResolution?.enabled ?: false,
                    lockBranch = p.lockBranch?.enabled ?: false
                )
            } catch (t: Throwable) {
                state.value = state.value.copy(loading = false, error = t.message)
            }
        }
    }
    fun update(transform: (BPState) -> BPState) { state.value = transform(state.value) }

    /**
     * GitHub's PUT branch-protection endpoint requires `required_status_checks`,
     * `enforce_admins`, `required_pull_request_reviews`, and `restrictions` to
     * appear in the JSON body even when their value is null. We build the body
     * by hand here so those keys are always present (the global Json instance
     * uses explicitNulls=false and would otherwise drop them, causing HTTP 422).
     */
    private fun buildBody(s: BPState): JsonObject = buildJsonObject {
        if (s.requireStatusChecks) {
            put("required_status_checks", buildJsonObject {
                put("strict", s.strictStatusChecks)
                put("contexts", buildJsonArray {
                    s.statusContexts.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { add(it) }
                })
            })
        } else {
            put("required_status_checks", JsonNull)
        }

        put("enforce_admins", s.enforceAdmins)

        if (s.requireReviews) {
            put("required_pull_request_reviews", buildJsonObject {
                put("required_approving_review_count", s.reviewerCount.coerceIn(0, 6))
                put("dismiss_stale_reviews", s.dismissStale)
                put("require_code_owner_reviews", s.requireCodeOwners)
            })
        } else {
            put("required_pull_request_reviews", JsonNull)
        }

        // Restrictions are only valid for organisation-owned repos. Sending null
        // disables them, which is the correct default for personal repos too.
        put("restrictions", JsonNull)

        put("required_linear_history", s.linearHistory)
        put("allow_force_pushes", s.allowForcePushes)
        put("allow_deletions", s.allowDeletions)
        put("required_conversation_resolution", s.requireConvResolution)
        put("lock_branch", s.lockBranch)
        put("block_creations", false)
    }

    fun save() {
        val s = state.value
        viewModelScope.launch {
            state.value = s.copy(saving = true, error = null, message = null)
            try {
                repo.setBranchProtection(owner, name, branch, buildBody(s))
                Logger.i("BranchProtection", "saved $owner/$name@$branch")
                state.value = s.copy(saving = false, protected = true, message = "Saved.")
            } catch (t: Throwable) {
                Logger.e("BranchProtection", "save failed", t)
                state.value = s.copy(saving = false, error = friendlyError(t))
            }
        }
    }

    private fun friendlyError(t: Throwable): String {
        val raw = t.message.orEmpty()
        return when {
            raw.contains("422") -> "GitHub rejected the rules (HTTP 422). Some options aren't valid for this branch — for example, status-check contexts must already exist on the repo, and reviewer rules require the repo to support pull requests."
            raw.contains("403") -> "Permission denied (HTTP 403). You need admin rights on this repository to change protection rules."
            raw.contains("404") -> "Branch not found (HTTP 404)."
            else -> raw.ifEmpty { "Unknown error while saving protection." }
        }
    }

    fun remove() {
        viewModelScope.launch {
            try {
                repo.removeBranchProtection(owner, name, branch)
                Logger.w("BranchProtection", "removed $owner/$name@$branch")
                state.value = BPState(loading = false, protected = false, message = "Protection removed.")
            } catch (t: Throwable) {
                state.value = state.value.copy(error = friendlyError(t))
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/*  UI                                                                       */
/* ------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchProtectionScreen(
    owner: String, name: String, branch: String,
    onBack: () -> Unit, vm: BranchProtectionViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name, branch) { vm.load(owner, name, branch) }
    val s by vm.state.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Protection · $branch") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { pad ->
        // We deliberately render the loading and ready states as separate
        // sibling branches of an if/else (rather than using `return@Column`
        // after the spinner). Early-returning inside a Compose lambda after
        // emitting a child has been observed to cause a "Start/end imbalance"
        // crash in Compose 1.7 on Android 15 once `loading` flips back.
        if (s.loading) {
            Column(
                Modifier.padding(pad).padding(12.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            Column(
                Modifier.padding(pad).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                s.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

                GhCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            null,
                            tint = if (s.protected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (s.protected) "Branch is protected" else "Branch is not protected",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "$owner/$name",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        InfoIcon(
                            "Branch protection",
                            "Branch protection rules guard a branch (typically your default branch like main) " +
                                    "against unsafe changes. Toggle the options below and tap Save protection. " +
                                    "You need admin permission on the repository."
                        )
                    }
                }

                GhCard {
                    SectionHeader(
                        "Pull request reviews",
                        "Settings that control whether pull requests targeting this branch must be reviewed before they can be merged."
                    )
                    InfoSwitch(
                        label = "Require approving reviews",
                        info = "If enabled, every pull request needs at least the configured number of approving reviews before it can be merged into this branch.",
                        checked = s.requireReviews,
                        onChange = { v -> vm.update { it.copy(requireReviews = v) } }
                    )
                    if (s.requireReviews) {
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Required reviewers")
                                    InfoIcon(
                                        "Required reviewers",
                                        "How many approving reviews each pull request must collect (1–6). Reviews from the PR author don't count."
                                    )
                                }
                            },
                            trailingContent = {
                                OutlinedTextField(
                                    value = s.reviewerCount.toString(),
                                    onValueChange = { v ->
                                        v.toIntOrNull()?.let { n ->
                                            vm.update { st -> st.copy(reviewerCount = n.coerceIn(0, 6)) }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.width(80.dp)
                                )
                            }
                        )
                        InfoSwitch(
                            label = "Dismiss stale reviews on new commits",
                            info = "When new commits are pushed to a pull request, previously approving reviews are automatically dismissed and reviewers must re-approve.",
                            checked = s.dismissStale,
                            onChange = { v -> vm.update { it.copy(dismissStale = v) } }
                        )
                        InfoSwitch(
                            label = "Require code owner reviews",
                            info = "If a pull request changes files owned by someone listed in the repository's CODEOWNERS file, those owners must approve before merging.",
                            checked = s.requireCodeOwners,
                            onChange = { v -> vm.update { it.copy(requireCodeOwners = v) } }
                        )
                    }
                }

                GhCard {
                    SectionHeader(
                        "Status checks",
                        "Require automated checks (CI builds, tests, etc.) to pass before a pull request can be merged."
                    )
                    InfoSwitch(
                        label = "Require status checks to pass",
                        info = "Pull requests must wait for the configured status checks to report success before they can be merged.",
                        checked = s.requireStatusChecks,
                        onChange = { v -> vm.update { it.copy(requireStatusChecks = v) } }
                    )
                    if (s.requireStatusChecks) {
                        InfoSwitch(
                            label = "Require branch up to date (strict)",
                            info = "Pull requests must be rebased or merged with the latest version of this branch before merging — guarantees the merged code was tested against the current head.",
                            checked = s.strictStatusChecks,
                            onChange = { v -> vm.update { it.copy(strictStatusChecks = v) } }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Required check contexts", style = MaterialTheme.typography.bodyMedium)
                            InfoIcon(
                                "Required check contexts",
                                "Comma-separated names of status checks that must pass. The names must match the contexts that your CI (GitHub Actions, etc.) reports — for example: build, test, lint."
                            )
                        }
                        OutlinedTextField(
                            value = s.statusContexts,
                            onValueChange = { v -> vm.update { it.copy(statusContexts = v) } },
                            label = { Text("e.g. build, test, lint") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                GhCard {
                    SectionHeader(
                        "Other rules",
                        "Additional restrictions that apply to anyone pushing to or merging into this branch."
                    )
                    InfoSwitch(
                        label = "Include administrators",
                        info = "Apply these rules to repository administrators too. Without this, admins can bypass every protection rule.",
                        checked = s.enforceAdmins,
                        onChange = { v -> vm.update { it.copy(enforceAdmins = v) } }
                    )
                    InfoSwitch(
                        label = "Require linear history",
                        info = "Prevent merge commits. Pull requests must be merged using squash or rebase so the branch history stays linear.",
                        checked = s.linearHistory,
                        onChange = { v -> vm.update { it.copy(linearHistory = v) } }
                    )
                    InfoSwitch(
                        label = "Require conversation resolution",
                        info = "All review-comment threads must be resolved before a pull request can be merged.",
                        checked = s.requireConvResolution,
                        onChange = { v -> vm.update { it.copy(requireConvResolution = v) } }
                    )
                    InfoSwitch(
                        label = "Lock branch (read-only)",
                        info = "Make the branch read-only. No commits, merges, or pushes are allowed until it's unlocked.",
                        checked = s.lockBranch,
                        onChange = { v -> vm.update { it.copy(lockBranch = v) } }
                    )
                    InfoSwitch(
                        label = "Allow force pushes",
                        info = "Allow rewriting history on this branch with `git push --force`. Disabled by default because it can destroy other people's commits.",
                        checked = s.allowForcePushes,
                        onChange = { v -> vm.update { it.copy(allowForcePushes = v) } }
                    )
                    InfoSwitch(
                        label = "Allow deletions",
                        info = "Allow this branch to be deleted. Disabled by default to prevent accidental loss of important branches.",
                        checked = s.allowDeletions,
                        onChange = { v -> vm.update { it.copy(allowDeletions = v) } }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.save() },
                        enabled = !s.saving,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (s.saving) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Filled.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save protection")
                        }
                    }
                    if (s.protected) {
                        OutlinedButton(
                            onClick = { vm.remove() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Remove") }
                    }
                }

                EmbeddedTerminal(section = "BranchProtection")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, info: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        InfoIcon(title, info)
    }
}

@Composable
private fun InfoSwitch(
    label: String,
    info: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                InfoIcon(label, info)
            }
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}
