package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhBranch
import com.githubcontrol.data.api.GhRepo
import com.githubcontrol.data.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellCmd(
    val label: String,
    val command: String,
    val category: String,
    val description: String = "",
    /** Detailed explanation shown in the info dialog */
    val detail: String = "",
    /** Searchable use-case keywords */
    val tags: List<String> = emptyList()
)

data class GhCommandsState(
    val repos: List<GhRepo> = emptyList(),
    val reposLoading: Boolean = true,
    val selectedRepo: GhRepo? = null,
    val branches: List<GhBranch> = emptyList(),
    val branchesLoading: Boolean = false,
    val filter: String = "",
    val selectedCategory: String = "All",
    val error: String? = null
)

@HiltViewModel
class GitHubCommandsViewModel @Inject constructor(
    private val repo: GitHubRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GhCommandsState())
    val state: StateFlow<GhCommandsState> = _state

    val catalog: List<ShellCmd> = buildCatalog()
    val categories: List<String> by lazy {
        listOf("All") + catalog.map { it.category }.distinct()
    }

    init { loadRepos() }

    fun loadRepos() {
        viewModelScope.launch {
            _state.update { it.copy(reposLoading = true, error = null) }
            try {
                val repos = repo.listMyRepos(1, 100, "updated", "desc", null)
                _state.update { it.copy(reposLoading = false, repos = repos) }
            } catch (t: Throwable) {
                _state.update { it.copy(reposLoading = false, error = t.message) }
            }
        }
    }

    fun selectRepo(r: GhRepo) {
        _state.update { it.copy(selectedRepo = r, branches = emptyList(), branchesLoading = true) }
        viewModelScope.launch {
            try {
                val branches = repo.branches(r.owner.login, r.name)
                _state.update { it.copy(branches = branches, branchesLoading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(branchesLoading = false) }
            }
        }
    }

    fun clearRepo() = _state.update { it.copy(selectedRepo = null, branches = emptyList()) }
    fun setFilter(f: String) = _state.update { it.copy(filter = f) }
    fun setCategory(c: String) = _state.update { it.copy(selectedCategory = c) }

    @Suppress("LongMethod")
    private fun buildCatalog(): List<ShellCmd> = listOf(

        // ── SETUP ──────────────────────────────────────────────────────────────
        ShellCmd(
            label = "Configure user name",
            command = """git config --global user.name "{name}"""",
            category = "Setup",
            description = "Set the author name used in all commits",
            detail = "Run once after installing Git. The value appears in every commit you make on this machine. Use --local instead of --global to set it per-repository only.",
            tags = listOf("config", "author", "identity", "name", "global", "first time setup")
        ),
        ShellCmd(
            label = "Configure user email",
            command = """git config --global user.email "{email}"""",
            category = "Setup",
            description = "Set the author email used in all commits",
            detail = "Must match one of the verified emails on your GitHub account so that commits are linked to your profile. Use your GitHub noreply email for privacy.",
            tags = listOf("config", "email", "identity", "author", "global", "first time setup")
        ),
        ShellCmd(
            label = "Set default editor",
            command = """git config --global core.editor "{editor}"""",
            category = "Setup",
            description = "Choose which editor opens for commit messages",
            detail = "Common values: nano, vim, 'code --wait' (VS Code), 'subl -n -w' (Sublime). Without this, Git defaults to the system's EDITOR environment variable.",
            tags = listOf("config", "editor", "vim", "vscode", "nano")
        ),
        ShellCmd(
            label = "Enable coloured output",
            command = "git config --global color.ui auto",
            category = "Setup",
            description = "Turn on terminal colours for git output",
            detail = "Makes git diff, git log and git status far easier to read. 'auto' enables colour only when output goes to a terminal, not when piped.",
            tags = listOf("config", "color", "colour", "output", "terminal")
        ),
        ShellCmd(
            label = "List all config",
            command = "git config --list",
            category = "Setup",
            description = "Show all active Git configuration values",
            detail = "Prints every key=value pair from global, system, and local config. Add --global or --local to narrow the scope. Useful for debugging unexpected behaviour.",
            tags = listOf("config", "list", "debug", "show", "settings")
        ),
        ShellCmd(
            label = "Generate Ed25519 SSH key",
            command = """ssh-keygen -t ed25519 -C "{email}" -f ~/.ssh/id_ed25519 -N """""",
            category = "Setup",
            description = "Create a new SSH key pair (modern algorithm)",
            detail = "Ed25519 is the recommended algorithm — faster and more secure than RSA. The -C comment helps identify the key on GitHub. The -N \"\" flag sets an empty passphrase so you won't be prompted each time.",
            tags = listOf("ssh", "keygen", "auth", "key", "ed25519", "authenticate")
        ),
        ShellCmd(
            label = "Print public SSH key",
            command = "cat ~/.ssh/id_ed25519.pub",
            category = "Setup",
            description = "Show the public key to paste into GitHub",
            detail = "Copy the entire output and paste it in GitHub → Settings → SSH and GPG keys → New SSH key. Never share the private key (id_ed25519 without .pub).",
            tags = listOf("ssh", "public key", "github", "paste", "copy key")
        ),
        ShellCmd(
            label = "Start SSH agent & add key",
            command = """eval "$(ssh-agent -s)" && ssh-add ~/.ssh/id_ed25519""",
            category = "Setup",
            description = "Load your SSH key into the agent for this session",
            detail = "Required if you set a passphrase when generating the key, or if your OS doesn't auto-start the agent. Add it to ~/.bashrc / ~/.zshrc to persist across sessions.",
            tags = listOf("ssh", "agent", "add key", "session", "passphrase")
        ),
        ShellCmd(
            label = "Test GitHub SSH connection",
            command = "ssh -T git@github.com",
            category = "Setup",
            description = "Verify your SSH key is accepted by GitHub",
            detail = "You should see 'Hi username! You've successfully authenticated'. If you get 'Permission denied', the public key hasn't been added to GitHub or the agent doesn't have the key loaded.",
            tags = listOf("ssh", "test", "verify", "connection", "authenticate", "debug")
        ),

        // ── REPOSITORY ─────────────────────────────────────────────────────────
        ShellCmd(
            label = "Initialise local repo",
            command = "git init",
            category = "Repository",
            description = "Create a new empty Git repository in the current folder",
            detail = "Creates a hidden .git directory. Run in any folder to start tracking it with Git. Follow up with git remote add origin to connect it to GitHub.",
            tags = listOf("init", "new", "create", "start", "local")
        ),
        ShellCmd(
            label = "Clone via HTTPS",
            command = "git clone https://github.com/{owner}/{repo}.git",
            category = "Repository",
            description = "Download a repository using a token / password",
            detail = "HTTPS works everywhere, no SSH key needed. GitHub now requires a Personal Access Token (PAT) instead of your password. Use this on machines where you can't easily add an SSH key.",
            tags = listOf("clone", "download", "https", "token", "pat")
        ),
        ShellCmd(
            label = "Clone via SSH",
            command = "git clone git@github.com:{owner}/{repo}.git",
            category = "Repository",
            description = "Download a repository using your SSH key",
            detail = "Preferred method once SSH is set up — no password prompts and more secure. Requires your public key to be added to GitHub first.",
            tags = listOf("clone", "download", "ssh", "key")
        ),
        ShellCmd(
            label = "Clone specific branch",
            command = "git clone -b {branch} https://github.com/{owner}/{repo}.git",
            category = "Repository",
            description = "Clone and immediately check out one branch",
            detail = "The entire repo history is still downloaded, but you start on the specified branch. Combine with --single-branch to fetch only that branch's history.",
            tags = listOf("clone", "branch", "specific", "checkout")
        ),
        ShellCmd(
            label = "Shallow clone (last N commits)",
            command = "git clone --depth 1 https://github.com/{owner}/{repo}.git",
            category = "Repository",
            description = "Download only the most recent commit, not full history",
            detail = "Much faster for large repos when you only need the latest code, e.g. CI/CD pipelines. Use --depth N for the last N commits. Git operations that need full history won't work.",
            tags = listOf("clone", "shallow", "fast", "ci", "depth", "speed")
        ),
        ShellCmd(
            label = "Add remote origin (HTTPS)",
            command = "git remote add origin https://github.com/{owner}/{repo}.git",
            category = "Repository",
            description = "Link a local repo to a GitHub repository",
            detail = "Run after git init to connect your local work to GitHub. 'origin' is the conventional name for the primary remote. Then use git push -u origin main for the first push.",
            tags = listOf("remote", "add", "origin", "connect", "link", "github")
        ),
        ShellCmd(
            label = "Add remote origin (SSH)",
            command = "git remote add origin git@github.com:{owner}/{repo}.git",
            category = "Repository",
            description = "Link a local repo to GitHub using SSH",
            detail = "Same as the HTTPS variant but uses SSH for authentication. Preferred when your SSH key is already configured.",
            tags = listOf("remote", "add", "origin", "ssh", "connect", "link")
        ),
        ShellCmd(
            label = "Switch remote → SSH",
            command = "git remote set-url origin git@github.com:{owner}/{repo}.git",
            category = "Repository",
            description = "Change an existing HTTPS remote to SSH",
            detail = "Use this when you've already cloned via HTTPS but want to switch to SSH to avoid repeated token prompts. Verify with git remote -v.",
            tags = listOf("remote", "set-url", "change", "switch", "ssh", "https")
        ),
        ShellCmd(
            label = "Switch remote → HTTPS",
            command = "git remote set-url origin https://github.com/{owner}/{repo}.git",
            category = "Repository",
            description = "Change an existing SSH remote to HTTPS",
            detail = "Useful in environments where SSH is blocked (corporate firewalls) or you're switching to token-based auth. Verify with git remote -v.",
            tags = listOf("remote", "set-url", "change", "switch", "https", "ssh")
        ),
        ShellCmd(
            label = "Remove a remote",
            command = "git remote remove origin",
            category = "Repository",
            description = "Disconnect the remote from this local repo",
            detail = "Useful when you want to re-link to a different remote or clean up stale remotes. Doesn't delete anything on GitHub.",
            tags = listOf("remote", "remove", "delete", "disconnect")
        ),
        ShellCmd(
            label = "List remotes",
            command = "git remote -v",
            category = "Repository",
            description = "Show all configured remotes and their URLs",
            detail = "The -v (verbose) flag shows both fetch and push URLs. If you've switched between SSH and HTTPS you can confirm which URL is active here.",
            tags = listOf("remote", "list", "show", "verbose", "url")
        ),

        // ── BASIC ──────────────────────────────────────────────────────────────
        ShellCmd(
            label = "Show status",
            command = "git status",
            category = "Basic",
            description = "See staged, unstaged, and untracked files",
            detail = "The most commonly used command. Shows which branch you're on, what's staged for the next commit, what's changed but not staged, and any untracked new files.",
            tags = listOf("status", "check", "changed", "staged", "untracked")
        ),
        ShellCmd(
            label = "Short status",
            command = "git status -s",
            category = "Basic",
            description = "Compact one-line-per-file status view",
            detail = "Letters: M = modified, A = added/staged, ? = untracked, D = deleted. First column = staged, second = unstaged. Faster to scan than the verbose output.",
            tags = listOf("status", "short", "compact", "quick")
        ),
        ShellCmd(
            label = "Stage all changes",
            command = "git add .",
            category = "Basic",
            description = "Stage every new and modified file in the current directory",
            detail = "The dot (.) means 'current directory and below'. Run from the repo root to stage everything. Doesn't stage files that are in .gitignore.",
            tags = listOf("add", "stage", "all", "dot")
        ),
        ShellCmd(
            label = "Stage a specific file",
            command = "git add {file}",
            category = "Basic",
            description = "Stage one file or path for the next commit",
            detail = "You can pass a file path, a glob (*.kt), or a directory. Staging selectively lets you create focused commits even if you changed many files at once.",
            tags = listOf("add", "stage", "file", "specific", "path")
        ),
        ShellCmd(
            label = "Interactive staging",
            command = "git add -p",
            category = "Basic",
            description = "Choose exactly which hunks to stage (patch mode)",
            detail = "Walks through each changed hunk and asks y/n/s/e. Perfect for splitting a large change into logical commits. Requires a terminal that supports interactive input.",
            tags = listOf("add", "patch", "interactive", "hunk", "partial", "selective staging")
        ),
        ShellCmd(
            label = "Unstage a file",
            command = "git restore --staged {file}",
            category = "Basic",
            description = "Remove a file from the staging area without losing changes",
            detail = "The file stays modified on disk; it's just removed from the index. Use this if you accidentally staged something you didn't mean to commit yet.",
            tags = listOf("unstage", "restore", "remove from index", "undo stage")
        ),
        ShellCmd(
            label = "Commit staged changes",
            command = """git commit -m "{message}"""",
            category = "Basic",
            description = "Save staged changes to the repository history",
            detail = "The -m flag lets you provide the message inline. Good commit messages use the imperative mood: 'Fix login bug' not 'Fixed login bug'. Keep the subject under 72 chars.",
            tags = listOf("commit", "save", "message", "history")
        ),
        ShellCmd(
            label = "Stage & commit in one step",
            command = """git commit -am "{message}"""",
            category = "Basic",
            description = "Stage all tracked changes and commit at once",
            detail = "-a stages modifications and deletions to tracked files only — it does NOT add brand-new untracked files. Use git add . first if you have new files.",
            tags = listOf("commit", "add", "stage", "shortcut", "tracked")
        ),
        ShellCmd(
            label = "Amend last commit",
            command = "git commit --amend --no-edit",
            category = "Basic",
            description = "Fold staged changes into the previous commit",
            detail = "Use when you forgot to stage a file before committing. --no-edit keeps the existing message. WARNING: never amend commits that have been pushed to a shared branch.",
            tags = listOf("amend", "fix", "last commit", "edit", "update commit")
        ),
        ShellCmd(
            label = "Amend commit message",
            command = "git commit --amend -m \"{message}\"",
            category = "Basic",
            description = "Rewrite the message of the most recent commit",
            detail = "Only changes the message, not the content. Opens the editor if you omit -m. WARNING: rewrites history — only do this before pushing.",
            tags = listOf("amend", "message", "rename commit", "fix typo", "edit message")
        ),
        ShellCmd(
            label = "Empty commit",
            command = """git commit --allow-empty -m "{message}"""",
            category = "Basic",
            description = "Create a commit with no file changes",
            detail = "Useful to trigger a CI/CD pipeline manually, mark a deploy point, or start a branch with a meaningful root commit.",
            tags = listOf("empty", "trigger ci", "placeholder", "no changes")
        ),
        ShellCmd(
            label = "Push to branch",
            command = "git push origin {branch}",
            category = "Basic",
            description = "Upload local commits to the remote branch",
            detail = "Use git push -u origin {branch} the first time to set the tracking relationship — after that a bare git push is enough.",
            tags = listOf("push", "upload", "remote", "publish", "sync")
        ),
        ShellCmd(
            label = "Push & set upstream",
            command = "git push -u origin {branch}",
            category = "Basic",
            description = "Push and link local branch to remote for future pushes",
            detail = "The -u flag sets the upstream so later you can just run 'git push' or 'git pull' without specifying the remote and branch every time.",
            tags = listOf("push", "upstream", "tracking", "link", "set-upstream", "-u")
        ),
        ShellCmd(
            label = "Force push (safe)",
            command = "git push --force-with-lease origin {branch}",
            category = "Basic",
            description = "Force-push only if nobody else has pushed since your last fetch",
            detail = "Safer than --force because it fails if someone else pushed after you last fetched. Use after rebasing or amending commits that were already pushed.",
            tags = listOf("push", "force", "overwrite", "rebase", "lease", "safe force push")
        ),
        ShellCmd(
            label = "Pull from branch",
            command = "git pull origin {branch}",
            category = "Basic",
            description = "Download and merge remote changes into the current branch",
            detail = "Equivalent to git fetch followed by git merge. If there are conflicts you'll need to resolve them manually. Consider --rebase for a cleaner history.",
            tags = listOf("pull", "download", "merge", "sync", "update")
        ),
        ShellCmd(
            label = "Pull with rebase",
            command = "git pull --rebase origin {branch}",
            category = "Basic",
            description = "Pull and replay your local commits on top of remote changes",
            detail = "Creates a linear history instead of merge commits. Preferred in teams that value clean history. If conflicts occur, resolve them and run git rebase --continue.",
            tags = listOf("pull", "rebase", "linear", "history", "no merge commit")
        ),
        ShellCmd(
            label = "Fetch all remotes",
            command = "git fetch --all",
            category = "Basic",
            description = "Download all remote changes without touching your working tree",
            detail = "Safe to run any time — it never modifies your local branches. Updates remote-tracking refs so you can see what's new before merging or rebasing.",
            tags = listOf("fetch", "download", "safe", "update refs", "no merge")
        ),
        ShellCmd(
            label = "Fetch & prune deleted branches",
            command = "git fetch --prune",
            category = "Basic",
            description = "Fetch and remove refs to remote branches that no longer exist",
            detail = "Cleans up stale remote-tracking branches that have been deleted on the server. Especially useful after many branches have been merged and deleted on GitHub.",
            tags = listOf("fetch", "prune", "clean", "stale", "deleted branches")
        ),

        // ── BRANCHING ──────────────────────────────────────────────────────────
        ShellCmd(
            label = "List local branches",
            command = "git branch",
            category = "Branching",
            description = "Show all local branches; current branch is starred",
            detail = "The asterisk (*) marks the currently checked-out branch. Add -v to show the latest commit on each branch.",
            tags = listOf("branch", "list", "show", "local")
        ),
        ShellCmd(
            label = "List all branches (+ remote)",
            command = "git branch -a",
            category = "Branching",
            description = "Show both local and remote-tracking branches",
            detail = "Remote branches are prefixed with remotes/origin/. Use -r to show only remote-tracking branches. Run git fetch first to make sure the list is current.",
            tags = listOf("branch", "list", "all", "remote", "tracking")
        ),
        ShellCmd(
            label = "Create new branch",
            command = "git branch {branch}",
            category = "Branching",
            description = "Create a branch at the current commit without switching to it",
            detail = "Creates the branch pointer but keeps you on the current branch. Use checkout -b or switch -c to create AND switch in one step.",
            tags = listOf("branch", "create", "new branch", "make branch")
        ),
        ShellCmd(
            label = "Create & switch to branch",
            command = "git checkout -b {branch}",
            category = "Branching",
            description = "Create a new branch and immediately switch to it",
            detail = "The most common way to start work on a new feature. By default the new branch starts at the current HEAD. Add a commit SHA or branch name to start from a specific point.",
            tags = listOf("checkout", "branch", "create", "switch", "new branch", "-b")
        ),
        ShellCmd(
            label = "Create & switch (modern syntax)",
            command = "git switch -c {branch}",
            category = "Branching",
            description = "Modern equivalent of checkout -b",
            detail = "'git switch' was introduced in Git 2.23 to separate switching and restoring. Preferred in newer workflows. -c means --create.",
            tags = listOf("switch", "create", "new branch", "modern", "git 2.23")
        ),
        ShellCmd(
            label = "Switch to existing branch",
            command = "git checkout {branch}",
            category = "Branching",
            description = "Move to an already-existing local or remote branch",
            detail = "If the branch only exists on the remote, Git will create a local tracking branch automatically. Use 'git switch {branch}' for the modern equivalent.",
            tags = listOf("checkout", "switch", "change branch", "move to branch")
        ),
        ShellCmd(
            label = "Switch branch (modern syntax)",
            command = "git switch {branch}",
            category = "Branching",
            description = "Move to an existing branch using the modern command",
            detail = "Introduced in Git 2.23 to make the intent clearer — 'switch' is only for changing branches, unlike 'checkout' which also restores files.",
            tags = listOf("switch", "change branch", "modern", "checkout alternative")
        ),
        ShellCmd(
            label = "Switch to previous branch",
            command = "git switch -",
            category = "Branching",
            description = "Jump back to the branch you were on before",
            detail = "The dash is a shorthand for @{-1} (the previous branch). Works just like 'cd -' in a shell. Also available as git checkout -.",
            tags = listOf("switch", "previous", "back", "last branch", "toggle")
        ),
        ShellCmd(
            label = "Rename current branch",
            command = "git branch -m {branch}",
            category = "Branching",
            description = "Rename the branch you are currently on",
            detail = "Only renames locally. To update the remote: push the renamed branch with git push origin {branch}, then delete the old remote branch.",
            tags = listOf("rename", "branch", "move", "-m")
        ),
        ShellCmd(
            label = "Delete local branch (safe)",
            command = "git branch -d {branch}",
            category = "Branching",
            description = "Delete a branch only if it has been merged",
            detail = "Fails if the branch has unmerged commits, which prevents accidental data loss. Use -D (capital) to force-delete regardless.",
            tags = listOf("delete", "branch", "remove", "merged", "safe delete")
        ),
        ShellCmd(
            label = "Force-delete local branch",
            command = "git branch -D {branch}",
            category = "Branching",
            description = "Delete a branch regardless of merge status",
            detail = "Use when you're sure you don't need the branch and -d is refusing. The commits are still reachable via reflog for 30 days.",
            tags = listOf("delete", "force", "branch", "remove", "unmerged", "-D")
        ),
        ShellCmd(
            label = "Delete remote branch",
            command = "git push origin --delete {branch}",
            category = "Branching",
            description = "Remove a branch from the remote repository",
            detail = "Other team members will see the branch disappear after their next git fetch --prune. Make sure the branch is merged or backed up first.",
            tags = listOf("delete", "remote", "branch", "push", "remove")
        ),
        ShellCmd(
            label = "Merge branch into current",
            command = "git merge {branch}",
            category = "Branching",
            description = "Integrate another branch's history into the current branch",
            detail = "Creates a merge commit if histories have diverged (fast-forward otherwise). On conflicts: resolve files, then git add + git commit to finish the merge.",
            tags = listOf("merge", "integrate", "combine", "join", "merge commit")
        ),
        ShellCmd(
            label = "Merge (no fast-forward)",
            command = "git merge --no-ff {branch}",
            category = "Branching",
            description = "Always create a merge commit, even on fast-forward",
            detail = "Preserves the fact that a feature branch existed. Useful in workflows like git flow where you want every feature integration visible in the history.",
            tags = listOf("merge", "no-ff", "merge commit", "preserve history")
        ),
        ShellCmd(
            label = "Abort in-progress merge",
            command = "git merge --abort",
            category = "Branching",
            description = "Cancel a merge that has conflicts and restore previous state",
            detail = "Returns the working tree to exactly how it was before git merge was run. Use when the conflicts are too complex and you want to rethink the approach.",
            tags = listOf("merge", "abort", "cancel", "undo merge", "conflict")
        ),
        ShellCmd(
            label = "Rebase onto branch",
            command = "git rebase {branch}",
            category = "Branching",
            description = "Replay local commits on top of another branch",
            detail = "Creates a cleaner linear history than merging. Rewrites commit SHAs — do NOT rebase commits that are already on a shared/public branch.",
            tags = listOf("rebase", "linear", "replay", "history", "clean history")
        ),
        ShellCmd(
            label = "Interactive rebase (last N commits)",
            command = "git rebase -i HEAD~{n}",
            category = "Branching",
            description = "Reorder, squash, edit, or drop the last N commits",
            detail = "Opens an editor listing the last N commits. Actions: pick (keep), squash (combine with previous), fixup (squash + discard message), reword (edit message), drop. Powerful but rewrites history.",
            tags = listOf("rebase", "interactive", "squash", "fixup", "reword", "drop", "edit commits", "-i")
        ),
        ShellCmd(
            label = "Continue rebase after conflict",
            command = "git rebase --continue",
            category = "Branching",
            description = "Resume a paused rebase after resolving a conflict",
            detail = "After resolving conflicts and running git add, use this to move to the next commit in the rebase. Repeat until the rebase finishes.",
            tags = listOf("rebase", "continue", "conflict", "resume")
        ),
        ShellCmd(
            label = "Abort in-progress rebase",
            command = "git rebase --abort",
            category = "Branching",
            description = "Cancel a rebase and return to the pre-rebase state",
            detail = "Restores your branch to the state it was in before you started the rebase. Safe to run at any point during a rebase.",
            tags = listOf("rebase", "abort", "cancel", "undo rebase")
        ),
        ShellCmd(
            label = "Cherry-pick a commit",
            command = "git cherry-pick {sha}",
            category = "Branching",
            description = "Apply a single commit from any branch to the current one",
            detail = "Creates a new commit with the same changes but a new SHA. Use for hot-fixing production branches by picking a fix commit from develop, without merging the whole branch.",
            tags = listOf("cherry-pick", "apply commit", "backport", "hotfix", "pick")
        ),

        // ── LOG & INSPECT ──────────────────────────────────────────────────────
        ShellCmd(
            label = "Short log (last 20)",
            command = "git log --oneline -20",
            category = "Log & Inspect",
            description = "View the last 20 commits, one line each",
            detail = "Each line: short SHA + commit message. Great for a quick orientation. Increase or decrease 20 as needed, or drop the limit entirely for the full history.",
            tags = listOf("log", "history", "oneline", "commits", "short")
        ),
        ShellCmd(
            label = "Graph log (all branches)",
            command = "git log --oneline --graph --all",
            category = "Log & Inspect",
            description = "Visual ASCII branch/merge graph in the terminal",
            detail = "Shows how all branches diverge and converge. Add --decorate to see branch and tag labels. Pipe to 'less' for long histories.",
            tags = listOf("log", "graph", "branches", "tree", "history", "visual")
        ),
        ShellCmd(
            label = "Full log with stats",
            command = "git log --stat",
            category = "Log & Inspect",
            description = "Log with number of files changed and insertions/deletions",
            detail = "Shows which files changed in each commit along with a summary of lines added/deleted. Good for understanding how a codebase evolved.",
            tags = listOf("log", "stat", "files changed", "lines", "history detail")
        ),
        ShellCmd(
            label = "Show a specific commit",
            command = "git show {sha}",
            category = "Log & Inspect",
            description = "Display the full diff and metadata of a commit",
            detail = "Shows author, date, message, and the complete patch. You can use a branch name or tag instead of a SHA to show the latest commit on that ref.",
            tags = listOf("show", "commit", "diff", "detail", "sha", "inspect")
        ),
        ShellCmd(
            label = "Diff unstaged changes",
            command = "git diff",
            category = "Log & Inspect",
            description = "Show what has changed but not yet been staged",
            detail = "Red lines are removed, green are added. Use 'git diff --stat' for a summary. If everything is staged, this output is empty — use git diff --staged instead.",
            tags = listOf("diff", "changes", "unstaged", "modified")
        ),
        ShellCmd(
            label = "Diff staged changes",
            command = "git diff --staged",
            category = "Log & Inspect",
            description = "Show what is staged and ready to commit",
            detail = "Also callable as git diff --cached. Shows exactly what will be included in the next commit. Run this to review before committing.",
            tags = listOf("diff", "staged", "cached", "review", "before commit")
        ),
        ShellCmd(
            label = "Diff two branches",
            command = "git diff {base}..{head}",
            category = "Log & Inspect",
            description = "Show all differences between two branches or refs",
            detail = "Two dots (..) compares the tips of both refs directly. Three dots (...) compares the head of head against the common ancestor — useful for pull request diffs.",
            tags = listOf("diff", "branches", "compare", "two dots", "pr review")
        ),
        ShellCmd(
            label = "Blame a file",
            command = "git blame {file}",
            category = "Log & Inspect",
            description = "Show which commit last changed each line of a file",
            detail = "Each line shows: SHA, author, date, line number, content. Useful for understanding why a specific line exists. -L 10,20 limits to lines 10–20.",
            tags = listOf("blame", "who changed", "line history", "annotate", "author")
        ),
        ShellCmd(
            label = "Search code in history",
            command = "git grep {pattern}",
            category = "Log & Inspect",
            description = "Find a string in all tracked files",
            detail = "Faster than grep because it only searches tracked files. Add -n for line numbers, -i for case-insensitive. Use 'git grep {pattern} HEAD~5' to search an older revision.",
            tags = listOf("grep", "search", "find", "code", "string", "text search")
        ),
        ShellCmd(
            label = "Find which commit introduced a change",
            command = "git log -S {pattern} --oneline",
            category = "Log & Inspect",
            description = "Pickaxe search — find commits that added or removed a string",
            detail = "The -S flag (pickaxe) searches the diff content, not the messages. Great for hunting down when a function was added or a bug was introduced.",
            tags = listOf("log", "search", "pickaxe", "-S", "when added", "find commit")
        ),
        ShellCmd(
            label = "Contributor summary",
            command = "git shortlog -sn",
            category = "Log & Inspect",
            description = "List contributors ranked by number of commits",
            detail = "-s suppresses the commit list, -n sorts by count. Great for understanding who owns which area. Add --no-merges to exclude merge commits.",
            tags = listOf("shortlog", "contributors", "count", "who committed", "stats")
        ),

        // ── STASH ──────────────────────────────────────────────────────────────
        ShellCmd(
            label = "Stash current changes",
            command = "git stash",
            category = "Stash",
            description = "Save uncommitted work and revert to a clean state",
            detail = "Stashes both staged and unstaged changes in tracked files. Working tree becomes clean so you can switch branches or pull. Doesn't stash untracked or ignored files by default.",
            tags = listOf("stash", "save", "hide", "temporary", "clean state")
        ),
        ShellCmd(
            label = "Stash including untracked files",
            command = "git stash -u",
            category = "Stash",
            description = "Stash changes plus new untracked files",
            detail = "-u includes untracked files (but not ignored ones). Use -a to include everything including .gitignore files.",
            tags = listOf("stash", "untracked", "new files", "-u", "include all")
        ),
        ShellCmd(
            label = "Stash with a label",
            command = """git stash save "{message}"""",
            category = "Stash",
            description = "Save stash with a descriptive name for easy identification",
            detail = "Without a message, stash entries are named like 'WIP on main: abc1234 Some commit'. A label makes git stash list much more readable.",
            tags = listOf("stash", "save", "label", "name", "message", "identify")
        ),
        ShellCmd(
            label = "List all stashes",
            command = "git stash list",
            category = "Stash",
            description = "Show all saved stash entries",
            detail = "Format: stash@{0}: WIP on branch: SHA message. stash@{0} is the most recent. Numbers increase going back in time.",
            tags = listOf("stash", "list", "show", "all stashes", "history")
        ),
        ShellCmd(
            label = "Apply & remove latest stash",
            command = "git stash pop",
            category = "Stash",
            description = "Restore the most recent stash and delete it from the stack",
            detail = "The typical 'undo stash' command. If there are conflicts, the stash is NOT removed — resolve conflicts and run git stash drop manually.",
            tags = listOf("stash", "pop", "apply", "restore", "remove")
        ),
        ShellCmd(
            label = "Apply stash (keep it)",
            command = "git stash apply",
            category = "Stash",
            description = "Restore the latest stash without removing it from the stack",
            detail = "Useful if you want to apply the same stash to multiple branches. Specify stash@{N} to apply a specific stash.",
            tags = listOf("stash", "apply", "restore", "keep", "multiple branches")
        ),
        ShellCmd(
            label = "Drop a specific stash",
            command = "git stash drop stash@{0}",
            category = "Stash",
            description = "Delete one stash entry from the stack",
            detail = "Use git stash list first to identify the index. stash@{0} is the most recent. This cannot be undone.",
            tags = listOf("stash", "drop", "delete", "remove", "clean up")
        ),
        ShellCmd(
            label = "Clear all stashes",
            command = "git stash clear",
            category = "Stash",
            description = "Delete every stash entry permanently",
            detail = "Cannot be undone. Run git stash list first to confirm you don't need any of the stashes.",
            tags = listOf("stash", "clear", "delete all", "clean", "remove all")
        ),

        // ── RESET & CLEAN ──────────────────────────────────────────────────────
        ShellCmd(
            label = "Discard all unstaged changes",
            command = "git restore .",
            category = "Reset & Clean",
            description = "Revert all tracked files to their last committed state",
            detail = "Non-destructive to the index (staged changes are preserved). Permanent for unstaged changes — there's no undo. Untracked files are not affected.",
            tags = listOf("restore", "discard", "revert", "unstaged", "undo changes")
        ),
        ShellCmd(
            label = "Discard changes to one file",
            command = "git restore {file}",
            category = "Reset & Clean",
            description = "Revert a single file to its last committed state",
            detail = "Only affects the working tree, not the index. If the file is also staged, run git restore --staged {file} first to remove it from the index.",
            tags = listOf("restore", "discard", "file", "revert file", "undo")
        ),
        ShellCmd(
            label = "Undo last commit (keep staged)",
            command = "git reset --soft HEAD~1",
            category = "Reset & Clean",
            description = "Move HEAD back one commit; changes remain staged",
            detail = "The safest reset. All changes from the undone commit are kept in the index ready to be re-committed. Use when you want to redo the commit with different changes or a better message.",
            tags = listOf("reset", "soft", "undo commit", "HEAD~1", "keep staged")
        ),
        ShellCmd(
            label = "Undo last commit (keep unstaged)",
            command = "git reset HEAD~1",
            category = "Reset & Clean",
            description = "Move HEAD back; changes are unstaged (mixed reset)",
            detail = "The default reset mode. Changes are preserved in the working tree but removed from the index. Use to break a commit apart and recommit selectively.",
            tags = listOf("reset", "mixed", "undo commit", "unstage", "HEAD~1")
        ),
        ShellCmd(
            label = "Hard reset to HEAD",
            command = "git reset --hard HEAD",
            category = "Reset & Clean",
            description = "Discard ALL uncommitted changes — cannot be undone",
            detail = "Resets both index and working tree to the last commit. Staged and unstaged changes are permanently lost. Untracked files remain.",
            tags = listOf("reset", "hard", "discard all", "clean", "dangerous", "undo")
        ),
        ShellCmd(
            label = "Hard reset to a commit",
            command = "git reset --hard {sha}",
            category = "Reset & Clean",
            description = "Roll back branch to a specific past commit",
            detail = "All commits after the given SHA are discarded from the branch. The commits still exist in reflog for 30 days. Never do this on shared/public branches.",
            tags = listOf("reset", "hard", "rollback", "specific commit", "dangerous")
        ),
        ShellCmd(
            label = "Revert a commit (safe undo)",
            command = "git revert {sha}",
            category = "Reset & Clean",
            description = "Create a new commit that undoes the specified commit",
            detail = "Unlike reset, revert preserves history — safe for shared branches. Use when you need to undo something that was already pushed. Resolves conflicts if needed, then commits.",
            tags = listOf("revert", "undo", "safe", "new commit", "shared branch")
        ),
        ShellCmd(
            label = "Remove untracked files",
            command = "git clean -fd",
            category = "Reset & Clean",
            description = "Delete all untracked files and directories",
            detail = "-f is required (safety guard). -d includes untracked directories. Run with -n (dry-run) first to preview what will be deleted.",
            tags = listOf("clean", "untracked", "delete", "remove files", "directory")
        ),
        ShellCmd(
            label = "Dry-run clean (preview)",
            command = "git clean -nfd",
            category = "Reset & Clean",
            description = "Preview which untracked files would be deleted",
            detail = "The -n flag means 'dry run' — nothing is deleted. Always run this before git clean -fd to avoid surprises.",
            tags = listOf("clean", "dry run", "preview", "safe", "-n")
        ),
        ShellCmd(
            label = "Remove untracked + ignored files",
            command = "git clean -fdx",
            category = "Reset & Clean",
            description = "Delete untracked files AND .gitignore files (build artefacts etc.)",
            detail = "-x ignores .gitignore rules so build artefacts, node_modules, and other ignored files are also deleted. Useful for a truly clean build.",
            tags = listOf("clean", "ignored", "build", "artefacts", "-x", "node_modules")
        ),

        // ── TAGS & RELEASES ────────────────────────────────────────────────────
        ShellCmd(
            label = "Create annotated tag",
            command = """git tag -a v{version} -m "Release v{version}"""",
            category = "Tags & Releases",
            description = "Create a tag with a message — used for release points",
            detail = "Annotated tags store extra metadata: tagger, date, message. They are the recommended type for releases. GitHub uses tags as the basis for releases.",
            tags = listOf("tag", "release", "annotated", "version", "mark")
        ),
        ShellCmd(
            label = "Create lightweight tag",
            command = "git tag v{version}",
            category = "Tags & Releases",
            description = "Create a simple tag pointer with no extra metadata",
            detail = "Lightweight tags are just pointers to a commit — no tagger info or message. Fine for private bookmarks but annotated tags are preferred for releases.",
            tags = listOf("tag", "lightweight", "simple", "pointer")
        ),
        ShellCmd(
            label = "Push single tag",
            command = "git push origin v{version}",
            category = "Tags & Releases",
            description = "Upload one specific tag to the remote",
            detail = "Tags are not pushed automatically with git push. You must push them explicitly, or use --tags to push all at once.",
            tags = listOf("tag", "push", "upload", "single", "release")
        ),
        ShellCmd(
            label = "Push all tags",
            command = "git push origin --tags",
            category = "Tags & Releases",
            description = "Upload all local tags to the remote at once",
            detail = "Pushes every tag that doesn't exist on the remote. Use with care in team projects — coordinate tag names first.",
            tags = listOf("tag", "push", "all tags", "upload", "batch")
        ),
        ShellCmd(
            label = "Delete local tag",
            command = "git tag -d v{version}",
            category = "Tags & Releases",
            description = "Remove a tag from your local repository",
            detail = "Does not affect the remote tag. To delete the remote tag too, follow with: git push origin --delete v{version}",
            tags = listOf("tag", "delete", "remove", "local")
        ),
        ShellCmd(
            label = "Delete remote tag",
            command = "git push origin --delete v{version}",
            category = "Tags & Releases",
            description = "Remove a tag from the remote repository",
            detail = "Other users will still see it until they run git fetch --prune --tags. First delete locally with git tag -d v{version} so your local copy is in sync.",
            tags = listOf("tag", "delete", "remote", "remove")
        ),
        ShellCmd(
            label = "List all tags",
            command = "git tag -l",
            category = "Tags & Releases",
            description = "Show all tags in the repository",
            detail = "Add a pattern to filter: git tag -l 'v2.*'. Tags are listed alphabetically, not by date. Use git log --tags --simplify-by-decoration for date-ordered tags.",
            tags = listOf("tag", "list", "show", "all", "versions")
        ),
        ShellCmd(
            label = "Checkout a tag (detached HEAD)",
            command = "git checkout v{version}",
            category = "Tags & Releases",
            description = "View code at a specific tagged release",
            detail = "Puts you in 'detached HEAD' state — not on any branch. Safe for reading/building but create a new branch if you want to make changes: git checkout -b hotfix v1.0",
            tags = listOf("checkout", "tag", "version", "detached head", "release")
        ),

        // ── ADVANCED ───────────────────────────────────────────────────────────
        ShellCmd(
            label = "Bisect: start session",
            command = "git bisect start",
            category = "Advanced",
            description = "Begin a binary search to find which commit introduced a bug",
            detail = "Git bisect binary-searches through your history. After starting, mark a bad commit with 'git bisect bad' and a known-good older commit with 'git bisect good {sha}'. Git checks out commits for you to test.",
            tags = listOf("bisect", "bug hunt", "find commit", "binary search", "debug")
        ),
        ShellCmd(
            label = "Bisect: mark current as bad",
            command = "git bisect bad",
            category = "Advanced",
            description = "Tell Git the bug exists at the current commit",
            detail = "Used during a bisect session. Git narrows the range and checks out another commit to test. Repeat until the first bad commit is identified.",
            tags = listOf("bisect", "bad", "bug", "mark")
        ),
        ShellCmd(
            label = "Bisect: mark commit as good",
            command = "git bisect good {sha}",
            category = "Advanced",
            description = "Tell Git a known-good commit (bug not present)",
            detail = "Start with a SHA far enough back that you're certain the bug wasn't there. Git will binary-search the range between good and bad.",
            tags = listOf("bisect", "good", "known good", "sha")
        ),
        ShellCmd(
            label = "Bisect: end session",
            command = "git bisect reset",
            category = "Advanced",
            description = "Finish bisecting and return to the original branch",
            detail = "Always run this when done or if you want to abort. Returns HEAD to where it was before you started bisecting.",
            tags = listOf("bisect", "reset", "end", "finish", "return")
        ),
        ShellCmd(
            label = "Add submodule",
            command = "git submodule add {url}",
            category = "Advanced",
            description = "Embed another repository as a subdirectory",
            detail = "Creates a .gitmodules file and pins the submodule at a specific commit. Collaborators need to run git submodule update --init after cloning.",
            tags = listOf("submodule", "embed", "dependency", "nested repo")
        ),
        ShellCmd(
            label = "Init & update submodules",
            command = "git submodule update --init --recursive",
            category = "Advanced",
            description = "Initialise and fetch all submodules after cloning",
            detail = "Use after cloning a repo that has submodules. --recursive handles nested submodules. Without this, submodule directories will be empty.",
            tags = listOf("submodule", "update", "init", "clone", "recursive")
        ),
        ShellCmd(
            label = "Add a worktree",
            command = "git worktree add {path} {branch}",
            category = "Advanced",
            description = "Check out a branch into a separate directory without cloning",
            detail = "Lets you work on two branches simultaneously in different directories, sharing the same .git database. Great for reviewing a PR while working on another feature.",
            tags = listOf("worktree", "multiple branches", "parallel work", "two branches")
        ),
        ShellCmd(
            label = "Archive repo as ZIP",
            command = "git archive --format=zip HEAD > {file}.zip",
            category = "Advanced",
            description = "Export the current commit as a ZIP archive",
            detail = "Creates a clean archive without the .git directory. Useful for distributing source snapshots. Replace HEAD with a branch name, tag, or SHA to archive a specific version.",
            tags = listOf("archive", "zip", "export", "snapshot", "distribute")
        ),
        ShellCmd(
            label = "Recover lost commits via reflog",
            command = "git reflog",
            category = "Advanced",
            description = "Show the history of HEAD movements — find lost commits",
            detail = "Reflog records every time HEAD moved, including resets and branch switches. If you accidentally reset --hard or deleted a branch, find the SHA here and run git checkout -b recovery {sha} to restore it.",
            tags = listOf("reflog", "recover", "lost commit", "undo reset", "restore", "safety net")
        ),
    )
}
