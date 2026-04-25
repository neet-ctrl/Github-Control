package com.githubcontrol.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists every uncaught exception (main thread, background threads, coroutines'
 * default exception handler) to disk under [Context.filesDir] / "crashes" so the
 * user can review them later. Reports include the full stack trace, every
 * `caused-by` chain, device info, and the last 200 in-memory log entries from
 * [Logger]. Files are kept until the user deletes them from the in-app
 * "Crash reports" screen.
 */
object CrashHandler {

    private const val DIR_NAME = "crashes"
    private const val MAX_FILES = 200
    private val tsFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    @Volatile private var installed = false
    private lateinit var appContext: Context

    /** Call once from [android.app.Application.onCreate] (very early). */
    fun install(context: Context) {
        if (installed) return
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try { writeReport(thread, throwable) } catch (_: Throwable) { /* never crash the crash reporter */ }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
        Logger.i("CrashHandler", "installed → ${dir().absolutePath}")
    }

    /** Manually record a non-fatal error so it's preserved like a crash. */
    fun recordNonFatal(tag: String, throwable: Throwable) {
        if (!installed) return
        try { writeReport(Thread.currentThread(), throwable, label = "non-fatal · $tag") }
        catch (_: Throwable) { /* ignore */ }
    }

    private fun writeReport(thread: Thread, t: Throwable, label: String = "uncaught") {
        val now = System.currentTimeMillis()
        val name = "${tsFormat.format(Date(now))}_${label.replace(" ", "_").replace("·", "")}.txt"
        val file = File(dir(), name)
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("# GitHub Control crash report")
            pw.println("Timestamp:   ${Date(now)}  (epoch=$now)")
            pw.println("Type:        $label")
            pw.println("Thread:      ${thread.name} (id=${thread.id}, prio=${thread.priority})")
            pw.println("Exception:   ${t.javaClass.name}: ${t.message}")
            pw.println()
            pw.println("# Device")
            pw.println("Brand:       ${Build.BRAND}")
            pw.println("Model:       ${Build.MODEL}")
            pw.println("Manufacturer:${Build.MANUFACTURER}")
            pw.println("Product:     ${Build.PRODUCT}")
            pw.println("Android:     ${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT})")
            pw.println("ABI:         ${Build.SUPPORTED_ABIS.joinToString(",")}")
            pw.println("App version: ${runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull() ?: "?"}")
            pw.println()
            pw.println("# Stack trace")
            t.printStackTrace(pw)
            var cause: Throwable? = t.cause
            var depth = 1
            while (cause != null && depth < 8) {
                pw.println()
                pw.println("# Caused by ($depth)")
                cause.printStackTrace(pw)
                cause = cause.cause; depth++
            }
            pw.println()
            pw.println("# Recent in-app log (last 200 entries)")
            try {
                Logger.entries.value.takeLast(200).forEach { e -> pw.println(e.formatted()) }
            } catch (_: Throwable) {
                pw.println("(log unavailable)")
            }
        }
        file.writeText(sw.toString())
        Logger.e("CrashHandler", "wrote ${file.name} (${file.length()} bytes)")
        rotate()
    }

    fun list(): List<File> = dir().listFiles().orEmpty()
        .filter { it.isFile && it.extension == "txt" }
        .sortedByDescending { it.lastModified() }

    fun read(file: File): String = runCatching { file.readText() }.getOrElse { "(unable to read ${file.name}: ${it.message})" }

    fun delete(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    fun deleteAll(): Int {
        var n = 0
        list().forEach { if (it.delete()) n++ }
        Logger.w("CrashHandler", "cleared $n crash report(s)")
        return n
    }

    private fun dir(): File {
        val d = File(appContext.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun rotate() {
        val files = list()
        if (files.size > MAX_FILES) {
            files.drop(MAX_FILES).forEach { it.delete() }
        }
    }
}
