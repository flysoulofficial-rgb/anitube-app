package ua.anitube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.anitube.app.Feed
import ua.anitube.app.data.Card

@Composable
fun PosterCard(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.7f)
                .clip(RoundedCornerShape(12.dp)).background(C.Surface2)
        ) {
            AsyncImage(
                model = card.poster, contentDescription = card.title,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
            if (card.label.isNotBlank()) {
                Text(
                    card.label, color = Color_White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(C.Accent, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (card.progress.isNotBlank()) {
                Text(
                    card.progress, color = Color_White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(androidx.compose.ui.graphics.Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            card.title, color = C.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
        )
    }
}

val Color_White = androidx.compose.ui.graphics.Color.White

@Composable
fun Header(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = C.Text2, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRetry) { Text("Спробувати ще") }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = C.Accent)
    }
}

/** Сітка карток з безкінечним прокручуванням. */
@Composable
fun CardGrid(
    feed: Feed,
    items: List<Card>,
    onOpen: (Card) -> Unit,
    emptyText: String = "Нічого не знайдено",
    header: (LazyGridScope.() -> Unit)? = null
) {
    val state = rememberLazyGridState()
    LaunchedEffect(feed, state) {
        snapshotFlow {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 8
        }.collect { near -> if (near) feed.loadMore() }
    }
    LaunchedEffect(feed) { if (!feed.firstLoadDone && !feed.loading) feed.loadMore() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        state = state,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        header?.invoke(this)
        items(items, key = { it.id }) { c -> PosterCard(c, onClick = { onOpen(c) }) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                feed.error != null -> ErrorBox(feed.error!!) { feed.loadMore() }
                feed.loading -> Loading()
                feed.firstLoadDone && items.isEmpty() ->
                    Text(emptyText, color = C.Text2, modifier = Modifier.padding(24.dp))
                else -> Spacer(Modifier.height(1.dp))
            }
        }
    }
}

@Composable
fun <T> PickerDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Surface,
        title = { Text(title, color = C.Text) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(options.size) { i ->
                    val (label, value) = options[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(value); onDismiss() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onPick(value); onDismiss() })
                        Text(label, color = C.Text, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрити") } }
    )
}
