package ua.anitube.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class HistoryItem(
    val card: Card,
    val episodeTitle: String,
    val episodeUrl: String,
    val dubIndex: Int,
    val playerKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val time: Long
)

data class AppSettings(
    val autoNext: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val skipSeconds: Int = 85,
    val speed: Float = 1f,
    val pip: Boolean = true,
    val resume: Boolean = true,
    val keepScreenOn: Boolean = true,
    val preferSubs: Boolean = false
)

/** Intro/outro marks per title, in ms (-1 = not set). */
data class Marks(val introStart: Long = -1, val introEnd: Long = -1, val outroStart: Long = -1)

object Store {
    private lateinit var sp: SharedPreferences

    private val _favorites = MutableStateFlow<List<Card>>(emptyList())
    val favorites: StateFlow<List<Card>> = _favorites
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("anitube", Context.MODE_PRIVATE)
        _favorites.value = readCards(sp.getString("fav", "[]") ?: "[]")
        _history.value = readHistory(sp.getString("hist", "[]") ?: "[]")
        _settings.value = AppSettings(
            autoNext = sp.getBoolean("autoNext", true),
            autoSkipIntro = sp.getBoolean("autoSkipIntro", false),
            skipSeconds = sp.getInt("skipSeconds", 85),
            speed = sp.getFloat("speed", 1f),
            pip = sp.getBoolean("pip", true),
            resume = sp.getBoolean("resume", true),
            keepScreenOn = sp.getBoolean("keepScreenOn", true),
            preferSubs = sp.getBoolean("preferSubs", false)
        )
    }

    // ---------- JSON ----------
    private fun cardJson(c: Card) = JSONObject()
        .put("id", c.id).put("url", c.url).put("title", c.title).put("poster", c.poster)
        .put("label", c.label).put("progress", c.progress).put("type", c.type)
        .put("genres", JSONArray(c.genres)).put("tr", c.translators)

    private fun cardFrom(o: JSONObject): Card {
        val g = o.optJSONArray("genres")
        return Card(
            id = o.optInt("id"), url = o.optString("url"), title = o.optString("title"),
            poster = o.optString("poster"), label = o.optString("label"),
            progress = o.optString("progress"), type = o.optString("type"),
            genres = (0 until (g?.length() ?: 0)).map { g!!.getString(it) },
            translators = o.optString("tr")
        )
    }

    private fun readCards(s: String): List<Card> = try {
        val a = JSONArray(s); (0 until a.length()).map { cardFrom(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    private fun readHistory(s: String): List<HistoryItem> = try {
        val a = JSONArray(s)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            HistoryItem(
                cardFrom(o.getJSONObject("card")), o.optString("et"), o.optString("eu"),
                o.optInt("di"), o.optString("pk"), o.optLong("pos"), o.optLong("dur"), o.optLong("t")
            )
        }
    } catch (e: Exception) { emptyList() }

    // ---------- Улюблене ----------
    fun isFavorite(id: Int) = _favorites.value.any { it.id == id }

    fun toggleFavorite(c: Card) {
        val cur = _favorites.value
        _favorites.value = if (cur.any { it.id == c.id }) cur.filterNot { it.id == c.id } else listOf(c) + cur
        sp.edit().putString("fav", JSONArray(_favorites.value.map { cardJson(it) }).toString()).apply()
    }

    // ---------- Історія ----------
    fun saveProgress(item: HistoryItem) {
        val list = listOf(item) + _history.value.filterNot { it.card.id == item.card.id }
        _history.value = list.take(150)
        val arr = JSONArray()
        _history.value.forEach {
            arr.put(
                JSONObject().put("card", cardJson(it.card)).put("et", it.episodeTitle)
                    .put("eu", it.episodeUrl).put("di", it.dubIndex).put("pk", it.playerKey)
                    .put("pos", it.positionMs).put("dur", it.durationMs).put("t", it.time)
            )
        }
        sp.edit().putString("hist", arr.toString()).apply()
    }

    fun removeHistory(id: Int) {
        _history.value = _history.value.filterNot { it.card.id == id }
        val arr = JSONArray()
        _history.value.forEach {
            arr.put(
                JSONObject().put("card", cardJson(it.card)).put("et", it.episodeTitle)
                    .put("eu", it.episodeUrl).put("di", it.dubIndex).put("pk", it.playerKey)
                    .put("pos", it.positionMs).put("dur", it.durationMs).put("t", it.time)
            )
        }
        sp.edit().putString("hist", arr.toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        sp.edit().putString("hist", "[]").apply()
    }

    fun historyOf(id: Int): HistoryItem? = _history.value.firstOrNull { it.card.id == id }

    // ---------- Позиція в окремій серії ----------
    fun episodePos(titleId: Int, episodeTitle: String): Long = sp.getLong("pos_${titleId}_$episodeTitle", 0L)
    fun episodeDone(titleId: Int, episodeTitle: String): Boolean = sp.getBoolean("done_${titleId}_$episodeTitle", false)

    fun setEpisodePos(titleId: Int, episodeTitle: String, pos: Long, done: Boolean) {
        sp.edit().putLong("pos_${titleId}_$episodeTitle", if (done) 0L else pos)
            .apply { if (done) putBoolean("done_${titleId}_$episodeTitle", true) }.apply()
    }

    // ---------- Позначки опенінгу/ендінгу ----------
    fun marks(titleId: Int) = Marks(
        sp.getLong("m_is_$titleId", -1), sp.getLong("m_ie_$titleId", -1), sp.getLong("m_os_$titleId", -1)
    )

    fun setMarks(titleId: Int, m: Marks) {
        sp.edit().putLong("m_is_$titleId", m.introStart).putLong("m_ie_$titleId", m.introEnd)
            .putLong("m_os_$titleId", m.outroStart).apply()
    }

    // ---------- Налаштування ----------
    fun update(block: (AppSettings) -> AppSettings) {
        val s = block(_settings.value)
        _settings.value = s
        sp.edit().putBoolean("autoNext", s.autoNext).putBoolean("autoSkipIntro", s.autoSkipIntro)
            .putInt("skipSeconds", s.skipSeconds).putFloat("speed", s.speed).putBoolean("pip", s.pip)
            .putBoolean("resume", s.resume).putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("preferSubs", s.preferSubs).apply()
    }

    // ---------- Вибір озвучки для тайтла ----------
    fun dubChoice(titleId: Int): Pair<String, String> =
        (sp.getString("dub_$titleId", "") ?: "") to (sp.getString("plr_$titleId", "") ?: "")

    fun setDubChoice(titleId: Int, dubLabel: String, player: String) {
        sp.edit().putString("dub_$titleId", dubLabel).putString("plr_$titleId", player).apply()
    }
    fun lastDub(): String = sp.getString("last_dub", "") ?: ""
    fun setLastDub(label: String) { sp.edit().putString("last_dub", label).apply() }
}
