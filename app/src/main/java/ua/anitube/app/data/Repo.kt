package ua.anitube.app.data

import ua.anitube.app.net.Site
import java.net.URLEncoder

enum class Status(val label: String) { ALL("Усі"), DONE("Завершені"), ONGOING("Онгоїнг") }

enum class Sort(val label: String) {
    NEW("Новизна"), TITLE_AZ("Назва А–Я"), TITLE_ZA("Назва Я–А"), EPISODES("Кількість серій")
}

val TYPES = listOf("Усі" to "", "Серіали (ТБ)" to "ТБ", "Фільми" to "Повнометражне", "ONA" to "ONA")

val GENRES = listOf(
    Genre("dystopia", "Антиутопія"), Genre("fight", "Бойове мистецтво"), Genre("action", "Бойовик"),
    Genre("routine", "Буденність"), Genre("war", "Війна"), Genre("gothic", "Готика"),
    Genre("kids", "Для дітей"), Genre("detective", "Детектив"), Genre("josei", "Джьосей"),
    Genre("drama", "Драма"), Genre("ero", "Еротика"), Genre("echi", "Еччі"), Genre("horror", "Жахи"),
    Genre("zombie", "Зомбі"), Genre("isekai", "Ісекай"), Genre("story", "Історія"),
    Genre("fairy", "Казка"), Genre("comedy", "Комедія"), Genre("cyberpunk", "Кіберпанк"),
    Genre("komodo", "Комодо"), Genre("majokko", "Махо-шьоджьо"), Genre("mecha", "Меха"),
    Genre("mystic", "Містика"), Genre("music", "Музичний"), Genre("supernatural", "Надприродне"),
    Genre("parodya", "Пародія"), Genre("prigodi", "Пригоди"),
    Genre("post-apocalyptic", "Постапокаліптика"), Genre("psiho", "Психологія"),
    Genre("romance", "Романтика"), Genre("seinen", "Сейнен"), Genre("sport", "Спорт"),
    Genre("triller", "Триллер"), Genre("fantaskyka", "Фантастика"), Genre("fantasy", "Фентезі"),
    Genre("shoujo", "Шьоджьо"), Genre("boyslove", "Шьоджьо-аї"), Genre("girlslove", "Шьонен-аї"),
    Genre("school-life", "Школа"), Genre("shounen", "Шьонен")
)

val YEARS: List<String> = (2026 downTo 1976).map { it.toString() }

data class Filters(
    val type: String = "",
    val status: Status = Status.ALL,
    val genre: Genre? = null,
    val year: String = "",
    val sort: Sort = Sort.NEW
) {
    val isDefault: Boolean get() = this == Filters()
}

data class Loaded(val items: List<Card>, val nextPage: Int?)

object Repo {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** Один фільтр сайт вміє сам (рік / жанр / тип), решту застосовуємо в застосунку. */
    fun listUrl(f: Filters, page: Int): String {
        val base = when {
            f.year.isNotEmpty() -> "${Site.BASE}/xfsearch/year/${f.year}/"
            f.genre != null -> "${Site.BASE}/anime/${f.genre.slug}/"
            f.type.isNotEmpty() -> "${Site.BASE}/xfsearch/type/${enc(f.type)}/"
            else -> "${Site.BASE}/anime/"
        }
        return if (page > 1) "${base}page/$page/" else base
    }

    fun matches(c: Card, f: Filters, serverHandled: String): Boolean {
        if (f.type.isNotEmpty() && serverHandled != "type" && !c.type.equals(f.type, true)) return false
        if (f.genre != null && serverHandled != "genre" && c.genres.none { it.equals(f.genre.name, true) }) return false
        when (f.status) {
            Status.DONE -> if (!c.isCompleted) return false
            Status.ONGOING -> if (c.isCompleted) return false
            Status.ALL -> {}
        }
        return true
    }

    private fun serverHandled(f: Filters) = when {
        f.year.isNotEmpty() -> "year"
        f.genre != null -> "genre"
        f.type.isNotEmpty() -> "type"
        else -> ""
    }

    fun sorted(items: List<Card>, sort: Sort): List<Card> = when (sort) {
        Sort.NEW -> items
        Sort.TITLE_AZ -> items.sortedBy { it.title.lowercase() }
        Sort.TITLE_ZA -> items.sortedByDescending { it.title.lowercase() }
        Sort.EPISODES -> items.sortedByDescending { it.total }
    }

    /** Завантажує сторінки, поки не набереться [minItems] підходящих карток (максимум [maxPages] запитів). */
    suspend fun catalog(f: Filters, startPage: Int, minItems: Int = 12, maxPages: Int = 6): Loaded {
        val handled = serverHandled(f)
        val out = ArrayList<Card>()
        var page = startPage
        var fetched = 0
        var next: Int? = startPage
        while (next != null && fetched < maxPages && out.size < minItems) {
            val p = Parser.parseListing(Site.html(listUrl(f, page)))
            fetched++
            out += p.items.filter { matches(it, f, handled) }
            next = if (p.hasNext && p.items.isNotEmpty()) page + 1 else null
            page++
        }
        return Loaded(out, next)
    }

    suspend fun search(query: String, page: Int = 1): Page {
        val form = mapOf(
            "do" to "search", "subaction" to "search", "story" to query,
            "search_start" to page.toString(), "full_search" to "0",
            "result_from" to ((page - 1) * 11 + 1).toString()
        )
        return Parser.parseListing(Site.html("${Site.BASE}/index.php?do=search", form))
    }

    suspend fun detail(url: String): Detail =
        Parser.parseDetail(Site.html(url), url) ?: throw ua.anitube.app.net.SiteException("Не вдалося розібрати сторінку аніме.")
}
