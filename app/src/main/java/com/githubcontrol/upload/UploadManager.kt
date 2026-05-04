package com.githubcontrol.upload

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.githubcontrol.data.api.PutFileRequest
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.notifications.Notifier
import com.githubcontrol.utils.GitignoreMatcher
import com.githubcontrol.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.util.Base64
import android.util.Base64OutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictMode { OVERWRITE, SKIP, RENAME, REPLACE_FOLDER }
enum class UploadFileState { PENDING, UPLOADING, DONE, SKIPPED, FAILED }

data class UploadFile(
    val id: String,
    val displayName: String,
    val targetPath: String,
    val sizeBytes: Long,
    var state: UploadFileState = UploadFileState.PENDING,
    var error: String? = null,
    var bytesDone: Long = 0,
)

data class UploadJob(
    val owner: String,
    val repo: String,
    val branch: String,
    val targetFolder: String,
    val message: String,
    val conflictMode: ConflictMode,
    val authorName: String?,
    val authorEmail: String?,
    val files: List<UploadFile>,
    val totalBytes: Long,
    val dryRun: Boolean = false,
)

data class UploadProgress(
    val job: UploadJob? = null,
    val files: List<UploadFile> = emptyList(),
    val currentFile: String = "",
    val uploaded: Int = 0,
    val total: Int = 0,
    val bytesDone: Long = 0,
    val bytesPerSec: Double = 0.0,
    val etaSeconds: Long = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
)

