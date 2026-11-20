@file:Suppress("MaxLineLength", "MagicNumber")

package org.fossify.messages.helpers

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Stores only exception classes and stack frames. Throwable messages are deliberately
 * omitted because they can contain SMS text, phone numbers, or other private data.
 */
object CrashLogger {
    private const val REPORT_PREFIX = "tidysms-crash-"
    private const val REPORT_COUNT = 3
    private const val MAX_REPORT_CHARS = 12_000

    @Volatile
    private var installed = false

    @Synchronized
    fun install(context: Context) {
        if (installed) return
        installed = true

        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                saveCrash(appContext, thread, throwable)
            } catch (_: Exception) {
                // Crash reporting must never prevent Android's normal crash handling.
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun readReports(context: Context): String? {
        val reports = (1..REPORT_COUNT)
            .map { File(context.filesDir, "$REPORT_PREFIX$it.txt") }
            .filter { it.isFile && it.length() > 0L }

        if (reports.isEmpty()) return null

        return reports.joinToString(separator = "\n\n--------------------\n\n") {
            it.readText().take(MAX_REPORT_CHARS)
        }
    }

    private fun saveCrash(context: Context, thread: Thread, throwable: Throwable) {
        val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val report = buildString {
            appendLine("TidySMS crash report")
            appendLine("Time (UTC): ${timeFormat.format(Date())}")
            appendLine("App version: $versionName")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            appendThrowableFrames(throwable)
        }.take(MAX_REPORT_CHARS)

        val directory = context.filesDir
        File(directory, "$REPORT_PREFIX$REPORT_COUNT.txt").delete()
        for (index in REPORT_COUNT - 1 downTo 1) {
            val oldFile = File(directory, "$REPORT_PREFIX$index.txt")
            if (oldFile.exists()) {
                oldFile.renameTo(File(directory, "$REPORT_PREFIX${index + 1}.txt"))
            }
        }

        val temporaryFile = File(directory, "$REPORT_PREFIX.tmp")
        FileOutputStream(temporaryFile).use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        temporaryFile.renameTo(File(directory, "$REPORT_PREFIX" + "1.txt"))
    }

    private fun StringBuilder.appendThrowableFrames(initial: Throwable) {
        var current: Throwable? = initial
        var causeCount = 0
        while (current != null && causeCount < 5) {
            appendLine("Exception: ${current.javaClass.name}")
            current.stackTrace.take(40).forEach { frame ->
                appendLine("  at ${frame.className}.${frame.methodName}(${frame.fileName ?: "Unknown"}:${frame.lineNumber})")
            }
            current = current.cause
            if (current != null) appendLine("Caused by:")
            causeCount++
        }
    }
}
