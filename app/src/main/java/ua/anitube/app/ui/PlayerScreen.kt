package ua.anitube.app.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import ua.anitube.app.data.Store
import ua.anitube.app.net.Site
import ua.anitube.app.player.PlayerVM

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@Composable
fun PlayerScreen(vm: PlayerVM, inPip: Boolean, onClose: () -> Unit) {
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf("") }
    var web by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }
    var touch by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(0f) }
    val settings by Store.settings.collectAsState()

    // автоприховування панелі керування
    LaunchedEffect(controls, vm.playing, touch, panel) {
        if (controls && vm.playing && panel.isEmpty()) {
            delay(3500)
            controls = false
        }
    }
    LaunchedEffect(toast) { if (toast.isNotEmpty()) { delay(900); toast = "" } }

    val ep = vm.episode
    val title = vm.detail?.card?.title.orEmpty()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    player = vm.player
                }
            },
            update = { it.player = vm.player },
            modifier = Modifier.fillMaxSize()
        )

        // жести: тап — показати/сховати, подвійний тап — перемотка
        val width = LocalConfiguration.current.screenWidthDp
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controls = !controls; touch++ },
                    onDoubleTap = { o ->
                        if (o.x < size.width / 2f) { vm.seekBy(-10_000); toast = "−10 с" }
                        else { vm.seekBy(10_000); toast = "+10 с" }
                    }
                )
            }
        )

        if (toast.isNotEmpty()) {
            Text(toast, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 10.dp))
        }

        if (vm.loading || vm.buffering) {
            CircularProgressIndicator(color = C.Accent, modifier = Modifier.align(Alignment.Center))
        }
        if (vm.loading) {
            Text("Завантаження відео…", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 80.dp))
        }

        // Помилка
        if (vm.error != null && !inPip) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xEE000000), RoundedCornerShape(16.dp)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(vm.error!!, color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.retry() }) { Text("Ще раз") }
                    OutlinedButton(onClick = { panel = "dub" }) { Text("Плеєр / озвучка") }
                    OutlinedButton(onClick = { web = true }) { Text("Веб-плеєр") }
                }
            }
        }

        // Кнопки «Пропустити опенінг» / «Наступна серія»
        if (!inPip && controls) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 110.dp),
                horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (vm.introWindowActive() && vm.error == null && !vm.loading) {
                    PillButton("Пропустити опенінг") { vm.skipOpening() }
                }
                if (vm.outroWindowActive() && vm.error == null) {
                    PillButton("Наступна серія  ›") { vm.next() }
                }
            }
        }

        // Панель керування
        AnimatedVisibility(visible = controls && !inPip, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(90.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(130.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))))

                // верх
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose) { Text("←", color = Color.White, fontSize = 24.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(ep?.title, vm.dub?.label, vm.source?.name).joinToString(" · "),
                            color = Color(0xFFCCCCCC), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { panel = "marks" }) { Text("Позначки", color = Color.White, fontSize = 13.sp) }
                }

                // центр
                Row(
                    Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    RoundBtn(enabled = vm.hasPrev, onClick = { vm.prev() }) { SkipGlyph(false, 24.dp) }
                    RoundBtn(onClick = { vm.seekBy(-10_000) }) { SeekGlyph(false, 30.dp) }
                    RoundBtn(big = true, onClick = { vm.togglePlay() }) {
                        if (vm.playing) PauseGlyph(34.dp) else PlayGlyph(34.dp)
                    }
                    RoundBtn(onClick = { vm.seekBy(10_000) }) { SeekGlyph(true, 30.dp) }
                    RoundBtn(enabled = vm.hasNext, onClick = { vm.next() }) { SkipGlyph(true, 24.dp) }
                }

                // низ
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(if (dragging) dragPos.toLong() else vm.position), color = Color.White, fontSize = 12.sp)
                        Slider(
                            value = if (dragging) dragPos else vm.position.toFloat(),
                            onValueChange = { dragging = true; dragPos = it; touch++ },
                            onValueChangeFinished = { vm.seekTo(dragPos.toLong()); dragging = false },
                            valueRange = 0f..(vm.duration.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = Color(0x55FFFFFF)),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(fmtTime(vm.duration), color = Color.White, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Chip("Озвучка") { panel = "dub" }
                        Chip("Серії") { panel = "episodes" }
                        Chip("×${fmtSpeed(vm.speed)}") { panel = "speed" }
                        Chip("Якість і звук") { panel = "quality" }
                        Chip("Веб-плеєр") { web = true }
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (settings.autoNext) "Автоперехід: так" else "Автоперехід: ні",
                            color = if (settings.autoNext) C.Accent else Color(0xFFCCCCCC), fontSize = 12.sp,
                            modifier = Modifier.clickable { Store.update { it.copy(autoNext = !it.autoNext) } }.padding(8.dp)
                        )
                    }
                }
            }
        }

        // Бокова панель
        if (panel.isNotEmpty() && !inPip) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(Unit) { detectTapGestures { panel = "" } })
            Column(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(340.dp)
                    .background(Color(0xF2171A22)).statusBarsPadding().navigationBarsPadding().padding(12.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (panel) { "dub" -> "Озвучка і плеєр"; "episodes" -> "Серії"; "speed" -> "Швидкість"; "quality" -> "Якість і звук"; else -> "Позначки опенінгу" },
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { panel = "" }) { Text("Закрити") }
                }
                when (panel) {
                    "dub" -> DubPanel(vm) { panel = "" }
                    "episodes" -> EpisodesPanel(vm) { panel = "" }
                    "speed" -> OptionList(SPEEDS.map { "×${fmtSpeed(it)}" to (it == vm.speed) }) { i -> vm.changeSpeed(SPEEDS[i]); panel = "" }
                    "quality" -> QualityPanel(vm)
                    "marks" -> MarksPanel(vm) { msg -> toast = msg; panel = "" }
                }
            }
        }

        if (web) {
            WebPlayer(ep?.url ?: "", onClose = { web = false })
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(C.Accent.copy(alpha = 0.4f)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun RoundBtn(big: Boolean = false, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(if (big) 72.dp else 48.dp).clip(CircleShape)
            .background(if (big) Color(0x88000000) else Color(0x44000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.then(if (enabled) Modifier else Modifier)) { if (enabled) content() else Box(Modifier.size(1.dp)) }
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x55FFFFFF)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun OptionList(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    LazyColumn {
        itemsIndexed(options) { i, (label, sel) ->
            Text(
                (if (sel) "●  " else "○  ") + label,
                color = if (sel) C.Accent else Color.White, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun DubPanel(vm: PlayerVM, close: () -> Unit) {
    val d = vm.detail ?: return
    LazyColumn {
        itemsIndexed(d.dubs) { i, dub ->
            val sel = i == vm.dubIndex
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    (if (sel) "●  " else "○  ") + dub.label,
                    color = if (sel) C.Accent else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable { if (!sel) vm.chooseDub(i); close() }.padding(vertical = 6.dp)
                )
                if (sel && dub.players.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp)) {
                        dub.players.forEachIndexed { pi, p ->
                            Text(
                                p.name, color = if (pi == vm.playerIndex) Color.White else Color(0xFFCCCCCC), fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                                    .background(if (pi == vm.playerIndex) C.Accent else Color(0x33FFFFFF))
                                    .clickable { vm.choosePlayer(pi); close() }.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodesPanel(vm: PlayerVM, close: () -> Unit) {
    val id = vm.detail?.card?.id ?: return
    val eps = vm.episodes
    LazyColumn {
        itemsIndexed(eps) { i, e ->
            val cur = i == vm.epIndex
            val done = Store.episodeDone(id, e.title)
            Text(
                (if (cur) "▶  " else if (done) "✓  " else "    ") + e.title,
                color = if (cur) C.Accent else if (done) C.Text2 else Color.White, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.playEpisode(i); close() }.padding(vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun QualityPanel(vm: PlayerVM) {
    LazyColumn {
        item { Text("Якість відео", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        item {
            Text("Авто", color = Color.White, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.autoQuality() }.padding(vertical = 10.dp))
        }
        itemsIndexed(vm.videoTracks) { _, t ->
            Text((if (t.selected) "●  " else "○  ") + t.label, color = if (t.selected) C.Accent else Color.White, fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().clickable { vm.selectTrack(MC.TRACK_TYPE_VIDEO, t) }.padding(vertical = 10.dp))
        }
        if (vm.audioTracks.size > 1) {
            item { Text("Звукова доріжка", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            itemsIndexed(vm.audioTracks) { _, t ->
                Text((if (t.selected) "●  " else "○  ") + t.label, color = if (t.selected) C.Accent else Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectTrack(MC.TRACK_TYPE_AUDIO, t) }.padding(vertical = 10.dp))
            }
        }
        if (vm.textTracks.isNotEmpty()) {
            item { Text("Субтитри", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            item {
                Text("Вимкнено", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectTrack(MC.TRACK_TYPE_TEXT, null) }.padding(vertical = 10.dp))
            }
            itemsIndexed(vm.textTracks) { _, t ->
                Text((if (t.selected) "●  " else "○  ") + t.label, color = if (t.selected) C.Accent else Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().clickable { vm.selectTrack(MC.TRACK_TYPE_TEXT, t) }.padding(vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun MarksPanel(vm: PlayerVM, done: (String) -> Unit) {
    val m = vm.marks
    fun f(v: Long) = if (v >= 0) fmtTime(v) else "не задано"
    Column {
        Text(
            "Позначте на цьому тайтлі, де починається й закінчується опенінг — кнопка «Пропустити опенінг» " +
                "стрибатиме рівно в кінець, а автопропуск працюватиме для всіх серій.",
            color = C.Text2, fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        MarkRow("Початок опенінгу", f(m.introStart)) { vm.markIntroStart(); done("Початок опенінгу: ${fmtTime(vm.position)}") }
        MarkRow("Кінець опенінгу", f(m.introEnd)) { vm.markIntroEnd(); done("Кінець опенінгу: ${fmtTime(vm.position)}") }
        MarkRow("Початок ендінгу", f(m.outroStart)) { vm.markOutroStart(); done("Початок ендінгу: ${fmtTime(vm.position)}") }
        Text("Скинути позначки", color = C.Accent, fontSize = 15.sp,
            modifier = Modifier.clickable { vm.clearMarks(); done("Позначки скинуто") }.padding(vertical = 14.dp))
    }
}

@Composable
private fun MarkRow(label: String, value: String, onSet: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onSet).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 15.sp)
            Text("Зараз: $value", color = C.Text2, fontSize = 12.sp)
        }
        Text("Поставити тут", color = C.Accent, fontSize = 13.sp)
    }
}

/** Запасний варіант: сторінка стороннього плеєра у вбудованому браузері. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPlayer(url: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = Site.UA
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(url, mapOf("Referer" to "${Site.BASE}/"))
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
            Text("← Закрити веб-плеєр", color = Color.White)
        }
    }
}
