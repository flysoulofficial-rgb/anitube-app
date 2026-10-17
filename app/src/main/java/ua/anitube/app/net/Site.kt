package ua.anitube.app.net

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SiteException(message: String) : Exception(message)

/** Мережевий доступ до сайту: OkHttp зі спільними з WebView cookie та тим самим User-Agent. */
object Site {
    const val BASE = "https://anitube.in.ua"
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36 AniTubeApp/1.0"

    lateinit var app: Context
        private set

    fun init(context: Context) {
        app = context.applicationContext
        CookieManager.getInstance().setAcceptCookie(true)
    }

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cm = CookieManager.getInstance()
            cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
            cm.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val raw = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
            return raw.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Стан перевірки безпеки сайту (Cloudflare): true — треба показати вікно перевірки. */
    val challenge = MutableStateFlow(false)
    private var challengeDone: CompletableDeferred<Boolean>? = null
    private val challengeLock = Mutex()

    fun looksLikeChallenge(code: Int, body: String): Boolean =
        (code == 403 || code == 503 || code == 429) &&
            (body.contains("Just a moment", true) || body.contains("cf-chl", true) ||
                body.contains("challenge-platform", true) || body.contains("Checking your browser", true) ||
                body.contains("Один момент", true))

    /** Викликається вікном перевірки, коли сторінка пройшла перевірку. */
    fun challengePassed() {
        challengeDone?.complete(true)
    }

    private suspend fun solveChallenge(): Boolean = challengeLock.withLock {
        val d = CompletableDeferred<Boolean>()
        challengeDone = d
        challenge.value = true
        val ok = withTimeoutOrNull(90_000) { d.await() } ?: false
        challenge.value = false
        challengeDone = null
        ok
    }

    private fun execute(url: String, form: Map<String, String>?): Pair<Int, String> {
        val b = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.5")
            .header("Referer", "$BASE/")
        if (form != null) {
            val fb = FormBody.Builder()
            form.forEach { (k, v) -> fb.add(k, v) }
            b.post(fb.build())
        }
        client.newCall(b.build()).execute().use { r ->
            return r.code to (r.body?.string().orEmpty())
        }
    }

    suspend fun html(url: String, form: Map<String, String>? = null): String = withContext(Dispatchers.IO) {
        var lastCode = 0
        repeat(3) {
            val (code, body) = try {
                execute(url, form)
            } catch (e: java.io.IOException) {
                throw SiteException("Немає зʼєднання з сайтом. Перевірте інтернет.")
            }
            lastCode = code
            if (code in 200..299 && !looksLikeChallenge(code, body)) return@withContext body
            if (looksLikeChallenge(code, body)) {
                if (!solveChallenge()) throw SiteException("Не вдалося пройти перевірку безпеки сайту.")
            } else if (code == 404) {
                throw SiteException("Сторінку не знайдено.")
            } else {
                throw SiteException("Сайт відповів помилкою $code.")
            }
        }
        throw SiteException("Сайт недоступний (код $lastCode).")
    }
}
