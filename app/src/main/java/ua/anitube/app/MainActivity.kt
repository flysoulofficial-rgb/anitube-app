package ua.anitube.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ua.anitube.app.data.Card
import ua.anitube.app.data.Parser
import ua.anitube.app.data.Store
import ua.anitube.app.player.PlayerVM
import ua.anitube.app.ui.*

class MainActivity : ComponentActivity() {
    private val pvm: PlayerVM by viewModels()
    private val pip = mutableStateOf(false)
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink.value = intent?.data?.toString()
        setContent {
            AniTheme {
                Root(pvm, pip.value, deepLink.value) { deepLink.value = null }
                ChallengeOverlay()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink.value = intent.data?.toString()
    }

    override fun onUserLeaveHint() {
        if (pvm.active && pvm.playing && Store.settings.value.pip) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // у PiP відтворення триває; інакше ставимо на паузу і зберігаємо місце
        if (!isInPictureInPictureMode && pvm.active) {
            pvm.player.pause()
            pvm.saveNow()
        }
    }
}

@Composable
fun Root(pvm: PlayerVM, inPip: Boolean, deepLink: String?, consumeLink: () -> Unit) {
    val vm: AppVM = androidx.lifecycle.viewmodel.compose.viewModel()
    val act = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val settings by Store.settings.collectAsState()

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            val id = Parser.idFromUrl(deepLink)
            if (id != 0) vm.open(Card(id, deepLink, "", ""))
            consumeLink()
        }
    }

    // Повний екран і горизонтальна орієнтація під час перегляду
    LaunchedEffect(pvm.active) {
        val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
        if (pvm.active) {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ctrl.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(pvm.active, settings.keepScreenOn) {
        if (pvm.active && settings.keepScreenOn) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    BackHandler(enabled = pvm.active) { pvm.close() }
    BackHandler(enabled = !pvm.active && (vm.stack.isNotEmpty() || vm.tab != 0)) { vm.back() }

    Box(Modifier.fillMaxSize().background(ua.anitube.app.ui.C.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (val top = vm.stack.lastOrNull()) {
                    is Route.Title -> key(top.card.url) { DetailScreen(top.card, vm, pvm) { vm.back() } }
                    is Route.Search -> SearchScreen(vm) { vm.back() }
                    null -> when (vm.tab) {
                        0 -> HomeScreen(vm)
                        1 -> CatalogScreen(vm)
                        2 -> LibraryScreen(vm)
                        else -> SettingsScreen()
                    }
                }
            }
            if (vm.stack.isEmpty() && !pvm.active) BottomBar(vm.tab) { vm.tab = it }
        }
        if (pvm.active) PlayerScreen(pvm, inPip) { pvm.close() }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ua.anitube.app.ui.C.Surface).navigationBarsPadding().height(60.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val items = listOf("Головна", "Каталог", "Бібліотека", "Налаштування")
        items.forEachIndexed { i, label ->
            val tint = if (i == selected) ua.anitube.app.ui.C.Accent else ua.anitube.app.ui.C.Text2
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                when (i) {
                    0 -> Icon(Icons.Default.Home, label, tint = tint, modifier = Modifier.size(24.dp))
                    1 -> GridGlyph(24.dp, tint)
                    2 -> Icon(Icons.Default.Favorite, label, tint = tint, modifier = Modifier.size(24.dp))
                    else -> Icon(Icons.Default.Settings, label, tint = tint, modifier = Modifier.size(24.dp))
                }
                Text(label, color = tint, fontSize = 11.sp)
            }
        }
    }
}