@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: GitHubRepository,
    private val notifier: Notifier
) {
    private val _state = MutableStateFlow(UploadProgress())
    val state: StateFlow<UploadProgress> = _state

    @Volatile var paused: Boolean = false
    @Volatile var cancelled: Boolean = false

    fun pause() { paused = true; _state.value = _state.value.copy(paused = true) }
    fun resume() { paused = false; _state.value = _state.value.copy(paused = false) }
    fun cancel() { cancelled = true; paused = false }

    /** Build an upload job from the given URIs (files and/or directories). */
    fun buildJob(
        uris: List<Uri>, owner: String, repo: String, branch: String, targetFolder: String,
        message: String, conflictMode: ConflictMode,
        authorName: String?, authorEmail: String?, gitignore: GitignoreMatcher? = null,
        dryRun: Boolean = false
    ): UploadJob {
        val files = mutableListOf<UploadFile>()
        var total = 0L
        for (uri in uris) {
            // OpenMultipleDocuments returns "single" document URIs; OpenDocumentTree returns "tree" URIs.
            // DocumentFile.fromTreeUri throws IllegalArgumentException on the former, so detect first.
            val df: DocumentFile? = runCatching {
                if (DocumentsContract.isTreeUri(uri)) DocumentFile.fromTreeUri(context, uri)
                else DocumentFile.fromSingleUri(context, uri)
            }.getOrNull() ?: runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()

            when {
                df == null -> {
                    Logger.w("Upload", "skip unreadable URI $uri")
                }
                df.isDirectory -> collectDir(df, targetFolder, files, gitignore)
                else -> {
                    val tname = df.name ?: "file"
                    val target = joinPath(targetFolder, tname)
                    if (gitignore?.isIgnored(target, false) != true) {
                        files += UploadFile(uri.toString(), tname, target, df.length())
                        total += df.length()
                    }
                }
            }
        }
        return UploadJob(owner, repo, branch, targetFolder, message, conflictMode, authorName, authorEmail, files, total, dryRun)
    }

    private fun collectDir(dir: DocumentFile, base: String, into: MutableList<UploadFile>, gitignore: GitignoreMatcher?) {
        val dname = dir.name ?: return
        val newBase = joinPath(base, dname)
        for (child in dir.listFiles()) {
            if (child.isDirectory) collectDir(child, newBase, into, gitignore)
            else {
                val target = joinPath(newBase, child.name ?: "file")
                if (gitignore?.isIgnored(target, false) != true) {
                    into += UploadFile(child.uri.toString(), child.name ?: "file", target, child.length())
                }
            }
        }
    }

    suspend fun runJob(job: UploadJob) = withContext(Dispatchers.IO) {
        cancelled = false; paused = false
        val start = System.currentTimeMillis()
        var bytesDone = 0L
        var done = 0
        _state.value = UploadProgress(job, job.files, "", 0, job.files.size, 0, 0.0, 0, true)
        Logger.i("Upload", "${if (job.dryRun) "DRY-RUN " else ""}start ${job.owner}/${job.repo}@${job.branch} files=${job.files.size} bytes=${job.totalBytes} mode=${job.conflictMode}")

        if (job.conflictMode == ConflictMode.REPLACE_FOLDER && !job.dryRun) {
            try {
                runReplaceFolder(job)
                done = job.files.count { it.state == UploadFileState.DONE }
                bytesDone = job.files.filter { it.state == UploadFileState.DONE }.sumOf { it.sizeBytes }
                _state.value = _state.value.copy(
                    files = job.files.toList(), uploaded = done, bytesDone = bytesDone,
                    running = false, finished = true,
                    error = if (job.files.any { it.state == UploadFileState.FAILED }) "Some files failed" else null
                )
                notifier.uploadDone(job.files.none { it.state == UploadFileState.FAILED }, "${done}/${job.files.size} files (folder replaced)")
                return@withContext
            } catch (t: Throwable) {
                Logger.e("Upload", "REPLACE_FOLDER failed", t)
                _state.value = _state.value.copy(running = false, finished = true, error = t.message ?: "Folder replace failed")
                notifier.uploadDone(false, t.message ?: "Folder replace failed")
                return@withContext
            }
        }

        // Files larger than this threshold skip the Contents API entirely and go
        // straight to the Git Blobs API via streaming (no full base64 String in heap).
        val LARGE_FILE_THRESHOLD = 50L * 1024 * 1024 // 50 MB

        for (uf in job.files) {
            if (cancelled) { Logger.w("Upload", "cancelled at ${uf.targetPath}"); break }
            while (paused && !cancelled) Thread.sleep(150)
            uf.state = UploadFileState.UPLOADING
            _state.value = _state.value.copy(currentFile = uf.targetPath, files = job.files.toList())

            // ── Dry-run (preview only) ───────────────────────────────────────────
            if (job.dryRun) {
                Logger.d("Upload", "PREVIEW ${uf.targetPath} (${uf.sizeBytes}B)")
                uf.state = UploadFileState.DONE
                uf.bytesDone = uf.sizeBytes
                bytesDone += uf.sizeBytes
                done++
                _state.value = _state.value.copy(files = job.files.toList(), uploaded = done, bytesDone = bytesDone)
                continue
            }

            // ── Conflict resolution ─────────────────────────────────────────────
            // Resolve path/sha BEFORE the upload attempt so the large-file fallback
            // path can reuse finalPath without re-fetching.
            var finalPath = uf.targetPath
            var targetSha: String? = null
            try {
                if (job.conflictMode == ConflictMode.SKIP) {
                    val exists = runCatching { repo.fileContent(job.owner, job.repo, uf.targetPath, job.branch) }.isSuccess
                    if (exists) { uf.state = UploadFileState.SKIPPED; done++; continue }
                }
                targetSha = if (job.conflictMode == ConflictMode.OVERWRITE) {
                    runCatching { repo.fileContent(job.owner, job.repo, uf.targetPath, job.branch).sha }.getOrNull()
                } else null
                finalPath = if (job.conflictMode == ConflictMode.RENAME)
                    renameIfExists(job.owner, job.repo, uf.targetPath, job.branch)
                else uf.targetPath
            } catch (t: Throwable) {
                uf.state = UploadFileState.FAILED
                uf.error = t.message
                Logger.e("Upload", "FAIL (conflict-check) ${uf.targetPath}", t)
                done++
                _state.value = _state.value.copy(files = job.files.toList(), uploaded = done, bytesDone = bytesDone)
                continue
            }

            // ── Upload ─────────────────────────────────────────────────────────
            // Files ≥ 50 MB: use Git Blobs API with streaming body (no OOM).
            // Files < 50 MB: use Contents API; fall back to streaming on OOM/413.
            val isLargeFile = uf.sizeBytes >= LARGE_FILE_THRESHOLD
            var uploadError: Throwable? = null

            if (!isLargeFile) {
                try {
                    val b64 = streamBase64(Uri.parse(uf.id), uf.sizeBytes)
                        ?: throw IllegalStateException("Cannot read ${uf.targetPath}")
                    val req = PutFileRequest(
                        message = job.message,
                        content = b64,
                        sha = targetSha,
                        branch = job.branch,
                        author = if (job.authorName != null && job.authorEmail != null)
                            com.githubcontrol.data.api.GhCommitAuthor(job.authorName, job.authorEmail, java.time.Instant.now().toString()) else null,
                        committer = if (job.authorName != null && job.authorEmail != null)
                            com.githubcontrol.data.api.GhCommitAuthor(job.authorName, job.authorEmail, java.time.Instant.now().toString()) else null,
                    )
                    repo.api.putFile(job.owner, job.repo, finalPath, req)
                    uf.state = UploadFileState.DONE
                    uf.bytesDone = uf.sizeBytes
                    bytesDone += uf.sizeBytes
                    Logger.i("Upload", "OK   $finalPath  ${uf.sizeBytes}B")
                } catch (t: Throwable) {
                    uploadError = t
                    Logger.w("Upload", "Contents API failed for ${uf.targetPath}: ${t.javaClass.simpleName}: ${t.message}")
                }
            }

            // Trigger streaming path for:
            //   • Files that were already identified as large (≥ 50 MB)
            //   • Files that failed with OOM / allocation error / HTTP 413
            val needStreaming = isLargeFile || run {
                val err = uploadError ?: return@run false
                val isOom = err is OutOfMemoryError ||
                    err.cause is OutOfMemoryError ||
                    err.message?.contains("allocation", ignoreCase = true) == true ||
                    err.message?.contains("OutOfMemory", ignoreCase = true) == true
                val isPayloadTooLarge = err is retrofit2.HttpException && err.code() == 413
                isOom || isPayloadTooLarge
            }

            if (needStreaming && uf.state != UploadFileState.DONE) {
                Logger.i("Upload", "${if (isLargeFile) "Large file" else "OOM retry"}: streaming ${uf.targetPath} (${uf.sizeBytes}B) via Git Blobs API")
                try {
                    repo.commitSingleLargeFile(
                        owner       = job.owner,
                        repoName    = job.repo,
                        branch      = job.branch,
                        targetPath  = finalPath,
                        uri         = Uri.parse(uf.id),
                        message     = job.message,
                        authorName  = job.authorName,
                        authorEmail = job.authorEmail
                    )
                    uf.state     = UploadFileState.DONE
                    uf.bytesDone = uf.sizeBytes
                    bytesDone   += uf.sizeBytes
                    Logger.i("Upload", "OK (streaming) $finalPath  ${uf.sizeBytes}B")
                } catch (retry: Throwable) {
                    uf.state = UploadFileState.FAILED
                    uf.error = retry.message
                    Logger.e("Upload", "FAIL (streaming) ${uf.targetPath}", retry)
                }
            } else if (uploadError != null && uf.state != UploadFileState.DONE) {
                // Small file that failed for a non-OOM reason — report the original error
                uf.state = UploadFileState.FAILED
                uf.error = uploadError.message
                Logger.e("Upload", "FAIL ${uf.targetPath}", uploadError)
            }

            done++
            val elapsed = (System.currentTimeMillis() - start) / 1000.0
            val rate = if (elapsed > 0) bytesDone / elapsed else 0.0
            val eta = if (rate > 0) ((job.totalBytes - bytesDone) / rate).toLong() else 0
            notifier.upload(done, job.files.size, uf.displayName)
            _state.value = _state.value.copy(
                files = job.files.toList(), uploaded = done, bytesDone = bytesDone,
                bytesPerSec = rate, etaSeconds = eta
            )
        }
        val ok = job.files.none { it.state == UploadFileState.FAILED }
        if (!job.dryRun) {
            notifier.uploadDone(ok, "${job.files.count { it.state == UploadFileState.DONE }}/${job.files.size} files")
        }
        Logger.i("Upload", "${if (job.dryRun) "DRY-RUN " else ""}done ok=$ok done=$done failed=${job.files.count { it.state == UploadFileState.FAILED }} skipped=${job.files.count { it.state == UploadFileState.SKIPPED }}")
        _state.value = _state.value.copy(running = false, finished = true, error = if (ok) null else "Some files failed")
    }

    private suspend fun renameIfExists(owner: String, repoName: String, path: String, branch: String): String {
        var candidate = path
        var i = 1
        while (runCatching { repo.fileContent(owner, repoName, candidate, branch) }.isSuccess) {
            val dot = path.lastIndexOf('.')
            candidate = if (dot > 0 && dot > path.lastIndexOf('/')) {
                path.substring(0, dot) + "-$i" + path.substring(dot)
            } else "$path-$i"
            i++
            if (i > 99) break
        }
        return candidate
    }

    suspend fun uploadZipExpanded(
        owner: String, repoName: String, branch: String, targetFolder: String,
        zipUri: Uri, message: String, authorName: String?, authorEmail: String?
    ) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val files = mutableListOf<Pair<String, ByteArray>>()
        resolver.openInputStream(zipUri)?.use { input ->
            ZipInputStream(input).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        zin.copyTo(baos)
                        files += joinPath(targetFolder, entry.name) to baos.toByteArray()
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }
        }
        // Atomic multi-file commit via Git data API
        repo.commitFiles(owner, repoName, branch, files.map { it.first to it.second as ByteArray? }, message, authorName, authorEmail)
    }

    private fun readUri(uri: Uri): ByteArray? = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    /**
     * Stream the URI through a Base64 encoder so we never hold the raw bytes and
     * the encoded string in memory at the same time.
     *
     * [fileSizeBytes] is used to pre-size the [ByteArrayOutputStream] to the exact
     * base64 output length, avoiding the internal buffer-doubling that would otherwise
     * waste up to 2× the encoded size in heap.  Pass -1 if size is unknown.
     *
     * NOTE: For files ≥ 50 MB use [GitHubRepository.commitSingleLargeFile] instead,
     * which streams directly into the OkHttp socket and avoids holding any String in memory.
     */
    private fun streamBase64(uri: Uri, fileSizeBytes: Long = -1): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        // ceil(n/3)*4 gives exact base64 output byte count; pre-size to avoid growth copies.
        val exactSize = if (fileSizeBytes > 0)
            ((fileSizeBytes / 3 + 1) * 4).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else
            65536
        return input.use { ins ->
            val baos = ByteArrayOutputStream(exactSize)
            Base64OutputStream(baos, Base64.NO_WRAP).use { b64 ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    b64.write(buf, 0, n)
                }
            }
            baos.toString("US-ASCII")
        }
    }

    /**
     * Atomically replace the contents of [job.targetFolder]: deletes any existing
     * files inside that folder that aren't being uploaded, then writes all of the
     * new files in a single Git Data API commit.
     */
    private suspend fun runReplaceFolder(job: UploadJob) {
        // 1. List existing files under the target folder (recursively).
        val targetFolder = job.targetFolder.trim('/')
        val existing: List<String> = runCatching {
            val branchInfo = repo.api.branch(job.owner, job.repo, job.branch)
            val parent = repo.api.commitDetail(job.owner, job.repo, branchInfo.commit.sha)
            val ft = repo.api.gitTree(job.owner, job.repo, parent.commit.tree.sha, recursive = 1)
            ft.tree.filter { it.type == "blob" && (targetFolder.isEmpty() || it.path.startsWith("$targetFolder/")) }
                .map { it.path }
        }.getOrElse { emptyList() }

        val newPaths = job.files.map { it.targetPath.trim('/') }.toSet()
        val toDelete = existing.filterNot { it in newPaths }

        // 2. Build commitFiles list (deletes + adds). Use ByteArray? = null for deletes.
        val ops = mutableListOf<Pair<String, ByteArray?>>()
        for (path in toDelete) ops += path to null
        for (uf in job.files) {
            try {
                val bytes = readUri(Uri.parse(uf.id)) ?: throw IllegalStateException("Cannot read")
                ops += uf.targetPath to bytes
                uf.state = UploadFileState.DONE
                uf.bytesDone = uf.sizeBytes
            } catch (t: Throwable) {
                uf.state = UploadFileState.FAILED
                uf.error = t.message
                Logger.e("Upload", "FAIL read ${uf.targetPath}", t)
            }
        }
        // 3. Single atomic commit.
        repo.commitFiles(job.owner, job.repo, job.branch, ops, job.message, job.authorName, job.authorEmail)
        Logger.i("Upload", "REPLACE_FOLDER committed: deleted=${toDelete.size} added=${job.files.size}")
    }

    private fun joinPath(base: String, name: String): String {
        val b = base.trim('/')
        val n = name.trim('/')
        return if (b.isEmpty()) n else "$b/$n"
    }
}
