package ua.anitube.app.player

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ua.anitube.app.net.Site
import kotlin.coroutines.resume

data class Resolved(val url: String, val referer: String, val subtitles: List<String>)

/**
 * Дістає пряме посилання на відео зі сторінки стороннього плеєра (Ashdi, Fenix, Moon).
 * Сторінку плеєра відкриває звичайний WebView без звуку; ми лише помічаємо мережеві запити
 * до .m3u8 / .mp4 і віддаємо їх у власний плеєр застосунку.
 */
object StreamResolver {
    private const val TIMEOUT_MS = 30_000L

    private const val PROBE_JS = """
(function(){
  try{
    var v=document.querySelector('video');
    if(v){v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});}
    var sel=['.pjsplay','.vjs-big-play-button','.jw-icon-display','.fp-play','#play','.play-button','[class*=play]'];
    for(var i=0;i<sel.length;i++){var e=document.querySelector(sel[i]);if(e){e.click();break;}}
  }catch(e){}
  var h=document.documentElement.innerHTML.replace(/\\\//g,'/');
  var m=h.match(/https?:\/\/[^"'\s<>\\]+\.(?:m3u8|mp4)[^"'\s<>\\]*/);
  return m?m[0]:'';
})()
"""

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(playerUrl: String): Resolved? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val wv = WebView(Site.app)
            var finished = false
            var streamUrl: String? = null
            val subs = LinkedHashSet<String>()

            fun finish(r: Resolved?) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                try { wv.stopLoading(); wv.loadUrl("about:blank"); wv.destroy() } catch (_: Exception) {}
                if (cont.isActive) cont.resume(r)
            }

            fun found(url: String) {
                if (streamUrl != null) return
                streamUrl = url
                // невелика затримка, щоб зібрати посилання на субтитри
                handler.postDelayed({ finish(Resolved(url, playerUrl, subs.toList())) }, 1200)
            }

            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = Site.UA
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadsImagesAutomatically = false
            }
            wv.layout(0, 0, 1080, 608)
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val u = request.url.toString()
                    val path = u.substringBefore('?').lowercase()
                    when {
                        path.endsWith(".m3u8") || path.endsWith(".mp4") || path.endsWith(".mpd") ->
                            handler.post { found(u) }
                        path.endsWith(".vtt") || path.endsWith(".srt") ->
                            handler.post { subs.add(u) }
                    }
                    return null
                }
            }

            fun probe(round: Int) {
                if (finished) return
                if (streamUrl == null) {
                    wv.evaluateJavascript(PROBE_JS) { raw ->
                        val s = raw?.trim('"').orEmpty()
                        if (s.startsWith("http")) found(s.replace("\\u0026", "&"))
                    }
                    if (round == 2 || round == 5 || round == 9) {
                        val t = SystemClock.uptimeMillis()
                        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { a ->
                            val e = MotionEvent.obtain(t, t, a, 540f, 304f, 0)
                            wv.dispatchTouchEvent(e); e.recycle()
                        }
                    }
                    handler.postDelayed({ probe(round + 1) }, 1200)
                }
            }

            cont.invokeOnCancellation { handler.post { finish(null) } }
            handler.postDelayed({ finish(streamUrl?.let { Resolved(it, playerUrl, subs.toList()) }) }, TIMEOUT_MS)
            wv.loadUrl(playerUrl, mapOf("Referer" to "${Site.BASE}/"))
            handler.postDelayed({ probe(0) }, 1500)
        }
    }
}
