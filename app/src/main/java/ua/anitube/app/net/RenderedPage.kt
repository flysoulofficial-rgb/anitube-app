package ua.anitube.app.net

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Сторінка аніме підвантажує список серій (озвучки, плеєри) скриптом сайту.
 * Тому відкриваємо її у справжньому WebView, чекаємо поки скрипт сайту заповнить плейлист
 * і забираємо готову розмітку.
 */
object RenderedPage {
    private const val JS = "(function(){var c=document.querySelector('#cinema,.playlists-ajax');" +
        "return (c&&c.querySelectorAll('li[data-file]').length>0)?document.documentElement.outerHTML:'';})()"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun withPlaylist(url: String, timeoutMs: Long = 25_000): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val wv = WebView(Site.app)
            var done = false
            fun finish(html: String?) {
                if (done) return
                done = true
                handler.removeCallbacksAndMessages(null)
                try { wv.stopLoading(); wv.destroy() } catch (_: Exception) {}
                if (cont.isActive) cont.resume(html)
            }
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = Site.UA
                loadsImagesAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            wv.layout(0, 0, 1080, 1920)
            wv.webViewClient = WebViewClient()
            fun poll() {
                if (done) return
                wv.evaluateJavascript(JS) { raw ->
                    val s = try { JSONTokener(raw ?: "").nextValue() as? String } catch (_: Exception) { null }
                    if (!s.isNullOrEmpty()) finish(s) else handler.postDelayed({ poll() }, 700)
                }
            }
            cont.invokeOnCancellation { handler.post { finish(null) } }
            handler.postDelayed({ finish(null) }, timeoutMs)
            wv.loadUrl(url, mapOf("Referer" to "${Site.BASE}/"))
            handler.postDelayed({ poll() }, 1500)
        }
    }
}
