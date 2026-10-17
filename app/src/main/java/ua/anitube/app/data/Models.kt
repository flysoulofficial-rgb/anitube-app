package ua.anitube.app.data

/** Картка тайтла зі списку (каталог, пошук, головна). */
data class Card(
    val id: Int,
    val url: String,
    val title: String,
    val poster: String,
    val label: String = "",
    val progress: String = "",
    val genres: List<String> = emptyList(),
    val type: String = "",
    val translators: String = "",
    val description: String = ""
) {
    val done: Int get() = progressParts().first
    val total: Int get() = progressParts().second

    private fun progressParts(): Pair<Int, Int> {
        val m = Regex("(\\d+)\\s*з\\s*(\\d+|\\?)").find(progress) ?: return 0 to 0
        return (m.groupValues[1].toIntOrNull() ?: 0) to (m.groupValues[2].toIntOrNull() ?: 0)
    }

    /** Завершене: показано всі серії. Якщо розмітки немає — вважаємо фільм завершеним. */
    val isCompleted: Boolean
        get() = if (total > 0) done >= total else isFilm

    val isFilm: Boolean get() = type.contains("Повнометраж", ignoreCase = true) || type.contains("Фільм", ignoreCase = true)
}

data class Genre(val slug: String, val name: String)

data class Episode(val title: String, val url: String) {
    /** Номер серії для сортування та переходу до наступної. */
    val number: Int get() = Regex("\\d+").find(title)?.value?.toIntOrNull() ?: Int.MAX_VALUE
}

/** Один плеєр (Moon / Fenix / Ashdi…) всередині озвучки або субтитрів. */
data class PlayerSource(val key: String, val name: String, val episodes: List<Episode>)

/** Команда озвучення або субтитрів. */
data class Dub(val category: String, val name: String, val players: List<PlayerSource>) {
    val isSubs: Boolean get() = category.contains("СУБ", ignoreCase = true)
    val label: String get() = if (name.isBlank()) category else "$name · ${category.lowercase().replaceFirstChar { it.uppercase() }}"
}

data class Detail(
    val card: Card,
    val year: String,
    val episodesText: String,
    val director: String,
    val studio: String,
    val voiced: String,
    val status: String,
    val addedInfo: String,
    val dubs: List<Dub>
)

data class Page(val items: List<Card>, val hasNext: Boolean)

enum class PlayerKind { ASHDI, FENIX, MOON, OTHER;
    companion object {
        fun of(url: String): PlayerKind = when {
            "ashdi" in url -> ASHDI
            "fenixplay" in url -> FENIX
            "moonanime" in url -> MOON
            else -> OTHER
        }
    }
}
