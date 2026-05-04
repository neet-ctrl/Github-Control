package com.githubcontrol.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.githubcontrol.ui.navigation.Routes
import com.githubcontrol.ui.screens.accounts.AccountsScreen
import com.githubcontrol.ui.screens.actions.ActionsScreen
import com.githubcontrol.ui.screens.analytics.AnalyticsScreen
import com.githubcontrol.ui.screens.auth.BiometricScreen
import com.githubcontrol.ui.screens.auth.LoginScreen
import com.githubcontrol.ui.screens.branches.BranchProtectionScreen
import com.githubcontrol.ui.screens.branches.BranchesScreen
import com.githubcontrol.ui.screens.collab.CollaboratorsScreen
import com.githubcontrol.ui.screens.compare.CompareScreen
import com.githubcontrol.ui.screens.keys.SshKeysScreen
import com.githubcontrol.ui.screens.logs.LogScreen
import com.githubcontrol.ui.screens.profile.ProfileEditScreen
import com.githubcontrol.ui.screens.command.CommandScreen
import com.githubcontrol.ui.screens.commits.CommitDetailScreen
import com.githubcontrol.ui.screens.commits.CommitsScreen
import com.githubcontrol.ui.screens.downloads.DownloadsScreen
import com.githubcontrol.ui.screens.files.FilePreviewScreen
import com.githubcontrol.ui.screens.files.FilesScreen
import com.githubcontrol.ui.screens.files.TreeScreen
import com.githubcontrol.ui.screens.home.DashboardScreen
import com.githubcontrol.ui.screens.issues.CreateIssueScreen
import com.githubcontrol.ui.screens.issues.IssueDetailScreen
import com.githubcontrol.ui.screens.issues.IssuesScreen
import com.githubcontrol.ui.screens.notifications.NotificationsScreen
import com.githubcontrol.ui.screens.onboarding.OnboardingScreen
import com.githubcontrol.ui.screens.plugins.PluginsScreen
import com.githubcontrol.ui.screens.pulls.CreatePullScreen
import com.githubcontrol.ui.screens.pulls.PullDetailScreen
import com.githubcontrol.ui.screens.pulls.PullsScreen
import com.githubcontrol.ui.screens.releases.ReleasesScreen
import com.githubcontrol.ui.screens.repos.CreateRepoScreen
import com.githubcontrol.ui.screens.repos.RepoDetailScreen
import com.githubcontrol.ui.screens.repos.RepoListScreen
import com.githubcontrol.ui.screens.search.SearchScreen
import com.githubcontrol.ui.screens.settings.SettingsScreen
import com.githubcontrol.ui.screens.sync.SyncScreen
import com.githubcontrol.ui.theme.GitHubControlTheme
import com.githubcontrol.ui.theme.ThemeSettings
import com.githubcontrol.viewmodel.MainViewModel

