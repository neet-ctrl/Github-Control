# GitHub Control · Android (com.githubcontrol)

A native Android client for GitHub written in Kotlin with Jetpack Compose + Material 3.
This Replit workspace **does not build APKs** — Android tooling and the Java/Kotlin
toolchain required for AGP (Android Gradle Plugin) are not provisioned here. Builds
are produced by GitHub Actions; Replit only hosts the source and a small status
webserver on port 5000 (`tools/server.py`) so the workspace preview shows the
project structure.

## Stack
- Kotlin 2.0.10, AGP 8.5.2, compileSdk 35, minSdk 26
- Jetpack Compose BOM 2024.10, Material 3
- Hilt 2.52 (DI), Room 2.6.1, WorkManager, EncryptedSharedPreferences, DataStore
- Retrofit + OkHttp + kotlinx.serialization for the GitHub REST API
- JGit for the optional in-app Git mode
- Coil for images, Markwon for Markdown

## Module layout (Kotlin)
```
com.githubcontrol
├─ App.kt, MainActivity.kt
├─ data/
│  ├─ api/         Retrofit GitHubApi, RetrofitClient, model classes
│  ├─ auth/        Account, AccountManager, TokenValidator, ScopeCatalog
│  ├─ db/          Room DAOs + AppDatabase
│  └─ repository/  GitHubRepository (high-level facade)
├─ ui/
│  ├─ AppRoot.kt   Compose nav graph
│  ├─ navigation/  Routes + helpers
│  ├─ components/  GhCard, GhBadge, EmbeddedTerminal, ConflictDialog, …
│  └─ screens/     One folder per feature surface
├─ viewmodel/      Hilt-injected ViewModels
├─ upload/         UploadManager (resumable, parallel, dry-run)
├─ worker/         SyncWorker, UploadWorker (WorkManager)
├─ notifications/  Notifier + channels
└─ utils/          Logger (ring buffer), ShareUtils, Diff, RelativeTime, …
```

## Cross-cutting features
- **Logger ring buffer** (`utils/Logger.kt`) feeds every `EmbeddedTerminal` and the
  full `LogScreen`. Sensitive values (`token=`, `Authorization`, `Bearer`) are
  redacted before storage. Capacity is 1500 entries.
- **CrashHandler** (`utils/CrashHandler.kt`) is installed in `App.onCreate` and
  catches every uncaught exception (any thread). Reports include device info,
  full cause chain, and the last 200 log entries; they are written to
  `filesDir/crashes/*.txt` and kept permanently until the user deletes them from
  the **Crash reports** screen (which has copy / share / delete-all actions).
