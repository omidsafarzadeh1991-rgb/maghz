package ir.maghz.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The whole library lives in assets/index.html + assets/content.json, so it works with no internet.
 * When the phone is online the app downloads a newer content.json from GitHub (two mirrors) and keeps it
 * in private storage; the newest of "bundled in the APK" and "downloaded" is always the one shown.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val main = Handler(Looper.getMainLooper())

    private val mirrors = listOf(
        "https://raw.githubusercontent.com/omidsafarzadeh1991-rgb/maghz/main/app/src/main/assets/content.json",
        "https://cdn.jsdelivr.net/gh/omidsafarzadeh1991-rgb/maghz@main/app/src/main/assets/content.json",
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.textZoom = 100
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") {
                    openExternal(u)
                    return true
                }
                return false
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState)
        } else {
            web.loadUrl("file:///android_asset/index.html")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Back navigation inside the library")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    // ---------- content storage ----------

    private fun cacheFile() = File(filesDir, "content.json")

    private fun bundled(): String =
        assets.open("content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun revOf(text: String?): Long =
        try {
            val o = JSONObject(text ?: "")
            if (o.has("lessons")) o.optLong("rev", 0) else -1
        } catch (e: Exception) {
            -1
        }

    private fun lessonCount(text: String): Int =
        try { JSONObject(text).optJSONArray("lessons")?.length() ?: 0 } catch (e: Exception) { 0 }

    @Synchronized
    private fun current(): String {
        val b = bundled()
        val f = cacheFile()
        if (f.exists()) {
            val c = try { f.readText(Charsets.UTF_8) } catch (e: Exception) { null }
            if (c != null && revOf(c) > revOf(b)) return c
        }
        return b
    }

    @Synchronized
    private fun store(text: String) {
        val tmp = File(filesDir, "content.json.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        tmp.renameTo(cacheFile())
    }

    private fun notifyPage(status: String, detail: String) {
        main.post {
            web.evaluateJavascript(
                "window.onUpdate && window.onUpdate(" + JSONObject.quote(status) + "," + JSONObject.quote(detail) + ")",
                null
            )
        }
    }

    private fun download(url: String): String? =
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.setRequestProperty("Cache-Control", "no-cache")
            c.setRequestProperty("User-Agent", "Maghz-Android")
            if (c.responseCode == 200) {
                c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }

    private fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u))
        } catch (e: Exception) {
            // no browser on the phone; the page shows the link as text anyway
        }
    }

    // ---------- file import (update from a file passed by Bluetooth/flash/Telegram) ----------

    @Deprecated("Uses the platform picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FILE) return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            notifyPage("import-cancel", "")
            return
        }
        Thread {
            val text = try {
                contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            } catch (e: Exception) {
                null
            }
            val r = revOf(text)
            when {
                text == null || r <= 0 -> notifyPage("import-bad", "")
                r <= revOf(current()) -> notifyPage("import-old", "")
                else -> {
                    store(text)
                    notifyPage("updated", lessonCount(text).toString())
                }
            }
        }.start()
    }

    // ---------- bridge for index.html ----------

    inner class Bridge {
        @JavascriptInterface
        fun getContent(): String = current()

        @JavascriptInterface
        fun checkUpdate() {
            Thread {
                var best: String? = null
                for (u in mirrors) {
                    val t = download(u)
                    if (revOf(t) > 0) { best = t; break }
                }
                val now = current()
                when {
                    best == null -> notifyPage("offline", "")
                    revOf(best) > revOf(now) -> {
                        store(best)
                        notifyPage("updated", (lessonCount(best) - lessonCount(now)).toString())
                    }
                    else -> notifyPage("same", "")
                }
            }.start()
        }

        @JavascriptInterface
        fun importFile() {
            main.post {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, PICK_FILE)
                } catch (e: Exception) {
                    notifyPage("import-bad", "")
                }
            }
        }

        @JavascriptInterface
        fun copy(text: String) {
            main.post {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("maghz", text))
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            main.post { openExternal(Uri.parse(url)) }
        }

        @JavascriptInterface
        fun appVersion(): String =
            try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }

    companion object {
        private const val PICK_FILE = 41
    }
}
