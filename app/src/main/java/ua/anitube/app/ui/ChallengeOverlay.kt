package ua.anitube.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
import ua.anitube.app.net.Site

/**
 * Вікно перевірки безпеки сайту. Це справжній браузерний рушій (WebView): він проходить
 * перевірку так само, як звичайний відвідувач, а отримані cookie використовує решта застосунку.
 * Якщо потрібна дія користувача (галочка «Я не робот») — її видно тут.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallengeOverlay() {
    val show by Site.challenge.collectAsState()
    if (!show) return
    Dialog(onDismissRequest = {}) {
        Column(
            Modifier.fillMaxWidth().background(C.Surface, RoundedCornerShape(16.dp)).padding(12.dp)
        ) {
            Text("Перевірка безпеки сайту…", color = C.Text, fontSize = 16.sp)
            Text("Зачекайте кілька секунд. Якщо зʼявиться галочка — натисніть її.", color = C.Text2, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            var web by remember { mutableStateOf<WebView?>(null) }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = Site.UA
                        webViewClient = WebViewClient()
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        loadUrl(Site.BASE + "/")
                        web = this
                    }
                },
                onRelease = { it.destroy() },
                modifier = Modifier.fillMaxWidth().height(380.dp).align(Alignment.CenterHorizontally)
            )
            LaunchedEffect(web) {
                val w = web ?: return@LaunchedEffect
                while (true) {
                    delay(1000)
                    val cookies = CookieManager.getInstance().getCookie(Site.BASE).orEmpty()
                    w.evaluateJavascript("document.title") { raw ->
                        val t = raw.orEmpty().trim('"')
                        val challenged = t.contains("moment", true) || t.contains("момент", true) ||
                            t.contains("Attention", true) || t.isEmpty() || t == "null"
                        if (cookies.contains("cf_clearance") || !challenged) Site.challengePassed()
                    }
                }
            }
        }
    }
}
