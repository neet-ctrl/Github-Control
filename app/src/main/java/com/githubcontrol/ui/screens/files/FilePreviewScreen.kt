package com.githubcontrol.ui.screens.files

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.githubcontrol.ui.components.GhBadge
import com.githubcontrol.ui.components.GhCard
import com.githubcontrol.ui.components.LoadingIndicator
import com.githubcontrol.ui.theme.LocalTerminalTheme
import com.githubcontrol.utils.Logger
import com.githubcontrol.utils.ShareUtils
import com.githubcontrol.viewmodel.PreviewKind
import com.githubcontrol.viewmodel.PreviewViewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    owner: String, name: String, path: String, ref: String,
    onBack: () -> Unit,
    vm: PreviewViewModel = hiltViewModel()
) {
    LaunchedEffect(owner, name, path, ref) { vm.load(owner, name, path, ref) }
    val ctx = LocalContext.current
    val s by vm.state.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var content by remember(s.text) { mutableStateOf(s.text ?: "") }
    var commitMsg by remember { mutableStateOf("Update $path") }
    val fileName = remember(path) { path.substringAfterLast('/') }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(s.mimeType)) { uri ->
        if (uri != null && s.bytes != null) {
            runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(s.bytes) }
                Logger.i("Preview", "downloaded $fileName → $uri")
            }.onFailure { Logger.w("Preview", "download failed: ${it.message}") }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (s.kind == PreviewKind.Text || s.kind == PreviewKind.Markdown) {
                        if (s.text != null && !editing) TextButton(onClick = { editing = true }) { Text("Edit") }
                        if (editing) IconButton(onClick = {
                            vm.save(owner, name, path, ref, content, commitMsg) { editing = false }
                        }) { Icon(Icons.Filled.Save, null) }
                    }
                    s.text?.let { txt ->
                        IconButton(onClick = { ShareUtils.copyToClipboard(ctx, txt, label = fileName) }) {
                            Icon(Icons.Filled.ContentCopy, null)
                        }
                    }
                    if (s.bytes != null) {
                        IconButton(onClick = { saveLauncher.launch(fileName) }) {
                            Icon(Icons.Filled.Download, null)
                        }
                        IconButton(onClick = { openExternally(ctx, fileName, s.mimeType, s.bytes!!) }) {
                            Icon(Icons.Filled.OpenInNew, null)
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(10.dp)) {
            HeaderRow(s.kind, s.mimeType, s.bytes?.size ?: 0)
            Spacer(Modifier.height(8.dp))

            when {
                s.loading -> LoadingIndicator()
                s.error != null -> Text(s.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                else -> when (s.kind) {
                    PreviewKind.Markdown -> MarkdownView(s.text ?: "", editing, content, { content = it }, commitMsg, { commitMsg = it })
                    PreviewKind.Text -> TextView(s.text ?: "", editing, content, { content = it }, commitMsg, { commitMsg = it })
                    PreviewKind.Image -> ImageView(s)
                    PreviewKind.Svg -> SvgView(s.text)
                    PreviewKind.Html -> HtmlView(s.text ?: "")
                    PreviewKind.Pdf -> PdfView(ctx, s.bytes, fileName)
                    PreviewKind.Audio -> MediaView(ctx, s.bytes, fileName, isVideo = false)
                    PreviewKind.Video -> MediaView(ctx, s.bytes, fileName, isVideo = true)
                    PreviewKind.Archive -> ArchiveView(s.bytes)
                    PreviewKind.Binary -> HexDumpView(s.bytes)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(kind: PreviewKind, mime: String, size: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GhBadge(kind.name.lowercase())
        GhBadge(mime)
        GhBadge(humanBytes(size))
    }
}

@Composable
private fun MarkdownView(
    text: String, editing: Boolean,
    edited: String, onEdit: (String) -> Unit,
    msg: String, onMsg: (String) -> Unit
) {
    if (editing) {
        EditorBox(edited, onEdit, msg, onMsg)
    } else {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState()).padding(12.dp)
        ) { MarkdownText(markdown = text, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun TextView(
    text: String, editing: Boolean,
    edited: String, onEdit: (String) -> Unit,
    msg: String, onMsg: (String) -> Unit
) {
    if (editing) {
        EditorBox(edited, onEdit, msg, onMsg)
    } else {
        val palette = LocalTerminalTheme.current
        Box(
            Modifier.fillMaxSize().background(palette.bg, RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text.ifEmpty { "(empty file)" },
                color = palette.fg,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EditorBox(
    edited: String, onEdit: (String) -> Unit,
    msg: String, onMsg: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(msg, onMsg, label = { Text("Commit message") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            edited, onEdit,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        )
    }
}

@Composable
private fun ImageView(s: com.githubcontrol.viewmodel.PreviewState) {
    val model: Any? = s.bytes ?: s.content?.downloadUrl
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        )
    } else {
        Text("Image data unavailable.")
    }
}

@Composable
private fun SvgView(text: String?) {
    if (text == null) {
        Text("SVG data unavailable.")
    } else {
        val palette = LocalTerminalTheme.current
        val bgHex = "#%06X".format(0xFFFFFF and palette.bg.toArgb())
        val fgHex = "#%06X".format(0xFFFFFF and palette.fg.toArgb())
        HtmlView("<html><body style='margin:0;background:$bgHex;color:$fgHex'>$text</body></html>")
    }
}

@Composable
private fun HtmlView(html: String) {
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
            }
        },
        update = { it.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null) }
    )
}

@Composable
private fun PdfView(ctx: android.content.Context, bytes: ByteArray?, fileName: String) {
    if (bytes == null) { Text("PDF data unavailable."); return }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(bytes) {
        try {
            pages = withContext(Dispatchers.IO) { renderPdf(ctx, bytes, fileName) }
        } catch (t: Throwable) {
            error = t.message
        }
    }
    error?.let { Text("PDF error: $it", color = MaterialTheme.colorScheme.error); return }
    if (pages.isEmpty()) { LoadingIndicator(); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(pages) { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

private fun renderPdf(ctx: android.content.Context, bytes: ByteArray, fileName: String): List<Bitmap> {
    val tmp = File(ctx.cacheDir, "preview/$fileName")
    tmp.parentFile?.mkdirs()
    tmp.writeBytes(bytes)
    val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
    val out = mutableListOf<Bitmap>()
    PdfRenderer(pfd).use { renderer ->
        for (i in 0 until renderer.pageCount.coerceAtMost(50)) {
            val page = renderer.openPage(i)
            val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            out += bmp
            page.close()
        }
    }
    return out
}

@Composable
private fun MediaView(ctx: android.content.Context, bytes: ByteArray?, fileName: String, isVideo: Boolean) {
    if (bytes == null) { Text("Media data unavailable."); return }
    val uri = remember(bytes, fileName) { writeCacheUri(ctx, fileName, bytes) }
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black),
        factory = { c ->
            VideoView(c).apply {
                setMediaController(MediaController(c).also { it.setAnchorView(this) })
                setVideoURI(uri)
                requestFocus()
                start()
            }
        }
    )
    if (!isVideo) {
        Spacer(Modifier.height(6.dp))
        Text("Audio file • use the player controls above.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ArchiveView(bytes: ByteArray?) {
    if (bytes == null) { Text("Archive data unavailable."); return }
    val entries = remember(bytes) {
        runCatching {
            val list = mutableListOf<String>()
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zin ->
                var e = zin.nextEntry
                var n = 0
                while (e != null && n < 2000) {
                    list += "${if (e.isDirectory) "📁" else "📄"} ${e.name}  (${e.size} B)"
                    zin.closeEntry(); e = zin.nextEntry; n++
                }
            }
            list
        }.getOrElse { listOf("(not a recognized archive: ${it.message})") }
    }
    val palette = LocalTerminalTheme.current
    LazyColumn(modifier = Modifier.fillMaxSize().background(palette.bg, RoundedCornerShape(8.dp)).padding(10.dp)) {
        items(entries) { Text(it, color = palette.fg, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
    }
}

@Composable
private fun HexDumpView(bytes: ByteArray?) {
    if (bytes == null) {
        GhCard { Text("Binary file — use Open or Download from the toolbar to view it in another app.") }
        return
    }
    val dump = remember(bytes) { hexDump(bytes, max = 8192) }
    val palette = LocalTerminalTheme.current
    Box(
        Modifier.fillMaxSize().background(palette.bg, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState()).padding(10.dp)
    ) {
        Text(dump, color = palette.fg, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

private fun hexDump(bytes: ByteArray, max: Int): String {
    val limit = minOf(bytes.size, max)
    val sb = StringBuilder()
    var i = 0
    while (i < limit) {
        val end = minOf(i + 16, limit)
        sb.append(String.format("%08x  ", i))
        for (j in i until i + 16) {
            sb.append(if (j < end) String.format("%02x ", bytes[j]) else "   ")
            if (j == i + 7) sb.append(" ")
        }
        sb.append(" |")
        for (j in i until end) {
            val b = bytes[j].toInt() and 0xff
            sb.append(if (b in 32..126) b.toChar() else '.')
        }
        sb.append("|\n")
        i += 16
    }
    if (bytes.size > max) sb.append("\n… (${bytes.size - max} more bytes — use Open or Download to see the full file)")
    return sb.toString()
}

private fun writeCacheUri(ctx: android.content.Context, fileName: String, bytes: ByteArray): Uri {
    val dir = File(ctx.cacheDir, "preview").apply { mkdirs() }
    val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    val f = File(dir, safeName)
    f.writeBytes(bytes)
    return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
}

private fun openExternally(ctx: android.content.Context, fileName: String, mime: String, bytes: ByteArray) {
    runCatching {
        val uri = writeCacheUri(ctx, fileName, bytes)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Logger.i("Preview", "open externally $fileName ($mime)")
    }.onFailure { Logger.w("Preview", "openExternally failed: ${it.message}") }
}

private fun humanBytes(n: Int): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${"%.1f".format(n / 1024.0)} KB"
    else -> "${"%.2f".format(n / (1024.0 * 1024.0))} MB"
}
