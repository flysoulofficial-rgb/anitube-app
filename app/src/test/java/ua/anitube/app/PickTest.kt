package ua.anitube.app

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.anitube.app.data.*

class PickTest {
    private fun ep(n: Int) = Episode("$n серія", "u$n")
    private val d = Detail(
        Card(1, "", "t", ""), "", "", "", "", "", "", "",
        listOf(
            Dub("СУБТИТРИ", "A", listOf(PlayerSource("0_0_0_0", "Moon", listOf(ep(1), ep(2))))),
            Dub("ОЗВУЧЕННЯ", "B", listOf(
                PlayerSource("0_1_0_0", "Fenix", listOf(ep(1), ep(2), ep(3))),
                PlayerSource("0_1_0_1", "Ashdi", listOf(ep(1), ep(2), ep(3)))
            ))
        )
    )

    @Test fun dubDefaults() {
        assertEquals(1, Pick.dub(d, "", "", false))
        assertEquals(0, Pick.dub(d, "", "", true))
        assertEquals(0, Pick.dub(d, d.dubs[0].label, "", false))
        assertEquals(1, Pick.dub(d, "нема", d.dubs[1].label, true))
    }

    @Test fun playerAndEpisode() {
        assertEquals(1, Pick.player(d.dubs[1], "ashdi"))
        assertEquals(0, Pick.player(d.dubs[1], "x"))
        val s = d.dubs[1].players[0]
        assertEquals(1, Pick.episode(s, "2 серія", false))
        assertEquals(2, Pick.episode(s, "2 серія", true))
        assertEquals(2, Pick.episode(s, "3 серія", true))
        assertEquals(0, Pick.episode(s, null, false))
    }

    @Test fun filters() {
        val f = Filters(status = Status.DONE, genre = Genre("x", "Драма"))
        val done = Card(1, "", "a", "", progress = "12 з 12", genres = listOf("Драма"))
        val going = Card(2, "", "b", "", progress = "3 з 12", genres = listOf("Драма"))
        assertEquals(true, Repo.matches(done, f, ""))
        assertEquals(false, Repo.matches(going, f, ""))
        assertEquals("https://anitube.in.ua/anime/x/page/2/", Repo.listUrl(Filters(genre = Genre("x", "Драма")), 2))
        assertEquals("https://anitube.in.ua/xfsearch/year/2020/", Repo.listUrl(Filters(year = "2020"), 1))
    }
}
