package ua.anitube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.anitube.app.AppVM
import ua.anitube.app.data.*
import ua.anitube.app.player.PlayerVM

@Composable
fun DetailScreen(card: Card, vm: AppVM, pvm: PlayerVM, onBack: () -> Unit) {
    var detail by remember(card.url) { mutableStateOf<Detail?>(null) }
    var error by remember(card.url) { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(card.url, reload) {
        error = null
        try { detail = vm.detail(card) } catch (e: Exception) { error = e.message ?: "Помилка" }
    }
    val favs by Store.favorites.collectAsState()
    val hist by Store.history.collectAsState()
    val settings by Store.settings.collectAsState()
    val ctx = LocalContext.current
    val isFav = favs.any { it.id == card.id }
    val d = detail

    var dubIdx by remember(d) { mutableIntStateOf(0) }
    var plrIdx by remember(d) { mutableIntStateOf(0) }
    LaunchedEffect(d) {
        if (d != null) {
            val (savedDub, savedPlr) = Store.dubChoice(d.card.id)
            dubIdx = Pick.dub(d, savedDub, Store.lastDub(), settings.preferSubs)
            plrIdx = Pick.player(d.dubs.getOrNull(dubIdx), savedPlr)
        }
    }
    val dub = d?.dubs?.getOrNull(dubIdx)
    val source = dub?.players?.getOrNull(plrIdx)
    val last = hist.firstOrNull { it.card.id == card.id }

    Column(Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(card.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), C.Bg))))
            TextButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                Text("← Назад", color = Color.White, fontSize = 16.sp)
            }
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
                IconButton(onClick = {
                    val i = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${card.title}\n${card.url}")
                    ctx.startActivity(android.content.Intent.createChooser(i, "Поділитись"))
                }) { Icon(Icons.Default.Share, "Поділитись", tint = Color.White) }
                IconButton(onClick = { Store.toggleFavorite(d?.card ?: card) }) {
                    Icon(if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Улюблене",
                        tint = if (isFav) C.Accent else Color.White)
                }
            }
            Text(
                d?.card?.title ?: card.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }

        when {
            error != null -> ErrorBox(error!!) { reload++ }
            d == null -> Loading()
            else -> Body(d, dubIdx, plrIdx, last, vm, pvm,
                onDub = { dubIdx = it; plrIdx = Pick.player(d.dubs.getOrNull(it), "") },
                onPlayer = { plrIdx = it },
                dub = dub, source = source, isFav = isFav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(
    d: Detail, dubIdx: Int, plrIdx: Int, last: HistoryItem?, vm: AppVM, pvm: PlayerVM,
    onDub: (Int) -> Unit, onPlayer: (Int) -> Unit, dub: Dub?, source: PlayerSource?, isFav: Boolean
) {
    val c = d.card
    Column(Modifier.padding(horizontal = 16.dp)) {
        // Мітки
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.type.isNotBlank()) Tag(c.type)
            if (d.year.isNotBlank()) Tag(d.year)
            if (d.episodesText.isNotBlank()) Tag("Серій: ${d.episodesText}")
            if (d.status == "finished" || c.isCompleted) Tag("Завершено", C.Green)
            else if (d.status.isNotBlank() || d.episodesText.isNotBlank()) Tag("Онгоїнг", C.Amber)
        }
        Spacer(Modifier.height(12.dp))

        // Кнопка перегляду
        val canWatch = dub != null && source != null && source.episodes.isNotEmpty()
        val resumeEp = if (last != null) Pick.episode(source, last.episodeTitle, Store.episodeDone(c.id, last.episodeTitle)) else 0
        val epName = source?.episodes?.getOrNull(resumeEp)?.title.orEmpty()
        Button(
            onClick = { if (canWatch) { pvm.start(d, dubIdx, plrIdx, resumeEp) } },
            enabled = canWatch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                if (!canWatch) "Відео поки немає" else if (last != null) "▶  Продовжити · $epName" else "▶  Дивитись · $epName",
                fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
        }
        if (d.addedInfo.isNotBlank()) Text(d.addedInfo, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        // Озвучки
        if (d.dubs.isNotEmpty()) {
            SectionTitle("Озвучка та субтитри", Modifier.padding(top = 10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(d.dubs.size) { i ->
                    FilterChip(selected = i == dubIdx, onClick = { onDub(i) }, label = { Text(d.dubs[i].label) })
                }
            }
            val players = dub?.players.orEmpty()
            if (players.size > 1) {
                Text("Плеєр", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(players.size) { i ->
                        FilterChip(selected = i == plrIdx, onClick = { onPlayer(i) }, label = { Text(players[i].name) })
                    }
                }
            }
            if (source != null) {
                SectionTitle("Серії (${source.episodes.size})", Modifier.padding(top = 10.dp))
                EpisodeGrid(c.id, source.episodes) { i -> pvm.start(d, dubIdx, plrIdx, i) }
            }
        }

        // Інформація
        SectionTitle("Інформація", Modifier.padding(top = 14.dp))
        Info("Жанри") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                c.genres.forEach { g ->
                    val genre = GENRES.firstOrNull { it.name.equals(g, true) }
                    AssistChip(
                        onClick = { if (genre != null) vm.openCatalog(Filters(genre = genre)) },
                        label = { Text(g) }
                    )
                }
            }
        }
        InfoText("Режисер", d.director)
        InfoText("Студія", d.studio)
        InfoText("Переклад", c.translators)
        InfoText("Ролі озвучували", d.voiced)
        if (c.description.isNotBlank()) {
            SectionTitle("Опис", Modifier.padding(top = 10.dp))
            var expanded by remember { mutableStateOf(false) }
            Text(
                c.description, color = C.Text2, fontSize = 14.sp, lineHeight = 21.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                modifier = Modifier.clickable { expanded = !expanded }
            )
            Text(if (expanded) "Згорнути" else "Читати повністю", color = C.Accent, fontSize = 13.sp,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 6.dp))
        }
        Spacer(Modifier.height(32.dp).navigationBarsPadding())
    }
}

@Composable
private fun Tag(text: String, color: Color = C.Text2) {
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.background(C.Surface2, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun Info(label: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = C.Text2, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun InfoText(label: String, value: String) {
    if (value.isBlank()) return
    Info(label) { Text(value, color = C.Text, fontSize = 14.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodeGrid(titleId: Int, episodes: List<Episode>, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        episodes.forEachIndexed { i, ep ->
            val done = Store.episodeDone(titleId, ep.title)
            val pos = Store.episodePos(titleId, ep.title)
            val num = ep.title.replace("серія", "", ignoreCase = true).trim().ifEmpty { "${i + 1}" }
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (done) C.AccentDim else C.Surface2)
                    .clickable { onPick(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(num, color = if (done) C.Accent else if (pos > 0) C.Amber else C.Text, fontWeight = FontWeight.Bold)
            }
        }
    }
}