- **Permissions hub** (`ui/screens/settings/PermissionsScreen.kt`, backed by
  `utils/PermissionsCatalog.kt`) lists every permission the app can use, shows
  current status, and lets the user grant any single permission with one tap or
  ask for all of them at once. Special permissions (battery optimisation, "all
  files access") deep-link to the right system settings page.
- **Token validation** (`data/auth/TokenValidator.kt`) records HTTP code, scopes,
  rate-limit, token type/expiry into `TokenValidation` snapshots that are stored on
  the `Account` (last 20 retained) and rendered in `AccountsScreen` + `LoginScreen`.
- **ScopeCatalog** describes every OAuth scope with a risk level for color-coded
  rendering and recommends a default set (`repo`, `read:user`, `read:org`,
  `notifications`, `workflow`).
- **EmbeddedTerminal** is mounted on Login, Tree, Upload, Sync, Command, Profile,
  SshKeys, BranchProtection, Compare, Collaborators, Permissions, and is reachable
  globally via Settings → Tools → Terminal log.
- **Universal file viewer** (`FilePreviewScreen` + `PreviewViewModel`) recognises
  text, source code, Markdown, images, SVG, HTML (WebView), PDF (native
  PdfRenderer, multi-page), audio & video (MediaPlayer/VideoView), zip archives
  (entry list), and unknown binaries (hex dump). Every preview has copy /
  download-to-device (SAF) / open-with-system-app actions, and large blobs fall
  through to the GitHub raw URL via `rawDownload`.

## Notable screens added in this iteration
| Route                         | Screen                       |
|-------------------------------|------------------------------|
| `Routes.LOGS`                 | `LogScreen`                  |
| `Routes.PROFILE_EDIT`         | `ProfileEditScreen`          |
| `Routes.SSH_KEYS`             | `SshKeysScreen`              |
| `Routes.BRANCH_PROTECTION`    | `BranchProtectionScreen`     |
| `Routes.COMPARE`              | `CompareScreen`              |
| `Routes.COLLABORATORS`        | `CollaboratorsScreen`        |

These are linked from `SettingsScreen.Tools/Account` and from the `RepoDetailScreen`
"Administration" card. The TreeScreen now supports multi-select, bulk delete,
and ZIP-folder download via the Storage Access Framework.

## Recent bug-fix iteration
- **Compose imbalance crashes** — removed every `return@Scaffold` / `return@Column`
  inside a Compose lambda across IssuesScreen, NotificationsScreen, PullDetailScreen,
  PullsScreen, RepoDetailScreen, RepoListScreen, DownloadsScreen, DashboardScreen
  (early-return now uses `if/else` so the slot-table balance is preserved).
- **Last-route restore** — `AccountManager` persists `lastRoute` to DataStore;
  `AppRoot` collects via `currentBackStackEntryFlow` and warps to the last screen
  on cold start.
- **Add-account no-biometric loop** — `MainViewModel.addingAccount` flag (set by
  `AccountsScreen.onAdd`, cleared by `LoginScreen.onDone`) routes to LOGIN while
  set so the biometric prompt does not fire mid add-account flow.
- **Branch rename / delete UX** — `BranchesViewModel` now exposes friendly text
  ("Renaming…", "Couldn't rename: target name may already exist…") instead of
  raw HTTP errors; per-action `renaming` / `deleting` state is exposed for spinners.
- **REPLACE_FOLDER upload mode** — `UploadManager.runReplaceFolder` lists existing
  blobs (`gitTree(recursive=1)`), then issues a single atomic `commitFiles` with
  deletes + adds. UI exposes the new mode in the conflict-mode chip row.
- **Large-file upload OOM** — `streamBase64` pipes the URI through a
  `Base64OutputStream` so raw bytes and encoded string are never resident at the
  same time. AndroidManifest enables `largeHeap`.
- **Sync screen** — full setup flow: Info card explainer, "Add sync job" FAB
  launches `OpenDocumentTree` + persists URI permission, dialog captures
  owner/repo/branch/remotePath/interval, toggle + delete wired to the DAO.
- **Add-account back-stack fix** — `AppRoot` no longer treats `addingAccount`
  as a routing trigger (it was tearing down the back stack). `AccountsScreen`
  now navigates to LOGIN with no `popUpTo`, and `LoginScreen.onDone` simply
  pops back when finishing an add-account flow, returning the user to the
  Accounts/Dashboard screen with a working back button.
- **User profile screen** — new `UserProfileScreen` + `UserProfileViewModel`
  reachable from search results: tapping a user surfaces their profile card
  (avatar, bio, follower counts) plus a paginated list of public repos using
  the new `users/{user}/repos` endpoint and `Routes.userProfile(login)`.
- **Branch picker on commits** — `CommitsScreen` exposes a top-bar branch
  dropdown sourced from `repo.branches()`, with the repo's default branch
  flagged. `CommitsViewModel.selectBranch` re-loads the commit list when the
  user picks a different branch.
- **Commit-detail actions** — `CommitDetailScreen` now offers two top
  actions: "Reset <default> to this commit" (force-updates `heads/<default>`
  via `repo.hardResetBranch`) and "New branch" (opens a name dialog, calls
  `repo.createBranchAtSha`). Both surface results through a snackbar and
  guard against repeated taps with `actionInFlight`.
- **Set default branch** — `BranchesScreen` shows a star toggle per row;
  tapping a non-default branch opens a confirmation dialog and calls
  `BranchesViewModel.setDefault`, which patches the repo through
  `UpdateRepoRequest.defaultBranch`. The default branch is also protected
  from accidental deletion.

## Latest iteration (Apr 2026)

- **Codespaces from a commit** — `CommitDetailScreen` now embeds a
  Codespaces card (state badge, machine + git status, ahead/behind) with
  per-row actions: open in browser, copy URL, start, stop, and delete
  (with confirm). Tapping a row opens a details dialog with the full
  metadata payload and a "Copy details" action. The "Create at this
  commit" button opens a dialog whose machine-type dropdown is populated
  by `repos/{owner}/{repo}/codespaces/machines?ref=<sha>`; on confirm it
  calls `createCodespace` with `ref=<sha>` so the env is pinned to that
  commit. A secondary button opens
  `https://github.com/codespaces/new/<owner>/<repo>/tree/<sha>` in the
  browser as a fallback. New plumbing: 7 endpoints in `GitHubApi.kt`,
  6 facade methods in `GitHubRepository.kt`, codespace state +
  `loadCodespaces / loadCodespaceMachines / createCodespaceForCommit /
  start|stop|refresh|deleteCodespace` in `CommitsViewModel.kt`.
- **Account switcher actually switches** — `MainViewModel.switchAccount`
  now emits a `accountSwitched: SharedFlow<String>` after calling
  `accountManager.setActiveAccount(...)` and refreshing dashboard data.
  `AppRoot` collects that flow and `navigate(Dashboard) { popUpTo(0) }`,
  so every screen reloads against the new identity instead of showing the
  previous account's cached view.
- **Cold-start route restore is back-button safe** — when restoring the
  saved last route, `AppRoot` now first navigates to `Dashboard` (anchor)
  and *then* pushes the deep route. Pressing back from a stale or 404'd
  screen always lands on the dashboard instead of dead-ending.
- **`GhBlob` deserialization fix** — `data/api/Models.kt:460` now declares
  `size`, `content`, `encoding` as optional with defaults so the response
  to `gitTree(recursive=1)` no longer trips kotlinx.serialization
  `MissingFieldException` during `REPLACE_FOLDER` runs.
- **Folder delete from Files** — `FilesViewModel.deleteFolder()` and the
  rewritten `deleteSelected()` walk the tree, expand any selected
  directories into their child paths, and issue a single atomic
  `commitFiles` with deletes (Git Data API). `FilesScreen` swipe-deletes
  on a directory open a commit-message dialog (`showFolderDelete` state).
- **Command Mode catalog** — `CommandViewModel.catalog` is the new source
  of truth for the in-app shell, with `~50` `CommandSpec(name, template,
  category, description, example)` rows grouped by category. The shell
  implementation in `execute(...)` and the `helpText()` output both
  derive from this list. `CommandScreen` now renders the catalog above
  the terminal with copy + info buttons per row, plus a search filter.
- **About-screen branding source of truth** — every "who built this" value
  rendered on About lives in `app/src/main/java/com/githubcontrol/utils/BuildInfo.kt`
  (`DEVELOPER_NAME`, `DEVELOPER_EMAIL`, `GITHUB_OWNER`, `GITHUB_REPO`,
  `REPO_URL`, `ISSUES_URL`). Forks change this single file and the About
  screen, update checker and settings backup all pick up the new values.
  See README "Customising the About screen" for the per-line breakdown.

## Building the APK
The Replit container can NOT compile Android. Use the GitHub Actions workflow
(`.github/workflows/android.yml`) which runs `gradlew assembleDebug` on Ubuntu
with JDK 17 + the Android SDK. The signed/unsigned APK is uploaded as a workflow
artifact.

## Status webserver
`tools/server.py` (auto-started by the **Status** workflow on port 5000) renders an
HTML index of the source tree so the Replit preview pane is non-empty. It is for
human navigation only and does not interact with the app.
