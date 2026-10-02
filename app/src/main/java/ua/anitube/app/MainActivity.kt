package ua.anitube.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val base = "https://anitube.in.ua"
    private val hosts = listOf("anitube.in.ua", "manga.in.ua")

    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: ProgressBar
    private lateinit var errorView: View
    private lateinit var content: View
    private lateinit var fullscreen: FrameLayout
    private lateinit var nav: BottomNavigationView
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var failed = false
    private var syncing = false

    private val tabs by lazy {
        mapOf(
            R.id.nav_home to "$base/",
            R.id.nav_anime to "$base/anime/",
            R.id.nav_collections to "$base/collections/",
            R.id.nav_random to "$base/?do=random_anime",
            R.id.nav_news to "$base/news/"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web); swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress); errorView = findViewById(R.id.error)
        content = findViewById(R.id.content); fullscreen = findViewById(R.id.fullscreen)
        nav = findViewById(R.id.bottom_nav)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val item = toolbar.menu.findItem(R.id.action_search)
        val sv = item.actionView as SearchView
        sv.queryHint = getString(R.string.search_hint)
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String): Boolean {
                web.loadUrl("$base/index.php?do=search&subaction=search&story=" + Uri.encode(q))
                sv.clearFocus(); item.collapseActionView(); return true
            }
            override fun onQueryTextChange(newText: String) = false
        })

        nav.setOnItemSelectedListener { m ->
            if (!syncing) tabs[m.itemId]?.let { web.loadUrl(it) }
            true
        }

        swipe.setColorSchemeResources(R.color.brand)
        swipe.setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        swipe.setOnRefreshListener { web.reload() }
        findViewById<Button>(R.id.retry).setOnClickListener {
            failed = false; errorView.visibility = View.GONE; web.reload()
        }

        setupWebView()
        web.loadUrl(startUrl(intent))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> web.webChromeClient?.onHideCustomView()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        web.loadUrl(startUrl(intent))
    }

    private fun isOurs(u: Uri) = hosts.any { u.host == it || u.host?.endsWith(".$it") == true }

    private fun startUrl(i: Intent?): String {
        val d = i?.data
        return if (d != null && isOurs(d)) d.toString() else base
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // "AniTubeApp" lets the site detect the app; "; wv" removed so video players don't block us
            userAgentString = userAgentString.replace("; wv", "") + " AniTubeApp/1.0"
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (!r.isForMainFrame) return false
                val u = r.url
                if ((u.scheme == "http" || u.scheme == "https") && isOurs(u)) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: Exception) {}
                return true
            }
            override fun onPageStarted(v: WebView, url: String, f: Bitmap?) {
                failed = false; progress.visibility = View.VISIBLE
            }
            override fun onPageFinished(v: WebView, url: String) {
                swipe.isRefreshing = false; progress.visibility = View.GONE
                errorView.visibility = if (failed) View.VISIBLE else View.GONE
                syncTab(url)
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) { failed = true; errorView.visibility = View.VISIBLE }
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) { progress.progress = p }
            override fun onShowCustomView(view: View, cb: CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view; customCb = cb
                fullscreen.addView(view); fullscreen.visibility = View.VISIBLE
                content.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                bars().apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            override fun onHideCustomView() {
                val v = customView ?: return
                fullscreen.removeView(v); fullscreen.visibility = View.GONE
                content.visibility = View.VISIBLE
                customCb?.onCustomViewHidden(); customView = null; customCb = null
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                bars().show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun bars() = WindowCompat.getInsetsController(window, window.decorView)

    private fun syncTab(url: String) {
        val path = Uri.parse(url).path ?: "/"
        val id = when {
            path == "/" || path.isEmpty() -> R.id.nav_home
            path.startsWith("/anime") -> R.id.nav_anime
            path.startsWith("/collections") -> R.id.nav_collections
            path.startsWith("/news") -> R.id.nav_news
            else -> null
        } ?: return
        syncing = true; nav.selectedItemId = id; syncing = false
    }

    override fun onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web.onResume() }
}
