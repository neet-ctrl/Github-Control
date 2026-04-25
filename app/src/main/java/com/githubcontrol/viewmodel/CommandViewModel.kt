package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.CreateIssueRequest
import com.githubcontrol.data.api.CreateRepoRequest
import com.githubcontrol.data.auth.AccountManager
import com.githubcontrol.data.db.AppDatabase
import com.githubcontrol.data.db.CommandHistoryEntity
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.fromBase64
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommandLine(val cmd: String, val output: String, val ok: Boolean)

/**
 * One catalog entry shown in the Command screen list. Mirrors the dispatch
 * table in [CommandViewModel.execute] — keep both in sync when adding
 * commands.
 */
data class CommandSpec(
    val name: String,
    val template: String,
    val category: String,
    val description: String,
    val example: String
)

data class CommandState(
    val input: String = "",
    val lines: List<CommandLine> = emptyList(),
    val running: Boolean = false
)

@HiltViewModel
class CommandViewModel @Inject constructor(
    private val repo: GitHubRepository,
    private val db: AppDatabase,
    private val accounts: AccountManager
) : ViewModel() {
    private val _state = MutableStateFlow(CommandState())
    val state: StateFlow<CommandState> = _state

    fun setInput(t: String) { _state.value = _state.value.copy(input = t) }

    /** Insert text at the input field — used by the catalog "copy / try" rows. */
    fun useTemplate(t: String) { _state.value = _state.value.copy(input = t) }

    fun run() {
        val raw = _state.value.input.trim()
        if (raw.isEmpty()) return
        _state.value = _state.value.copy(running = true)
        viewModelScope.launch {
            Logger.i("Command", "$ $raw")
            val out = execute(raw)
            if (out.ok) Logger.i("Command", out.output.lineSequence().firstOrNull().orEmpty())
            else Logger.e("Command", out.output)
            val accId = accounts.activeAccount()?.id ?: "anon"
            db.commandHistory().insert(CommandHistoryEntity(accountId = accId, command = raw, output = out.output, success = out.ok))
            _state.value = CommandState(input = "", lines = _state.value.lines + CommandLine(raw, out.output, out.ok), running = false)
        }
    }

    private suspend fun execute(line: String): CommandLine {
        val parts = tokenize(line)
        val cmd = parts.firstOrNull() ?: return CommandLine(line, "Empty", false)
        val args = parts.drop(1)
        return try {
            when (cmd) {
                "help" -> CommandLine(line, helpText(), true)
                "me" -> {
                    val u = repo.me()
                    CommandLine(line, "${u.login} (${u.name ?: ""}) — ${u.publicRepos} public repos, ${u.followers} followers", true)
                }
                "rate" -> {
                    val r = repo.rateLimit().rate
                    CommandLine(line, "core: ${r.remaining}/${r.limit} (resets ${r.reset})", true)
                }
                "whoami" -> {
                    val a = accounts.activeAccount()
                    CommandLine(line, if (a == null) "no active account" else "${a.login} · scopes=${a.scopes.joinToString(",")}", true)
                }
                "accounts" -> {
                    val all = accounts.accountsBlocking()
                    val act = accounts.activeAccount()?.id
                    CommandLine(line, all.joinToString("\n") { "${if (it.id == act) "*" else " "} ${it.login}" }, true)
                }
                "repo" -> repoCmd(args, line)
                "branch" -> branchCmd(args, line)
                "issue" -> issueCmd(args, line)
                "pr" -> prCmd(args, line)
                "release" -> releaseCmd(args, line)
                "commits" -> commitsCmd(args, line)
                "tree" -> treeCmd(args, line)
                "file" -> fileCmd(args, line)
                "user" -> userCmd(args, line)
                "search" -> searchCmd(args, line)
                "starred" -> {
                    val me = accounts.activeAccount()?.login
                    if (me == null) CommandLine(line, "No active account", false)
                    else {
                        val l = repo.api.myStarred(me, 20, 1)
                        CommandLine(line, l.joinToString("\n") { "★ ${it.fullName}" }, true)
                    }
                }
                "notifications" -> {
                    val n = repo.notifications()
                    if (n.isEmpty()) CommandLine(line, "(no notifications)", true)
                    else CommandLine(line, n.joinToString("\n") { "${if (it.unread) "•" else " "} ${it.repository.fullName}: ${it.subject.title}" }, true)
                }
                "clear" -> { _state.value = _state.value.copy(lines = emptyList()); CommandLine(line, "(cleared)", true) }
                else -> CommandLine(line, "Unknown command. Try 'help' or browse the command catalog above.", false)
            }
        } catch (t: Throwable) { CommandLine(line, "ERR: ${t.message}", false) }
    }

    private suspend fun repoCmd(args: List<String>, line: String): CommandLine {
        if (args.isEmpty()) return CommandLine(line, "Usage: repo <create|delete|rename|star|unstar|fork|info|list|visibility|archive|unarchive|watch|unwatch>", false)
        return when (args[0]) {
            "create" -> {
                val name = args.getOrNull(1) ?: return CommandLine(line, "Name required", false)
                val priv = args.contains("--private")
                val desc = args.firstOrNull { it.startsWith("--desc=") }?.substringAfter("--desc=")
                val r = repo.createRepo(CreateRepoRequest(name = name, private = priv, description = desc))
                CommandLine(line, "Created ${r.fullName}", true)
            }
            "delete" -> { val (o, n) = ownerName(args[1]); repo.deleteRepo(o, n); CommandLine(line, "Deleted $o/$n", true) }
            "rename" -> {
                val (o, n) = ownerName(args[1])
                val newName = args.getOrNull(2) ?: return CommandLine(line, "Need new name", false)
                val r = repo.updateRepo(o, n, com.githubcontrol.data.api.UpdateRepoRequest(name = newName))
                CommandLine(line, "Renamed → ${r.fullName}", true)
            }
            "star" -> { val (o, n) = ownerName(args[1]); repo.star(o, n); CommandLine(line, "Starred", true) }
            "unstar" -> { val (o, n) = ownerName(args[1]); repo.unstar(o, n); CommandLine(line, "Unstarred", true) }
            "watch" -> { val (o, n) = ownerName(args[1]); repo.watch(o, n, true); CommandLine(line, "Watching", true) }
            "unwatch" -> { val (o, n) = ownerName(args[1]); repo.unwatch(o, n); CommandLine(line, "Unwatched", true) }
            "fork" -> { val (o, n) = ownerName(args[1]); val r = repo.forkRepo(o, n); CommandLine(line, "Forked → ${r.fullName}", true) }
            "info" -> {
                val (o, n) = ownerName(args[1])
                val r = repo.repo(o, n)
                CommandLine(line, "${r.fullName}\n  ${r.description ?: ""}\n  ★ ${r.stars}  ⑂ ${r.forks}  ${if (r.private) "private" else "public"}", true)
            }
            "list" -> {
                val l = repo.listMyRepos(1, 20, "updated", "desc", null)
                CommandLine(line, l.joinToString("\n") { "${it.fullName}  ★${it.stars}" }, true)
            }
            "visibility" -> {
                val (o, n) = ownerName(args[1])
                val vis = args.getOrNull(2) ?: return CommandLine(line, "public|private required", false)
                repo.updateRepo(o, n, com.githubcontrol.data.api.UpdateRepoRequest(private = (vis == "private")))
                CommandLine(line, "Visibility set to $vis", true)
            }
            "archive" -> {
                val (o, n) = ownerName(args[1])
                repo.updateRepo(o, n, com.githubcontrol.data.api.UpdateRepoRequest(archived = true))
                CommandLine(line, "Archived $o/$n", true)
            }
            "unarchive" -> {
                val (o, n) = ownerName(args[1])
                repo.updateRepo(o, n, com.githubcontrol.data.api.UpdateRepoRequest(archived = false))
                CommandLine(line, "Unarchived $o/$n", true)
            }
            else -> CommandLine(line, "Unknown repo subcmd", false)
        }
    }

    private suspend fun branchCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: branch <list|create|delete|default> <owner>/<name> ...", false)
        val (o, n) = ownerName(args[1])
        return when (args[0]) {
            "list" -> CommandLine(line, repo.branches(o, n).joinToString("\n") { "${it.name} @ ${it.commit.sha.take(7)}" }, true)
            "create" -> {
                val newName = args.getOrNull(2) ?: return CommandLine(line, "Need new branch name", false)
                val from = args.getOrNull(4) ?: "main"
                repo.createBranch(o, n, newName, from); CommandLine(line, "Created $newName from $from", true)
            }
            "delete" -> {
                val br = args.getOrNull(2) ?: return CommandLine(line, "Need branch", false)
                repo.deleteBranch(o, n, br); CommandLine(line, "Deleted $br", true)
            }
            "default" -> {
                val br = args.getOrNull(2) ?: return CommandLine(line, "Need branch", false)
                repo.updateRepo(o, n, com.githubcontrol.data.api.UpdateRepoRequest(defaultBranch = br))
                CommandLine(line, "Default branch → $br", true)
            }
            else -> CommandLine(line, "Unknown branch subcmd", false)
        }
    }

    private suspend fun issueCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: issue <create|close|reopen|list|comment> <owner>/<name> ...", false)
        val (o, n) = ownerName(args[1])
        return when (args[0]) {
            "create" -> {
                val title = args.getOrNull(2) ?: return CommandLine(line, "title required", false)
                val body = args.getOrNull(3)
                val i = repo.createIssue(o, n, CreateIssueRequest(title = title, body = body))
                CommandLine(line, "#${i.number} ${i.title}", true)
            }
            "close" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "issue # required", false)
                repo.updateIssue(o, n, number, com.githubcontrol.data.api.UpdateIssueRequest(state = "closed"))
                CommandLine(line, "Closed #$number", true)
            }
            "reopen" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "issue # required", false)
                repo.updateIssue(o, n, number, com.githubcontrol.data.api.UpdateIssueRequest(state = "open"))
                CommandLine(line, "Reopened #$number", true)
            }
            "list" -> {
                val l = repo.issues(o, n, "open", 1).take(20)
                CommandLine(line, l.joinToString("\n") { "#${it.number} ${it.title}" }, true)
            }
            "comment" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "issue # required", false)
                val body = args.getOrNull(3) ?: return CommandLine(line, "body required", false)
                repo.api.addIssueComment(o, n, number, com.githubcontrol.data.api.CreateCommentRequest(body))
                CommandLine(line, "Commented on #$number", true)
            }
            else -> CommandLine(line, "Unknown issue subcmd", false)
        }
    }

    private suspend fun prCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: pr <list|merge|close|info> <owner>/<name> ...", false)
        val (o, n) = ownerName(args[1])
        return when (args[0]) {
            "list" -> {
                val l = repo.pulls(o, n, "open", 1).take(20)
                CommandLine(line, l.joinToString("\n") { "#${it.number} ${it.title} (${it.head.ref} → ${it.base.ref})" }, true)
            }
            "merge" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "PR # required", false)
                val resp = repo.mergePull(o, n, number, com.githubcontrol.data.api.MergePRRequest())
                CommandLine(line, if (resp.isSuccessful) "Merged #$number" else "Merge failed: HTTP ${resp.code()}", resp.isSuccessful)
            }
            "close" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "PR # required", false)
                repo.updatePull(o, n, number, com.githubcontrol.data.api.UpdatePRRequest(state = "closed"))
                CommandLine(line, "Closed #$number", true)
            }
            "info" -> {
                val number = args.getOrNull(2)?.toIntOrNull() ?: return CommandLine(line, "PR # required", false)
                val p = repo.pull(o, n, number)
                CommandLine(line, "#${p.number} ${p.title}\nstate=${p.state}  base=${p.base.ref}  head=${p.head.ref}\nby ${p.user?.login}", true)
            }
            else -> CommandLine(line, "Unknown pr subcmd", false)
        }
    }

    private suspend fun releaseCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: release <list|latest> <owner>/<name>", false)
        val (o, n) = ownerName(args[1])
        return when (args[0]) {
            "list" -> {
                val l = repo.api.releases(o, n).take(10)
                if (l.isEmpty()) CommandLine(line, "(no releases)", true)
                else CommandLine(line, l.joinToString("\n") { "${it.tagName} — ${it.name ?: ""}" }, true)
            }
            "latest" -> {
                val r = repo.api.releases(o, n).firstOrNull()
                if (r == null) CommandLine(line, "(no releases)", false)
                else CommandLine(line, "${r.tagName} — ${r.name ?: ""}\n${r.body?.take(300) ?: ""}", true)
            }
            else -> CommandLine(line, "Unknown release subcmd", false)
        }
    }

    private suspend fun commitsCmd(args: List<String>, line: String): CommandLine {
        if (args.isEmpty()) return CommandLine(line, "Usage: commits <owner>/<name> [branch]", false)
        val (o, n) = ownerName(args[0])
        val br = args.getOrNull(1)
        val l = repo.commits(o, n, br, 1, 20)
        return CommandLine(line, l.joinToString("\n") { "${it.sha.take(7)}  ${it.commit.message.lineSequence().firstOrNull().orEmpty()}" }, true)
    }

    private suspend fun treeCmd(args: List<String>, line: String): CommandLine {
        if (args.isEmpty()) return CommandLine(line, "Usage: tree <owner>/<name> [branch]", false)
        val (o, n) = ownerName(args[0])
        val br = args.getOrNull(1) ?: "HEAD"
        val branchInfo = repo.api.branch(o, n, br)
        val parent = repo.api.commitDetail(o, n, branchInfo.commit.sha)
        val ft = repo.api.gitTree(o, n, parent.commit.tree.sha, recursive = 1)
        val out = ft.tree.take(80).joinToString("\n") { "${it.type[0]}  ${it.path}" }
        return CommandLine(line, out + (if (ft.tree.size > 80) "\n…(${ft.tree.size - 80} more)" else ""), true)
    }

    private suspend fun fileCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: file <view|delete> <owner>/<name>:<path> [ref]", false)
        val target = args[1]
        val parts = target.split(":", limit = 2)
        val (o, n) = ownerName(parts[0])
        val path = parts.getOrNull(1) ?: return CommandLine(line, "path required (use owner/name:path)", false)
        val ref = args.getOrNull(2)
        return when (args[0]) {
            "view" -> {
                val f = repo.fileContent(o, n, path, ref)
                val text = f.content?.fromBase64()?.toString(Charsets.UTF_8) ?: ""
                CommandLine(line, text.take(2000) + (if (text.length > 2000) "\n…(truncated)" else ""), true)
            }
            "delete" -> {
                val f = repo.fileContent(o, n, path, ref)
                repo.api.deleteFile(o, n, path, com.githubcontrol.data.api.DeleteFileRequest("Delete $path", f.sha, ref))
                CommandLine(line, "Deleted $path", true)
            }
            else -> CommandLine(line, "Unknown file subcmd", false)
        }
    }

    private suspend fun userCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: user <info|repos> <login>", false)
        val login = args[1]
        return when (args[0]) {
            "info" -> {
                val u = repo.user(login)
                CommandLine(line, "${u.login} (${u.name ?: ""})\n${u.bio ?: ""}\nrepos=${u.publicRepos} followers=${u.followers} following=${u.following}", true)
            }
            "repos" -> {
                val l = repo.userRepos(login, 1, 20)
                CommandLine(line, l.joinToString("\n") { "${it.fullName} ★${it.stars}" }, true)
            }
            else -> CommandLine(line, "Unknown user subcmd", false)
        }
    }

    private suspend fun searchCmd(args: List<String>, line: String): CommandLine {
        if (args.size < 2) return CommandLine(line, "Usage: search <repos|code|users> <query>", false)
        val q = args.drop(1).joinToString(" ")
        return when (args[0]) {
            "repos" -> CommandLine(line, repo.searchRepos(q).items.take(10).joinToString("\n") { "${it.fullName} ★${it.stars}" }, true)
            "code" -> CommandLine(line, repo.searchCode(q).items.take(10).joinToString("\n") { "${it.repository.fullName}: ${it.path}" }, true)
            "users" -> CommandLine(line, repo.searchUsers(q).items.take(10).joinToString("\n") { it.login }, true)
            else -> CommandLine(line, "Unknown search type", false)
        }
    }

    private fun ownerName(s: String): Pair<String, String> {
        val parts = s.split("/")
        return parts[0] to parts.getOrElse(1) { "" }
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQ = false
        for (c in s) {
            when {
                c == '"' -> inQ = !inQ
                c.isWhitespace() && !inQ -> { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
                else -> cur.append(c)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun helpText(): String =
        catalog.joinToString("\n") { "${it.template.padEnd(50)}  — ${it.description}" }

    companion object {
        /**
         * Catalog of every command the in-app shell understands. Surfaced as a
         * scrollable list above the terminal in [com.githubcontrol.ui.screens.command.CommandScreen]
         * with copy + info buttons per row.
         */
        val catalog: List<CommandSpec> = listOf(
            // --- Session ---
            CommandSpec("help", "help", "Session", "List every command with a short description.", "help"),
            CommandSpec("clear", "clear", "Session", "Clear the terminal output.", "clear"),
            CommandSpec("whoami", "whoami", "Session", "Show the active account login + token scopes.", "whoami"),
            CommandSpec("accounts", "accounts", "Session", "List every signed-in account (active marked with *).", "accounts"),
            CommandSpec("me", "me", "Session", "Profile of the active account (login, name, repo count).", "me"),
            CommandSpec("rate", "rate", "Session", "Show core API rate-limit remaining / total / reset epoch.", "rate"),
            CommandSpec("notifications", "notifications", "Session", "List unread + recent notifications across repos.", "notifications"),

            // --- Repos ---
            CommandSpec("repo list", "repo list", "Repos", "List your most recently updated repositories.", "repo list"),
            CommandSpec("repo info", "repo info <owner>/<name>", "Repos", "Show description, stars, forks and visibility.", "repo info torvalds/linux"),
            CommandSpec("repo create", "repo create <name> [--private] [--desc=...]", "Repos", "Create a new repository on the active account.", "repo create demo --private --desc=\"Test repo\""),
            CommandSpec("repo delete", "repo delete <owner>/<name>", "Repos", "Permanently delete a repository (needs delete_repo scope).", "repo delete me/demo"),
            CommandSpec("repo rename", "repo rename <owner>/<name> <newName>", "Repos", "Rename a repository.", "repo rename me/old me-renamed"),
            CommandSpec("repo star", "repo star <owner>/<name>", "Repos", "Star a repository.", "repo star vercel/next.js"),
            CommandSpec("repo unstar", "repo unstar <owner>/<name>", "Repos", "Remove your star from a repository.", "repo unstar vercel/next.js"),
            CommandSpec("repo fork", "repo fork <owner>/<name>", "Repos", "Fork a repository to your account.", "repo fork torvalds/linux"),
            CommandSpec("repo visibility", "repo visibility <owner>/<name> public|private", "Repos", "Switch repo visibility.", "repo visibility me/demo private"),
            CommandSpec("repo archive", "repo archive <owner>/<name>", "Repos", "Archive a repository (read-only).", "repo archive me/demo"),
            CommandSpec("repo unarchive", "repo unarchive <owner>/<name>", "Repos", "Unarchive a repository.", "repo unarchive me/demo"),
            CommandSpec("repo watch", "repo watch <owner>/<name>", "Repos", "Subscribe to all notifications for a repo.", "repo watch vercel/next.js"),
            CommandSpec("repo unwatch", "repo unwatch <owner>/<name>", "Repos", "Stop watching a repo.", "repo unwatch vercel/next.js"),

            // --- Branches ---
            CommandSpec("branch list", "branch list <owner>/<name>", "Branches", "List branches with their tip SHAs.", "branch list me/demo"),
            CommandSpec("branch create", "branch create <owner>/<name> <new> from <base>", "Branches", "Create a new branch from a base branch.", "branch create me/demo feat/x from main"),
            CommandSpec("branch delete", "branch delete <owner>/<name> <branch>", "Branches", "Delete a branch.", "branch delete me/demo feat/x"),
            CommandSpec("branch default", "branch default <owner>/<name> <branch>", "Branches", "Set the repository's default branch.", "branch default me/demo main"),

            // --- Commits / tree ---
            CommandSpec("commits", "commits <owner>/<name> [branch]", "History", "List the 20 latest commits on a branch.", "commits me/demo main"),
            CommandSpec("tree", "tree <owner>/<name> [branch]", "History", "Print the recursive tree of a branch.", "tree me/demo main"),

            // --- Files ---
            CommandSpec("file view", "file view <owner>/<name>:<path> [ref]", "Files", "Print a file's contents (truncated to 2 KB).", "file view me/demo:README.md"),
            CommandSpec("file delete", "file delete <owner>/<name>:<path> [ref]", "Files", "Delete a single file via the contents API.", "file delete me/demo:tmp.txt"),

            // --- Issues ---
            CommandSpec("issue list", "issue list <owner>/<name>", "Issues", "List open issues (latest 20).", "issue list me/demo"),
            CommandSpec("issue create", "issue create <owner>/<name> \"title\" \"body\"", "Issues", "Open a new issue.", "issue create me/demo \"Bug\" \"steps to repro\""),
            CommandSpec("issue close", "issue close <owner>/<name> <number>", "Issues", "Close an issue.", "issue close me/demo 12"),
            CommandSpec("issue reopen", "issue reopen <owner>/<name> <number>", "Issues", "Reopen a closed issue.", "issue reopen me/demo 12"),
            CommandSpec("issue comment", "issue comment <owner>/<name> <number> \"text\"", "Issues", "Post a comment on an issue.", "issue comment me/demo 12 \"thanks!\""),

            // --- Pull requests ---
            CommandSpec("pr list", "pr list <owner>/<name>", "Pull Requests", "List open pull requests.", "pr list me/demo"),
            CommandSpec("pr info", "pr info <owner>/<name> <number>", "Pull Requests", "Show base/head branches and metadata.", "pr info me/demo 5"),
            CommandSpec("pr merge", "pr merge <owner>/<name> <number>", "Pull Requests", "Merge a PR with the default merge method.", "pr merge me/demo 5"),
            CommandSpec("pr close", "pr close <owner>/<name> <number>", "Pull Requests", "Close a PR without merging.", "pr close me/demo 5"),

            // --- Releases ---
            CommandSpec("release list", "release list <owner>/<name>", "Releases", "List the 10 latest releases.", "release list me/demo"),
            CommandSpec("release latest", "release latest <owner>/<name>", "Releases", "Show the latest published release.", "release latest me/demo"),

            // --- Users ---
            CommandSpec("user info", "user info <login>", "Users", "Show another user's profile.", "user info torvalds"),
            CommandSpec("user repos", "user repos <login>", "Users", "List a user's recent repos.", "user repos torvalds"),
            CommandSpec("starred", "starred", "Users", "List the 20 latest repos you've starred.", "starred"),

            // --- Search ---
            CommandSpec("search repos", "search repos <query>", "Search", "Search public repositories.", "search repos jetpack compose"),
            CommandSpec("search code", "search code <query>", "Search", "Search code globally (needs scope).", "search code addInterceptor language:kotlin"),
            CommandSpec("search users", "search users <query>", "Search", "Search GitHub users.", "search users location:india"),
        )
    }
}
