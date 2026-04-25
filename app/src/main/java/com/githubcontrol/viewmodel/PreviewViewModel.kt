package com.githubcontrol.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.githubcontrol.data.api.GhContent
import com.githubcontrol.data.api.PutFileRequest
import com.githubcontrol.data.repository.GitHubRepository
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.fromBase64
import com.githubcontrol.utils.toBase64
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PreviewKind { Text, Markdown, Image, Pdf, Audio, Video, Html, Svg, Archive, Binary }

data class PreviewState(
    val loading: Boolean = false,
    val content: GhContent? = null,
    val text: String? = null,
    val bytes: ByteArray? = null,
    val kind: PreviewKind = PreviewKind.Binary,
    val mimeType: String = "application/octet-stream",
    val error: String? = null,
    val saving: Boolean = false
)

@HiltViewModel
class PreviewViewModel @Inject constructor(private val repo: GitHubRepository) : ViewModel() {
    private val _state = MutableStateFlow(PreviewState())
    val state: StateFlow<PreviewState> = _state

    fun load(owner: String, name: String, path: String, ref: String) {
        viewModelScope.launch {
            _state.value = PreviewState(loading = true)
            try {
                val c = repo.fileContent(owner, name, path, ref.ifBlank { null })
                val ext = path.substringAfterLast('.', "").lowercase()
                val mime = guessMimeType(ext)
                val kind = classify(ext, mime)

                val bytes: ByteArray? = withContext(Dispatchers.IO) {
                    try {
                        if (c.encoding == "base64" && !c.content.isNullOrBlank()) {
                            c.content!!.fromBase64()
                        } else if (!c.downloadUrl.isNullOrBlank()) {
                            val resp = repo.rawDownload(c.downloadUrl!!)
                            if (resp.isSuccessful) resp.body()?.bytes() else null
                        } else null
                    } catch (t: Throwable) {
                        Logger.w("Preview", "byte fetch failed for $path: ${t.message}")
                        null
                    }
                }

                val text: String? = if (bytes != null && (kind == PreviewKind.Text || kind == PreviewKind.Markdown
                            || kind == PreviewKind.Html || kind == PreviewKind.Svg) && bytes.size < 5_000_000)
                    runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                else null

                _state.value = PreviewState(
                    loading = false, content = c, text = text, bytes = bytes,
                    kind = kind, mimeType = mime
                )
            } catch (t: Throwable) {
                Logger.e("Preview", "load failed: ${t.message}")
                _state.value = PreviewState(loading = false, error = t.message)
            }
        }
    }

    fun save(owner: String, name: String, path: String, ref: String, newText: String, message: String, onDone: () -> Unit) {
        val sha = _state.value.content?.sha ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            try {
                repo.api.putFile(owner, name, path, PutFileRequest(message, newText.toByteArray().toBase64(), sha, ref.ifBlank { null }))
                onDone()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message)
            } finally {
                _state.value = _state.value.copy(saving = false)
            }
        }
    }

    private fun classify(ext: String, mime: String): PreviewKind = when {
        ext in setOf("md", "markdown", "mdown", "mkd") -> PreviewKind.Markdown
        ext == "pdf" -> PreviewKind.Pdf
        ext in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "heic", "heif") -> PreviewKind.Image
        ext == "svg" -> PreviewKind.Svg
        ext in setOf("html", "htm", "xhtml") -> PreviewKind.Html
        ext in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr") -> PreviewKind.Audio
        ext in setOf("mp4", "webm", "mkv", "mov", "3gp", "avi", "ts", "m4v") -> PreviewKind.Video
        ext in setOf("zip", "tar", "gz", "tgz", "rar", "7z", "bz2", "xz", "jar", "apk", "aar", "war") -> PreviewKind.Archive
        mime.startsWith("text/") || isTextExtension(ext) -> PreviewKind.Text
        else -> PreviewKind.Binary
    }

    private fun isTextExtension(ext: String): Boolean = ext in setOf(
        "txt","log","csv","tsv","json","xml","yaml","yml","toml","ini","conf","cfg","env",
        "kt","java","scala","groovy","gradle","kts","js","jsx","ts","tsx","mjs","cjs",
        "py","rb","go","rs","c","cc","cpp","cxx","h","hpp","hh","m","mm","swift",
        "php","pl","sh","bash","zsh","fish","bat","cmd","ps1","sql","r","lua","dart",
        "vue","svelte","astro","sass","scss","less","css","tex","bib","makefile","mk",
        "dockerfile","gitignore","gitattributes","editorconfig","prettierrc","eslintrc",
        "lock","properties","plist","strings","po","pot","srt","vtt","diff","patch","s","asm"
    )

    private fun guessMimeType(ext: String): String = when (ext) {
        "png" -> "image/png"; "jpg","jpeg" -> "image/jpeg"; "gif" -> "image/gif"
        "webp" -> "image/webp"; "bmp" -> "image/bmp"; "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
        "m4a","aac" -> "audio/aac"; "flac" -> "audio/flac"; "opus" -> "audio/opus"
        "mp4","m4v" -> "video/mp4"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "avi" -> "video/x-msvideo"
        "html","htm" -> "text/html"; "xml" -> "application/xml"
        "json" -> "application/json"; "txt","log" -> "text/plain"
        "csv" -> "text/csv"; "md","markdown" -> "text/markdown"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
