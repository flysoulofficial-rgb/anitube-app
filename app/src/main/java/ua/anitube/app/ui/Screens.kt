package ua.anitube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.anitube.app.AppVM
import ua.anitube.app.data.*

// ---------------- Головна ----------------

@Composable
fun HomeScreen(vm: AppVM) {
    val history by Store.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Header("AniTube") {
            IconButton(onClick = { vm.stack.add(ua.anitube.app.Route.Search) }) {
                Icon(Icons.Default.Search, contentDescription = "Пошук", tint = C.Text)
            }
        }
        CardGrid(
            feed = vm.home, items = vm.home.items, onOpen = { vm.open(it) },
            emptyText = "Поки що порожньо",
            header = {
                if (history.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SectionTitle("Продовжити перегляд")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(history.take(12), key = { it.card.id }) { h -> ContinueTile(h) { vm.open(h.card) } }
                            }
                            SectionTitle("Нове на сайті", Modifier.padding(top = 8.dp))
                        }
                    }
                } else {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Нове на сайті") }
                }
            }
        )
    }
}

@Composable
fun ContinueTile(h: HistoryItem, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(12.dp)).background(C.Surface2)) {
            AsyncImage(h.card.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val frac = if (h.durationMs > 0) (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { frac }, color = C.Accent, trackColor = androidx.compose.ui.graphics.Color(0x66000000),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp)
            )
        }
        Text(h.card.title, color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(h.episodeTitle, color = C.Text2, fontSize = 12.sp, maxLines = 1)
    }
}

// ---------------- Каталог ----------------

@Composable
fun CatalogScreen(vm: AppVM) {
    val f = vm.filters
    var picker by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Header("Каталог") {
            IconButton(onClick = { vm.stack.add(ua.anitube.app.Route.Search) }) {
                Icon(Icons.Default.Search, contentDescription = "Пошук", tint = C.Text)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TYPES) { (label, value) ->
                FilterChip(
                    selected = f.type == value, onClick = { vm.applyFilters(f.copy(type = value)) },
                    label = { Text(label) }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Status.values().toList()) { s ->
                FilterChip(selected = f.status == s, onClick = { vm.applyFilters(f.copy(status = s)) }, label = { Text(s.label) })
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { AssistChip(onClick = { picker = "genre" }, label = { Text(f.genre?.name ?: "Жанр ▾") }) }
            item { AssistChip(onClick = { picker = "year" }, label = { Text(if (f.year.isEmpty()) "Рік ▾" else "${f.year} ▾") }) }
            item { AssistChip(onClick = { picker = "sort" }, label = { Text("${f.sort.label} ▾") }) }
            if (!f.isDefault) item { AssistChip(onClick = { vm.applyFilters(Filters()) }, label = { Text("Скинути") }) }
        }
        Spacer(Modifier.height(6.dp))
        CardGrid(
            feed = vm.catalog,
            items = Repo.sorted(vm.catalog.items, f.sort),
            onOpen = { vm.open(it) },
            emptyText = "За цими фільтрами нічого не знайдено"
        )
    }
    when (picker) {
        "genre" -> PickerDialog(
            "Жанр", listOf("Усі жанри" to (null as Genre?)) + GENRES.map { it.name to (it as Genre?) },
            f.genre, { vm.applyFilters(f.copy(genre = it)) }, { picker = "" }
        )
        "year" -> PickerDialog(
            "Рік випуску", listOf("Усі роки" to "") + YEARS.map { it to it },
            f.year, { vm.applyFilters(f.copy(year = it)) }, { picker = "" }
        )
        "sort" -> PickerDialog(
            "Сортування", Sort.values().map { it.label to it },
            f.sort, { vm.applyFilters(f.copy(sort = it)) }, { picker = "" }
        )
    }
}

// ---------------- Пошук ----------------

@Composable
fun SearchScreen(vm: AppVM, onBack: () -> Unit) {
    var text by remember { mutableStateOf(vm.query) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Назад", color = C.Text) }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                placeholder = { Text("Пошук на сайті...") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.runSearch(text) })
            )
        }
        val feed = vm.searchFeed
        if (feed == null) {
            Text("Введіть назву аніме та натисніть «Пошук»", color = C.Text2, modifier = Modifier.padding(24.dp))
        } else {
            CardGrid(feed, feed.items, { vm.open(it) }, emptyText = "Нічого не знайдено")
        }
    }
}

