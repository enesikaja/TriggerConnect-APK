package com.triggerconnect.app.core

import android.content.Context
import android.os.Build
import com.triggerconnect.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Captures uncaught exceptions to a local file and uploads them to the backend
 * on the next launch. This gives remote visibility into device-specific crashes
 * we can't reproduce locally. Best-effort: never throws.
 */
object CrashReporter {

    private const val FILE = "last_crash.json"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val json = JSONObject()
                    .put("appVersion", BuildConfig.VERSION_NAME)
                    .put("device", "${Build.MANUFACTURER} ${Build.MODEL} (One UI / API ${Build.VERSION.SDK_INT})")
                    .put("androidSdk", Build.VERSION.SDK_INT)
                    .put("thread", thread.name)
                    .put("message", throwable.toString())
                    .put("stacktrace", sw.toString())
                appContext.openFileOutput(FILE, Context.MODE_PRIVATE)
                    .use { it.write(json.toString().toByteArray()) }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Upload any saved crash from a previous run, then delete it on success. */
    fun flush(context: Context) {
        val appContext = context.applicationContext
        Thread {
            runCatching {
                val f = File(appContext.filesDir, FILE)
                if (!f.exists()) return@runCatching
                val body = f.readText()
                if (postCrash(body)) f.delete()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun postCrash(json: String): Boolean {
        val url = URL(BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/report-crash")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
        }
        return try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
