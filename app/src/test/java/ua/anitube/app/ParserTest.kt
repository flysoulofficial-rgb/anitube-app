package ua.anitube.app

import org.junit.Assert.*
import org.junit.Test
import ua.anitube.app.data.Card
import ua.anitube.app.data.Parser

class ParserTest {
    private fun res(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes().toString(Charsets.UTF_8)

    @Test fun listingCards() {
        val p = Parser.parseListing(res("listing.html"))
        assertEquals(11, p.items.size)
        assertTrue(p.hasNext)
        val c = p.items.first { it.id == 6130 }
        assertEquals("Певний таємничий загін ITEM", c.title)
        assertEquals("ТБ", c.type)
        assertEquals(2, c.done)
        assertEquals(12, c.total)
        assertFalse(c.isCompleted)
        assertTrue(c.genres.contains("Фентезі"))
        assertTrue(c.poster.startsWith("https://anitube.in.ua/uploads/"))
    }

    @Test fun detailAshdi() {
        val d = Parser.parseDetail(res("title_ashdi.html"), "https://anitube.in.ua/3972-slozi-tari.html")!!
        assertEquals("Сльози Тіари", d.card.title)
        assertEquals(3972, d.card.id)
        assertEquals("2009", d.year)
        assertEquals("26 з 26", d.episodesText)
        assertEquals("White Fox", d.studio)
        assertTrue(d.card.isCompleted)
        assertTrue(d.dubs.size >= 2)
        val vr = d.dubs.first { it.name == "VRdub" }
        assertTrue(vr.players.any { it.name.contains("ASHDI", true) && it.episodes.size >= 15 })
        val ashdi = vr.players.first { it.name.contains("ASHDI", true) }
        assertEquals("https://ashdi.vip/vod/289367", ashdi.episodes.first().url)
        assertTrue(d.dubs.first { it.isSubs }.name.isNotBlank())
    }

    @Test fun detailFenix() {
        val d = Parser.parseDetail(res("title_fenix.html"), "https://anitube.in.ua/1-x.html")!!
        assertTrue(d.dubs.isNotEmpty())
        assertTrue(d.dubs.flatMap { it.players }.any { it.episodes.isNotEmpty() })
    }

    @Test fun cardProgress() {
        assertTrue(Card(1, "", "t", "", progress = "12 з 12").isCompleted)
        assertFalse(Card(1, "", "t", "", progress = "3 з 12").isCompleted)
        assertTrue(Card(1, "", "t", "", type = "Повнометражне").isFilm)
    }
}
