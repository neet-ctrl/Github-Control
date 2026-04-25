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

## Building the APK
The Replit container can NOT compile Android. Use the GitHub Actions workflow
(`.github/workflows/android.yml`) which runs `gradlew assembleDebug` on Ubuntu
with JDK 17 + the Android SDK. The signed/unsigned APK is uploaded as a workflow
artifact.

## Status webserver
`tools/server.py` (auto-started by the **Status** workflow on port 5000) renders an
HTML index of the source tree so the Replit preview pane is non-empty. It is for
human navigation only and does not interact with the app.
