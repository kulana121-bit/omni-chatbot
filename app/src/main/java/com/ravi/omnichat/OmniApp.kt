package com.ravi.omnichat

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Saves any crash to a file so the app can show it (and recover) on the next launch. */
class OmniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Crash reports are kept at most 7 days.
        File(filesDir, CRASH_FILE).let { f -> if (f.exists() && System.currentTimeMillis() - f.lastModified() > 7 * 86_400_000L) f.delete() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                // Only the stack trace and app/OS version, with anything that looks like a key removed and the size capped.
                File(filesDir, CRASH_FILE).writeText(
                    com.ravi.omnichat.security.Redactor.redact(
                        "OmniChat ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}\n" +
                            "Thread: ${t.name}\n\n$sw"
                    ).take(12_000)
                )
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object { const val CRASH_FILE = "last_crash.txt" }
}
