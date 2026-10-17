package ua.anitube.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ua.anitube.app.data.Card
import ua.anitube.app.data.Detail
import ua.anitube.app.data.Filters
import ua.anitube.app.data.Loaded
import ua.anitube.app.data.Repo

/** Посторінкове завантаження списку карток. */
class Feed(private val scope: CoroutineScope, private val loader: suspend (Int) -> Loaded) {
    var items by mutableStateOf<List<Card>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var hasMore by mutableStateOf(true); private set
    var firstLoadDone by mutableStateOf(false); private set
    private var next: Int? = 1
    private var job: Job? = null

    fun loadMore() {
        val page = next ?: return
        if (loading) return
        loading = true
        error = null
        job = scope.launch {
            try {
                val r = loader(page)
                items = (items + r.items).distinctBy { it.id }
                next = r.nextPage
                hasMore = r.nextPage != null
                firstLoadDone = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Помилка завантаження"
            } finally {
                loading = false
            }
        }
    }

    fun cancel() { job?.cancel() }
}

sealed class Route {
    data class Title(val card: Card) : Route()
    object Search : Route()
}

class AppVM : ViewModel() {
    var tab by mutableStateOf(0)
    val stack = mutableStateListOf<Route>()

    val home = Feed(viewModelScope) { p -> Repo.catalog(Filters(), p, minItems = 1, maxPages = 1) }

    var filters by mutableStateOf(Filters()); private set
    var catalog by mutableStateOf(newCatalog(Filters())); private set

    var query by mutableStateOf("")
    var searchFeed by mutableStateOf<Feed?>(null); private set

    private val details = HashMap<String, Detail>()

    private fun newCatalog(f: Filters) = Feed(viewModelScope) { p -> Repo.catalog(f, p) }

    fun applyFilters(f: Filters) {
        if (f == filters) return
        catalog.cancel()
        filters = f
        catalog = newCatalog(f)
        catalog.loadMore()
    }

    fun openCatalog(f: Filters) {
        stack.clear()
        tab = 1
        applyFilters(f)
    }

    fun runSearch(q: String) {
        query = q
        searchFeed?.cancel()
        if (q.isBlank()) { searchFeed = null; return }
        val f = Feed(viewModelScope) { p ->
            val page = Repo.search(q.trim(), p)
            Loaded(page.items, if (page.hasNext) p + 1 else null)
        }
        searchFeed = f
        f.loadMore()
    }

    fun open(card: Card) { stack.add(Route.Title(card)) }
    fun back(): Boolean {
        if (stack.isNotEmpty()) { stack.removeAt(stack.lastIndex); return true }
        if (tab != 0) { tab = 0; return true }
        return false
    }

    suspend fun detail(card: Card): Detail {
        details[card.url]?.let { return it }
        val d = Repo.detail(card.url)
        details[card.url] = d
        return d
    }
}