@Composable
fun AppRoot() {
    val main: MainViewModel = hiltViewModel()
    val state by main.state.collectAsState()
    val am = main.accountManager
    val theme by am.themeFlow.collectAsState(initial = "system")
    val accent by am.accentColorFlow.collectAsState(initial = "blue")
    val dynamic by am.dynamicColorFlow.collectAsState(initial = false)
    val amoled by am.amoledFlow.collectAsState(initial = false)
    val fontScale by am.fontScaleFlow.collectAsState(initial = 1.0f)
    val monoScale by am.monoFontScaleFlow.collectAsState(initial = 1.0f)
    val density by am.densityFlow.collectAsState(initial = "comfortable")
    val corner by am.cornerRadiusFlow.collectAsState(initial = 14)
    val terminal by am.terminalThemeFlow.collectAsState(initial = "github-dark")
    val onboardingDone by am.onboardingCompletedFlow.collectAsState(initial = true)
    val lastRoute by am.lastRouteFlow.collectAsState(initial = null)
    val biometricEnabled by am.biometricEnabledFlow.collectAsState(initial = false)

    val settings = ThemeSettings(
        mode = theme, accentKey = accent, dynamicColor = dynamic, amoled = amoled,
        fontScale = fontScale, monoFontScale = monoScale, density = density,
        cornerRadius = corner, terminalTheme = terminal
    )

    GitHubControlTheme(settings = settings) {
        val nav = rememberNavController()

        LaunchedEffect(state.loggedIn, state.locked, onboardingDone, biometricEnabled) {
            // The "add another account" flow intentionally skips this auto-routing —
            // it lives entirely in the existing back stack so that the back button
            // returns the user to AccountsScreen / Dashboard after sign-in completes.
            val target = when {
                !onboardingDone -> Routes.ONBOARDING
                !state.loggedIn -> Routes.LOGIN
                state.locked && biometricEnabled -> Routes.BIOMETRIC
                else -> lastRoute ?: Routes.DASHBOARD
            }
            val current = nav.currentDestination?.route
            if (current != target && current?.substringBefore('/') != target.substringBefore('/')) {
                if (target != Routes.DASHBOARD &&
                    target !in setOf(Routes.LOGIN, Routes.BIOMETRIC, Routes.ONBOARDING)
                ) {
                    // Restoring a deep route on cold-start: anchor the back stack
                    // to Dashboard first so the system back button always works
                    // (and so 404s on a stale lastRoute don't trap the user).
                    nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    nav.navigate(target)
                } else {
                    nav.navigate(target) { popUpTo(0) { inclusive = true } }
                }
            }
        }

        // When the active account changes, every screen's data was loaded under
        // the previous token. Reset the back stack to a fresh Dashboard so all
        // ViewModels are recreated and reload using the new account.
        LaunchedEffect(nav) {
            main.accountSwitched.collect {
                nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
            }
        }

        LaunchedEffect(nav) {
            nav.currentBackStackEntryFlow.collect { entry ->
                val route = entry.destination.route ?: return@collect
                if (route !in setOf(Routes.LOGIN, Routes.BIOMETRIC, Routes.ONBOARDING)) {
                    am.setLastRoute(route)
                }
            }
        }

        val startDest = if (!onboardingDone) Routes.ONBOARDING else Routes.LOGIN
        NavHost(navController = nav, startDestination = startDest) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinish = {
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                })
            }
            composable(Routes.LOGIN) {
                LoginScreen(main) {
                    val wasAdding = main.addingAccount.value
                    main.endAddAccount()
                    if (wasAdding && nav.previousBackStackEntry != null) {
                        // Came from AccountsScreen / Dashboard — pop back to it
                        // so the user lands on the screen they invoked Add from
                        // and the back button continues to work normally.
                        nav.popBackStack()
                    } else {
                        nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
                    }
                }
            }
            composable(Routes.BIOMETRIC) {
                BiometricScreen(main) { nav.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } } }
            }
            composable(Routes.DASHBOARD) {
                DashboardScreen(main, onNavigate = { nav.navigate(it) })
            }
            composable(Routes.REPOS) {
                RepoListScreen(onBack = { nav.popBackStack() }, onOpen = { o, n -> nav.navigate(Routes.repoDetail(o, n)) }, onCreate = { nav.navigate(Routes.CREATE_REPO) })
            }
            composable(Routes.CREATE_REPO) {
                CreateRepoScreen(onBack = { nav.popBackStack() }, onCreated = { o, n -> nav.popBackStack(); nav.navigate(Routes.repoDetail(o, n)) })
            }
            composable(
                Routes.REPO_DETAIL,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                RepoDetailScreen(owner, name, main, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
            }
            composable(
                Routes.FILES,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                FilesScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    java.net.URLDecoder.decode(back.arguments?.getString("path") ?: "", "UTF-8"),
                    java.net.URLDecoder.decode(back.arguments?.getString("ref") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }
                )
            }
            composable(
                Routes.TREE,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                TreeScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    java.net.URLDecoder.decode(back.arguments?.getString("ref") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }, onPreview = { nav.navigate(it) }
                )
            }
            composable(
                Routes.FILE_PREVIEW,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                FilePreviewScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    java.net.URLDecoder.decode(back.arguments?.getString("path") ?: "", "UTF-8"),
                    java.net.URLDecoder.decode(back.arguments?.getString("ref") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.UPLOAD,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                com.githubcontrol.ui.screens.upload.UploadScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    java.net.URLDecoder.decode(back.arguments?.getString("path") ?: "", "UTF-8"),
                    java.net.URLDecoder.decode(back.arguments?.getString("ref") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.COMMITS,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("branch") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                val branch = java.net.URLDecoder.decode(back.arguments?.getString("branch") ?: "", "UTF-8")
                CommitsScreen(owner, name, branch, onBack = { nav.popBackStack() }, onOpenCommit = { sha -> nav.navigate(Routes.commitDetail(owner, name, sha)) })
            }
            composable(
                Routes.COMMIT_DETAIL,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }, navArgument("sha") { type = NavType.StringType })
            ) { back ->
                CommitDetailScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    back.arguments?.getString("sha") ?: "",
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.PRS,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                PullsScreen(owner, name, onBack = { nav.popBackStack() }, onOpen = { num -> nav.navigate(Routes.pull(owner, name, num)) }, onCreate = { nav.navigate(Routes.createPr(owner, name)) })
            }
            composable(
                Routes.PR_DETAIL,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }, navArgument("number") { type = NavType.IntType })
            ) { back ->
                PullDetailScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    back.arguments?.getInt("number") ?: 0,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.CREATE_PR,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                CreatePullScreen(owner, name, onBack = { nav.popBackStack() }, onCreated = { num -> nav.popBackStack(); nav.navigate(Routes.pull(owner, name, num)) })
            }
            composable(
                Routes.ISSUES,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                IssuesScreen(owner, name, onBack = { nav.popBackStack() }, onOpen = { num -> nav.navigate(Routes.issue(owner, name, num)) }, onCreate = { nav.navigate("create_issue/$owner/$name") })
            }
            composable(
                "create_issue/{owner}/{name}",
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                val owner = back.arguments?.getString("owner") ?: ""
                val name = back.arguments?.getString("name") ?: ""
                CreateIssueScreen(owner, name, onBack = { nav.popBackStack() }, onCreated = { num -> nav.popBackStack(); nav.navigate(Routes.issue(owner, name, num)) })
            }
            composable(
                Routes.ISSUE_DETAIL,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }, navArgument("number") { type = NavType.IntType })
            ) { back ->
                IssueDetailScreen(
                    back.arguments?.getString("owner") ?: "",
                    back.arguments?.getString("name") ?: "",
                    back.arguments?.getInt("number") ?: 0,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.ACTIONS,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                ActionsScreen(back.arguments?.getString("owner") ?: "", back.arguments?.getString("name") ?: "", onBack = { nav.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) })
            }
            composable(
                Routes.ANALYTICS,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                AnalyticsScreen(back.arguments?.getString("owner") ?: "", back.arguments?.getString("name") ?: "", onBack = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNTS) {
                AccountsScreen(main, onBack = { nav.popBackStack() }, onAdd = {
                    // Keep AccountsScreen on the back stack so the back button on the
                    // login screen / after sign-in returns the user where they were.
                    main.beginAddAccount()
                    nav.navigate(Routes.LOGIN)
                })
            }
            composable(Routes.COMMAND) { CommandScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { SettingsScreen(main, onBack = { nav.popBackStack() }, onNavigate = { nav.navigate(it) }) }
            composable(Routes.SYNC) { SyncScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PLUGINS) { PluginsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DOWNLOADS) { com.githubcontrol.ui.screens.downloads.DownloadsScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.BRANCHES,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                BranchesScreen(back.arguments?.getString("owner") ?: "", back.arguments?.getString("name") ?: "", onBack = { nav.popBackStack() })
            }
            composable(
                Routes.README,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("ref") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                com.githubcontrol.ui.screens.repos.ReadmeScreen(
                    owner = back.arguments?.getString("owner") ?: "",
                    name = back.arguments?.getString("name") ?: "",
                    ref = back.arguments?.getString("ref")?.takeIf { it.isNotBlank() },
                    onBack = { nav.popBackStack() }
                )
            }
            // ---- New routes ----
            composable(Routes.LOGS) { LogScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PROFILE_EDIT) { ProfileEditScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SSH_KEYS) { SshKeysScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.BRANCH_PROTECTION,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("branch") { type = NavType.StringType }
                )
            ) { back ->
                BranchProtectionScreen(
                    owner = back.arguments?.getString("owner") ?: "",
                    name = back.arguments?.getString("name") ?: "",
                    branch = java.net.URLDecoder.decode(back.arguments?.getString("branch") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.COMPARE,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType },
                    navArgument("base") { type = NavType.StringType; defaultValue = "" },
                    navArgument("head") { type = NavType.StringType; defaultValue = "" }
                )
            ) { back ->
                CompareScreen(
                    owner = back.arguments?.getString("owner") ?: "",
                    name = back.arguments?.getString("name") ?: "",
                    base = java.net.URLDecoder.decode(back.arguments?.getString("base") ?: "", "UTF-8"),
                    head = java.net.URLDecoder.decode(back.arguments?.getString("head") ?: "", "UTF-8"),
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.COLLABORATORS,
                arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType })
            ) { back ->
                CollaboratorsScreen(
                    owner = back.arguments?.getString("owner") ?: "",
                    name = back.arguments?.getString("name") ?: "",
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.PERMISSIONS) {
                com.githubcontrol.ui.screens.settings.PermissionsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CRASHES) {
                com.githubcontrol.ui.screens.settings.CrashLogScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.APPEARANCE) {
                com.githubcontrol.ui.screens.settings.AppearanceScreen(main = main, onBack = { nav.popBackStack() })
            }
            composable(Routes.ABOUT) {
                com.githubcontrol.ui.screens.about.AboutScreen(
                    onBack = { nav.popBackStack() },
                    onCheckUpdates = { /* AboutScreen handles its own check */ },
                    onClearCache = { com.githubcontrol.utils.Logger.clear() }
                )
            }
            composable(
                Routes.USER_PROFILE,
                arguments = listOf(navArgument("login") { type = NavType.StringType })
            ) { back ->
                com.githubcontrol.ui.screens.profile.UserProfileScreen(
                    login = back.arguments?.getString("login") ?: "",
                    onBack = { nav.popBackStack() },
                    onOpenRepo = { o, n -> nav.navigate(Routes.repoDetail(o, n)) }
                )
            }
            composable(Routes.HEALTH) {
                val um = androidx.hilt.navigation.compose.hiltViewModel<com.githubcontrol.viewmodel.UploadManagerHolder>().uploadManager
                com.githubcontrol.ui.screens.health.HealthScreen(
                    main = main,
                    uploadManager = um,
                    onBack = { nav.popBackStack() }
                )
            }
            composable(
                Routes.RELEASES,
                arguments = listOf(
                    navArgument("owner") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType }
                )
            ) { back ->
                ReleasesScreen(
                    owner = back.arguments?.getString("owner") ?: "",
                    name = back.arguments?.getString("name") ?: "",
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
