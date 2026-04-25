package com.githubcontrol.ui.screens.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.githubcontrol.ui.components.EmbeddedTerminal
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.upload.ConflictMode
import com.githubcontrol.upload.UploadFileState
import com.githubcontrol.utils.ByteFormat
import com.githubcontrol.viewmodel.UploadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(owner: String, name: String, path: String, ref: String, onBack: () -> Unit, vm: UploadViewModel = hiltViewModel()) {
    LaunchedEffect(owner, name, path, ref) { vm.init(owner, name, path, ref) }
    val form by vm.form.collectAsState()
    val progress by vm.progress.collectAsState()

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.setUris(uris) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.setUris(listOf(uri)) }

    var showFolderBrowser by remember { mutableStateOf(false) }
    var showBranchPicker by remember { mutableStateOf(false) }
    // Per-section in-line help, toggled by the Info icon next to each label.
    var helpOpen by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Upload to $name") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GhCard {
                LabelWithHelp(
                    "Destination",
                    helpKey = "destination",
                    helpText = "Where the uploaded files will land in the repo. Tap “Browse” to pick a folder, or type a new one — folders are created on commit if they don't already exist.",
                    helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    form.targetFolder, { vm.setTarget(it) },
                    label = { Text("Folder path inside repo") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = {
                            showFolderBrowser = true
                            vm.browseFolder("")
                        }) { Text("Browse") }
                    }
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    form.branch, { vm.setBranch(it) },
                    label = { Text("Branch") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { showBranchPicker = true }) { Text("Pick") }
                    },
                    supportingText = { Text("${form.branches.size} branches available") }
                )
            }

            GhCard {
                LabelWithHelp(
                    "Pick files or folders",
                    helpKey = "pick",
                    helpText = "Files: choose individual documents from anywhere on the device. Folder: pick an entire directory — its structure (subfolders included) is preserved on the repo.",
                    helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFiles.launch(arrayOf("*/*")) }) { Icon(Icons.Filled.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Files") }
                    OutlinedButton(onClick = { pickFolder.launch(null) }) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Folder") }
                }
                if (form.totalFiles > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhBadge("${form.totalFiles} files")
                        GhBadge(ByteFormat.human(form.totalBytes))
                    }
                }
            }

            GhCard {
                LabelWithHelp(
                    "Commit",
                    helpKey = "commit",
                    helpText = "Commit message and authorship details for the upload. Author defaults to the active GitHub account; override only if you have permission to commit as someone else.",
                    helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(form.message, { vm.setMessage(it) }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { vm.aiSuggestMessage() }) { Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(4.dp)); Text("AI suggest") }
                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On conflict", style = MaterialTheme.typography.labelMedium)
                    InfoToggle(
                        helpKey = "conflict",
                        helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                    )
                }
                if (helpOpen == "conflict") {
                    Text(
                        "OVERWRITE — replace the existing file. " +
                            "SKIP — keep the version that's already in the repo, do not commit. " +
                            "RENAME — append a numeric suffix so both files coexist. " +
                            "REPLACE_FOLDER — make the target folder match exactly what you upload (deletes any extra files in that folder, in a single atomic commit).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConflictMode.values().forEach { m ->
                        FilterChip(selected = form.conflictMode == m, onClick = { vm.setMode(m) }, label = { Text(m.name) })
                    }
                }
                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Author", style = MaterialTheme.typography.labelMedium)
                    InfoToggle(
                        helpKey = "author",
                        helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                    )
                }
                if (helpOpen == "author") {
                    Text(
                        "Embedded in the Git commit object as both author and committer. Defaults to the signed-in account; change only when committing on someone else's behalf with permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(form.authorName ?: "", { vm.setAuthor(it, form.authorEmail) }, label = { Text("Author name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(form.authorEmail ?: "", { vm.setAuthor(form.authorName, it) }, label = { Text("Author email") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(form.dryRun, { vm.setDryRun(it) })
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dry run", style = MaterialTheme.typography.bodyMedium)
                        Text("Preview without committing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InfoToggle(
                        helpKey = "dry",
                        helpOpen = helpOpen, onHelpToggle = { helpOpen = it }
                    )
                }
                if (helpOpen == "dry") {
                    Text(
                        "Walks through every file and reports what would happen — sizes, conflicts, ignored paths — without making a single commit. Use it to sanity-check large uploads.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (progress.running || progress.finished) {
                GhCard {
                    Text(if (progress.finished) "Finished" else "Uploading…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { if (form.totalFiles == 0) 0f else progress.uploaded / form.totalFiles.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${progress.uploaded}/${progress.total}  ${ByteFormat.human(progress.bytesDone)} @ ${ByteFormat.human(progress.bytesPerSec.toLong())}/s  ETA ${progress.etaSeconds}s")
                    if (progress.currentFile.isNotEmpty()) Text(progress.currentFile, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress.running && !progress.paused) OutlinedButton(onClick = { vm.pause() }) { Text("Pause") }
                        if (progress.running && progress.paused) OutlinedButton(onClick = { vm.resume() }) { Text("Resume") }
                        if (progress.running) OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
                    }
                }
            }
            Button(onClick = { vm.start() }, enabled = form.totalFiles > 0 && !progress.running, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(6.dp))
                Text(if (form.dryRun) "Run preview" else "Start upload")
            }
            EmbeddedTerminal(section = "Upload", initiallyExpanded = progress.running)
            if (progress.files.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                    items(progress.files, key = { it.id }) { uf ->
                        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (uf.state) {
                                UploadFileState.DONE -> Icons.Filled.CheckCircle
                                UploadFileState.FAILED -> Icons.Filled.Error
                                UploadFileState.SKIPPED -> Icons.Filled.Block
                                UploadFileState.UPLOADING -> Icons.Filled.CloudUpload
                                else -> Icons.Filled.Pending
                            }
                            Icon(icon, null, tint = when (uf.state) {
                                UploadFileState.DONE -> MaterialTheme.colorScheme.primary
                                UploadFileState.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            })
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(uf.targetPath, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                if (uf.error != null) Text(uf.error!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text(ByteFormat.human(uf.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    if (showFolderBrowser) {
        ModalBottomSheet(onDismissRequest = { showFolderBrowser = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Pick destination folder", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Currently inside: /${form.browsePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (form.browseLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    if (form.browsePath.isNotBlank()) {
                        item {
                            ListItem(
                                headlineContent = { Text("..") },
                                leadingContent = { Icon(Icons.Filled.ArrowUpward, null) },
                                modifier = Modifier.clickable {
                                    val parent = form.browsePath.substringBeforeLast('/', "")
                                    vm.browseFolder(parent)
                                }
                            )
                        }
                    }
                    items(form.browseFolders) { folderName ->
                        ListItem(
                            headlineContent = { Text(folderName) },
                            leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                val next = if (form.browsePath.isBlank()) folderName else "${form.browsePath}/$folderName"
                                vm.browseFolder(next)
                            }
                        )
                    }
                    if (!form.browseLoading && form.browseFolders.isEmpty()) {
                        item {
                            Text(
                                "(no subfolders here)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showFolderBrowser = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { vm.applyBrowsedFolder(); showFolderBrowser = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Use /${form.browsePath}") }
                }
            }
        }
    }

    if (showBranchPicker) {
        ModalBottomSheet(onDismissRequest = { showBranchPicker = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Pick branch", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(form.branches) { b ->
                        ListItem(
                            headlineContent = { Text(b) },
                            leadingContent = { Icon(Icons.Filled.CallSplit, null) },
                            modifier = Modifier.clickable {
                                vm.setBranch(b)
                                showBranchPicker = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Section header with an Info icon that toggles a paragraph of help text below. */
@Composable
private fun LabelWithHelp(
    title: String,
    helpKey: String,
    helpText: String,
    helpOpen: String?,
    onHelpToggle: (String?) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        InfoToggle(helpKey = helpKey, helpOpen = helpOpen, onHelpToggle = onHelpToggle)
    }
    if (helpOpen == helpKey) {
        Text(
            helpText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun InfoToggle(
    helpKey: String,
    helpOpen: String?,
    onHelpToggle: (String?) -> Unit
) {
    IconButton(onClick = { onHelpToggle(if (helpOpen == helpKey) null else helpKey) }) {
        Icon(Icons.Filled.Info, contentDescription = "Help", tint = MaterialTheme.colorScheme.primary)
    }
}
