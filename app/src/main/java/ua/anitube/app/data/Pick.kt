package ua.anitube.app.data

/** Вибір озвучки/плеєра/серії за замовчуванням — чиста логіка. */
object Pick {
    fun dub(d: Detail, savedLabel: String, lastLabel: String, preferSubs: Boolean): Int {
        if (d.dubs.isEmpty()) return 0
        d.dubs.indexOfFirst { it.label == savedLabel && savedLabel.isNotEmpty() }.let { if (it >= 0) return it }
        d.dubs.indexOfFirst { it.label == lastLabel && lastLabel.isNotEmpty() }.let { if (it >= 0) return it }
        d.dubs.indexOfFirst { it.isSubs == preferSubs }.let { if (it >= 0) return it }
        return 0
    }

    fun player(dub: Dub?, savedPlayer: String): Int {
        if (dub == null || dub.players.isEmpty()) return 0
        val i = dub.players.indexOfFirst { it.name.equals(savedPlayer, true) }
        return if (i >= 0) i else 0
    }

    /** Серія: за історією (або наступна, якщо попередню дивилися до кінця), інакше перша. */
    fun episode(source: PlayerSource?, lastTitle: String?, lastDone: Boolean): Int {
        if (source == null || source.episodes.isEmpty() || lastTitle == null) return 0
        val i = source.episodes.indexOfFirst { it.title == lastTitle }
        if (i < 0) return 0
        return if (lastDone && i + 1 < source.episodes.size) i + 1 else i
    }
}
