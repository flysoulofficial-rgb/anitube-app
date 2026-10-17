package ua.anitube.app.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Розбір сторінок anitube.in.ua (двигун DataLife Engine). Чиста логіка — легко тестується. */
object Parser {
    const val BASE = "https://anitube.in.ua"

    fun idFromUrl(url: String): Int =
        Regex("/(\\d+)-[^/]*\\.html").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun abs(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> BASE + url
        else -> "$BASE/$url"
    }

    fun parseListing(html: String): Page {
        val doc = Jsoup.parse(html, BASE)
        val cards = doc.select("article.short").mapNotNull { a ->
            val link = a.selectFirst("a.short-title[href]") ?: return@mapNotNull null
            val url = abs(link.attr("href"))
            val id = idFromUrl(url)
            if (id == 0) return@mapNotNull null
            val title = (a.selectFirst("a.short-title h2")?.text()
                ?: link.attr("title").removePrefix("Перегляд:")).trim()
            val img = a.selectFirst(".short-img img")
            val poster = img?.attr("src").orEmpty().ifBlank { img?.attr("data-src").orEmpty() }
            val lines = a.select(".short-desc .sd-line")
            fun lineOf(label: String): String = lines.firstOrNull {
                it.selectFirst("span")?.text()?.trim()?.startsWith(label, ignoreCase = true) == true
            }?.let { cleanLine(it) }.orEmpty()
            val genres = (a.selectFirst(".short-cat")?.text().orEmpty().ifBlank { lineOf("Жанр") })
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            Card(
                id = id,
                url = url,
                title = title,
                poster = abs(poster),
                label = a.selectFirst(".short-label")?.text()?.trim().orEmpty(),
                progress = a.selectFirst(".up-series")?.text()?.trim().orEmpty(),
                genres = genres,
                type = lineOf("Тип"),
                translators = lineOf("Переклад"),
                description = lines.firstOrNull { it.hasClass("sd-text") }?.let { cleanLine(it) }.orEmpty()
            )
        }
        val hasNext = doc.selectFirst("#pagi-load a[href]") != null ||
            doc.selectFirst(".pagi-navi .navigation a:has(i.fa-angle-right)") != null
        return Page(cards.distinctBy { it.id }, hasNext)
    }

    private fun cleanLine(e: Element): String {
        val c = e.clone()
        c.select("span").first()?.remove()
        return c.text().trim()
    }

    fun parseDetail(html: String, pageUrl: String): Detail? {
        val doc = Jsoup.parse(html, BASE)
        val full = doc.selectFirst("article.full") ?: return null
        val title = full.selectFirst("h1")?.text()?.trim().orEmpty()
        if (title.isEmpty()) return null
        fun info(label: String): String = full.select("ul.full_list-col li").firstOrNull {
            it.selectFirst("span")?.text()?.trim()?.startsWith(label, ignoreCase = true) == true
        }?.let { cleanLine(it) }.orEmpty()
        val poster = full.selectFirst(".full_poster img")?.attr("src").orEmpty()
            .ifBlank { doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
        val genres = full.select("ul.full_list-col li").firstOrNull {
            it.selectFirst("span")?.text()?.trim()?.startsWith("Жанр", ignoreCase = true) == true
        }?.select("a")?.map { it.text().trim() }.orEmpty()
        val desc = full.selectFirst(".full_desc")?.let {
            val c = it.clone(); c.select(".full_blc-title").remove(); c.text().trim()
        }.orEmpty()
        val status = doc.selectFirst("#anime-status")
        val episodesText = info("Кількість епізодів").ifBlank { status?.attr("data-episode").orEmpty() }
        val card = Card(
            id = idFromUrl(pageUrl).let { if (it == 0) idFromUrl(doc.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()) else it },
            url = pageUrl,
            title = title,
            poster = abs(poster),
            progress = episodesText,
            genres = genres,
            type = info("Тип"),
            translators = info("Переклад"),
            description = desc
        )
        return Detail(
            card = card,
            year = info("Рік"),
            episodesText = episodesText,
            director = info("Режис"),
            studio = info("Студія"),
            voiced = info("Ролі озвучували"),
            status = status?.attr("data-status").orEmpty(),
            addedInfo = full.selectFirst(".to_view")?.text()?.trim().orEmpty(),
            dubs = parsePlaylist(doc)
        )
    }

    /** Вкладки: категорія (0_1) → команда (0_1_0) → плеєр (0_1_0_1); серії — li[data-file]. */
    fun parsePlaylist(doc: org.jsoup.nodes.Document): List<Dub> {
        val root = doc.selectFirst("#cinema, .playlists-ajax") ?: return emptyList()
        val names = HashMap<String, String>()
        root.select(".playlists-lists ul li[data-id]:not([data-file])").forEach {
            names.putIfAbsent(it.attr("data-id"), it.text().trim())
        }
        val groups = LinkedHashMap<String, MutableList<Episode>>()
        root.select("li[data-file][data-id]").forEach { li ->
            val file = li.attr("data-file").trim()
            if (file.isEmpty()) return@forEach
            val title = li.ownText().trim().ifEmpty { li.text().trim() }
            groups.getOrPut(li.attr("data-id")) { ArrayList() }.add(Episode(title, file))
        }
        // dub key -> (category, name) ; зберігаємо порядок появи
        val dubs = LinkedHashMap<String, Triple<String, String, MutableList<PlayerSource>>>()
        for ((id, eps) in groups) {
            val parts = id.split('_')
            val catKey = if (parts.size >= 2) parts.take(2).joinToString("_") else id
            val dubKey = if (parts.size >= 3) parts.take(3).joinToString("_") else catKey
            val cat = names[catKey].orEmpty()
            val dubName = if (parts.size >= 3) names[dubKey].orEmpty() else ""
            val playerName = (if (parts.size >= 4) names[id].orEmpty() else "")
                .replace("ПЛЕЄР", "", ignoreCase = true).trim()
            val kind = PlayerKind.of(eps.first().url)
            val shown = playerName.ifBlank { kind.name.lowercase().replaceFirstChar { it.uppercase() } }
            val entry = dubs.getOrPut(dubKey) { Triple(cat, dubName, ArrayList()) }
            entry.third.add(PlayerSource(id, shown, eps.sortedBy { it.number }))
        }
        return dubs.map { (_, v) -> Dub(v.first, v.second, v.third) }
    }
}