// ---------------- Бібліотека: улюблене + історія ----------------

@Composable
fun LibraryScreen(vm: AppVM) {
    var sub by remember { mutableStateOf(0) }
    val favs by Store.favorites.collectAsState()
    val hist by Store.history.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Header("Моя бібліотека")
        TabRow(selectedTabIndex = sub, containerColor = C.Bg, contentColor = C.Accent) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Улюблене (${favs.size})") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Історія (${hist.size})") })
        }
        if (sub == 0) {
            if (favs.isEmpty()) {
                Text("Додайте аніме в улюблене на його сторінці — натисніть «В улюблене».", color = C.Text2, modifier = Modifier.padding(24.dp))
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(favs, key = { it.id }) { c -> PosterCard(c, onClick = { vm.open(c) }) }
                }
            }
        } else {
            if (hist.isEmpty()) {
                Text("Історія перегляду порожня.", color = C.Text2, modifier = Modifier.padding(24.dp))
            } else {
                androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        TextButton(onClick = { Store.clearHistory() }) { Text("Очистити історію") }
                    }
                    items(hist, key = { it.card.id }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.Surface)
                                .clickable { vm.open(h.card) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(h.card.poster, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(60.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(h.card.title, color = C.Text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(h.episodeTitle, color = C.Text2, fontSize = 13.sp)
                                val frac = if (h.durationMs > 0) (h.positionMs.toFloat() / h.durationMs).coerceIn(0f, 1f) else 0f
                                LinearProgressIndicator(progress = { frac }, color = C.Accent, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                            }
                            TextButton(onClick = { Store.removeHistory(h.card.id) }) { Text("✕", color = C.Text2) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Налаштування ----------------

@Composable
fun SettingsScreen() {
    val s by Store.settings.collectAsState()
    var picker by remember { mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().verticalScrollCompat()) {
        Header("Налаштування")
        Group("Плеєр")
        SwitchRow("Автоперехід на наступну серію", "Коли серія закінчується, одразу вмикається наступна, плеєр не згортається", s.autoNext) { v -> Store.update { it.copy(autoNext = v) } }
        SwitchRow("Автопропуск опенінгу", "Працює, якщо в серії позначено початок і кінець опенінгу", s.autoSkipIntro) { v -> Store.update { it.copy(autoSkipIntro = v) } }
        ValueRow("Тривалість пропуску", "${s.skipSeconds} с") { picker = "skip" }
        ValueRow("Швидкість за замовчуванням", "×${fmtSpeed(s.speed)}") { picker = "speed" }
        SwitchRow("Продовжувати з місця зупинки", "Відновлювати перегляд серії з того моменту, де зупинились", s.resume) { v -> Store.update { it.copy(resume = v) } }
        SwitchRow("Картинка в картинці", "Мініатюра плеєра при виході з застосунку", s.pip) { v -> Store.update { it.copy(pip = v) } }
        SwitchRow("Не вимикати екран під час перегляду", null, s.keepScreenOn) { v -> Store.update { it.copy(keepScreenOn = v) } }
        Group("Озвучка")
        SwitchRow("Спершу показувати субтитри", "Якщо вимкнено — за замовчуванням обирається озвучення", s.preferSubs) { v -> Store.update { it.copy(preferSubs = v) } }
        Group("Про застосунок")
        ValueRow("Відкрити сайт AniTube", "anitube.in.ua") {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://anitube.in.ua/")))
        }
        Text(
            "Неофіційний застосунок для перегляду аніме з anitube.in.ua. Весь контент належить сайту та його авторам. Версія 1.0.",
            color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(16.dp)
        )
    }
    when (picker) {
        "skip" -> PickerDialog("Тривалість пропуску", listOf(60, 75, 85, 90, 120).map { "$it с" to it }, s.skipSeconds,
            { v -> Store.update { it.copy(skipSeconds = v) } }, { picker = "" })
        "speed" -> PickerDialog("Швидкість", SPEEDS.map { "×${fmtSpeed(it)}" to it }, s.speed,
            { v -> Store.update { it.copy(speed = v) } }, { picker = "" })
    }
}

val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
fun fmtSpeed(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()

@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), color = C.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 16.sp)
            if (sub != null) Text(sub, color = C.Text2, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Accent, fontSize = 15.sp)
    }
}
