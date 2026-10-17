package ua.anitube.app.player

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.anitube.app.data.Detail
import ua.anitube.app.data.HistoryItem
import ua.anitube.app.data.Store
import ua.anitube.app.net.Site

data class TrackOption(val label: String, val group: Int, val index: Int, val selected: Boolean)

class PlayerVM(app: Application) : AndroidViewModel(app) {
    @Volatile private var referer: String = Site.BASE + "/"

    private val http = Site.client.newBuilder().addInterceptor { chain ->
        val b = chain.request().newBuilder()
            .header("User-Agent", Site.UA)
            .header("Referer", referer)
        try {
            val o = java.net.URI(referer)
            b.header("Origin", "${o.scheme}://${o.host}")
        } catch (_: Exception) {}
        chain.proceed(b.build())
    }.build()

    val player: ExoPlayer = ExoPlayer.Builder(app)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 90_000, 1_500, 3_000).build()
        )
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    var detail by mutableStateOf<Detail?>(null); private set
    var dubIndex by mutableStateOf(0); private set
    var playerIndex by mutableStateOf(0); private set
    var epIndex by mutableStateOf(0); private set

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var playing by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var ended by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var speed by mutableStateOf(1f); private set
    var videoTracks by mutableStateOf<List<TrackOption>>(emptyList()); private set
    var audioTracks by mutableStateOf<List<TrackOption>>(emptyList()); private set
    var textTracks by mutableStateOf<List<TrackOption>>(emptyList()); private set
    var marks by mutableStateOf(ua.anitube.app.data.Marks()); private set
    var active by mutableStateOf(false); private set

    private var loadJob: Job? = null
    private var pendingSeek = 0L
    private var autoSkipped = false
    private var lastSave = 0L
    private var started = false

    val dub get() = detail?.dubs?.getOrNull(dubIndex)
    val source get() = dub?.players?.getOrNull(playerIndex)
    val episodes get() = source?.episodes.orEmpty()
    val episode get() = episodes.getOrNull(epIndex)
    val hasNext get() = epIndex + 1 < episodes.size
    val hasPrev get() = epIndex > 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0)
                    if (pendingSeek > 0) { player.seekTo(pendingSeek); pendingSeek = 0 }
                }
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(e: PlaybackException) {
                loading = false
                error = "Не вдалося відтворити відео (${e.errorCodeName}). Спробуйте інший плеєр чи озвучку."
            }
            override fun onTracksChanged(tracks: Tracks) { readTracks(tracks) }
        })
        viewModelScope.launch {
            while (true) {
                delay(500)
                if (started) {
                    position = player.currentPosition.coerceAtLeast(0)
                    if (player.duration > 0) duration = player.duration
                    tick()
                }
            }
        }
    }

    // ---------- Запуск ----------
    fun start(d: Detail, dub: Int, plr: Int, ep: Int) {
        detail = d
        dubIndex = dub.coerceIn(0, (d.dubs.size - 1).coerceAtLeast(0))
        playerIndex = plr.coerceIn(0, ((curDub().players.size) - 1).coerceAtLeast(0))
        epIndex = ep.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
        marks = Store.marks(d.card.id)
        active = true
        loadCurrent(resume = true)
    }

    private fun curDub() = detail!!.dubs[dubIndex]

    fun close() {
        saveNow()
        player.stop()
        started = false
        active = false
        loadJob?.cancel()
        loading = false
        error = null
    }

    private fun loadCurrent(resume: Boolean) {
        val d = detail ?: return
        val ep = episode ?: run { error = "У цій озвучці немає серій."; return }
        loadJob?.cancel()
        player.pause()
        ended = false
        error = null
        loading = true
        autoSkipped = false
        started = false
        position = 0
        duration = 0
        val saved = Store.episodePos(d.card.id, ep.title)
        pendingSeek = if (resume && Store.settings.value.resume && saved > 10_000) saved else 0
        loadJob = viewModelScope.launch {
            val r = StreamResolver.resolve(ep.url)
            if (r == null) {
                loading = false
                error = "Не вдалося отримати відео з плеєра «${source?.name}». Оберіть інший плеєр або відкрийте у веб-плеєрі."
                return@launch
            }
            referer = r.referer
            val subs = r.subtitles.mapIndexed { i, u ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(u))
                    .setMimeType(if (u.substringBefore('?').endsWith(".srt")) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
                    .setLanguage("uk").setLabel("Субтитри ${i + 1}")
                    .setSelectionFlags(if (i == 0) C.SELECTION_FLAG_DEFAULT else 0).build()
            }
            val item = MediaItem.Builder().setUri(r.url).setSubtitleConfigurations(subs).build()
            player.setMediaItem(item)
            player.setPlaybackSpeed(Store.settings.value.speed)
            speed = Store.settings.value.speed
            player.prepare()
            player.playWhenReady = true
            loading = false
            started = true
        }
    }

    // ---------- Вибір серії / озвучки ----------
    fun playEpisode(i: Int) {
        saveNow()
        epIndex = i.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
        loadCurrent(resume = true)
    }

    fun next() { if (hasNext) playEpisode(epIndex + 1) }
    fun prev() { if (hasPrev) playEpisode(epIndex - 1) }

    /** Змінює озвучку, зберігаючи ту саму серію (за номером) і місце перегляду. */
    fun chooseDub(i: Int, plr: Int = 0) {
        val d = detail ?: return
        if (i !in d.dubs.indices) return
        val curNumber = episode?.number
        val keepPos = player.currentPosition
        saveNow()
        dubIndex = i
        playerIndex = plr.coerceIn(0, (d.dubs[i].players.size - 1).coerceAtLeast(0))
        val idx = episodes.indexOfFirst { it.number == curNumber }
        epIndex = if (idx >= 0) idx else 0
        Store.setDubChoice(d.card.id, curDub().label, source?.name.orEmpty())
        Store.setLastDub(curDub().label)
        loadCurrent(resume = false)
        pendingSeek = keepPos
    }

    fun choosePlayer(p: Int) {
        val d = detail ?: return
        if (p !in curDub().players.indices) return
        val curNumber = episode?.number
        val keepPos = player.currentPosition
        saveNow()
        playerIndex = p
        val idx = episodes.indexOfFirst { it.number == curNumber }
        epIndex = if (idx >= 0) idx else 0
        Store.setDubChoice(d.card.id, curDub().label, source?.name.orEmpty())
        loadCurrent(resume = false)
        pendingSeek = keepPos
    }

    fun retry() { loadCurrent(resume = true) }

    // ---------- Керування ----------
    fun togglePlay() { if (player.isPlaying) player.pause() else { if (ended) player.seekTo(0); player.play() } }
    fun seekTo(ms: Long) { player.seekTo(ms.coerceIn(0, duration.coerceAtLeast(0))); position = ms }
    fun seekBy(ms: Long) = seekTo(player.currentPosition + ms)

    fun changeSpeed(s: Float) {
        speed = s
        player.setPlaybackSpeed(s)
        Store.update { it.copy(speed = s) }
    }

    fun skipOpening() {
        val m = marks
        if (m.introEnd > 0) seekTo(m.introEnd)
        else seekBy(Store.settings.value.skipSeconds * 1000L)
    }

    fun markIntroStart() { marks = marks.copy(introStart = player.currentPosition); persistMarks() }
    fun markIntroEnd() { marks = marks.copy(introEnd = player.currentPosition); persistMarks() }
    fun markOutroStart() { marks = marks.copy(outroStart = player.currentPosition); persistMarks() }
    fun clearMarks() { marks = ua.anitube.app.data.Marks(); persistMarks() }
    private fun persistMarks() { detail?.let { Store.setMarks(it.card.id, marks) } }

    /** Показувати кнопку «Пропустити опенінг»: у вікні відмітки або в перші 5 хвилин. */
    fun introWindowActive(): Boolean {
        if (duration <= 0) return false
        val m = marks
        if (m.introEnd > 0) {
            val start = if (m.introStart >= 0) m.introStart else 0
            return position in start..(m.introEnd - 1500)
        }
        return position in 3_000..300_000
    }

    fun outroWindowActive(): Boolean {
        if (duration <= 0 || !hasNext) return false
        val m = marks
        val start = if (m.outroStart > 0) m.outroStart else duration - 100_000
        return position >= start && duration - position > 1500
    }

    private fun tick() {
        val st = Store.settings.value
        if (st.autoSkipIntro && !autoSkipped && marks.introEnd > 0 && introWindowActive()) {
            autoSkipped = true
            seekTo(marks.introEnd)
        }
        val now = System.currentTimeMillis()
        if (now - lastSave > 5_000 && player.isPlaying) { lastSave = now; saveNow() }
    }

    private fun onEnded() {
        ended = true
        val d = detail ?: return
        episode?.let { Store.setEpisodePos(d.card.id, it.title, 0, true) }
        saveNow(done = true)
        if (Store.settings.value.autoNext && hasNext) {
            epIndex += 1
            loadCurrent(resume = false)
        }
    }

    fun saveNow(done: Boolean = false) {
        val d = detail ?: return
        val ep = episode ?: return
        if (!started && !done) return
        val pos = if (done) 0 else player.currentPosition
        if (!done) Store.setEpisodePos(d.card.id, ep.title, pos, false)
        Store.saveProgress(
            HistoryItem(d.card, ep.title, ep.url, dubIndex, source?.key.orEmpty(), pos, duration, System.currentTimeMillis())
        )
    }

    // ---------- Доріжки ----------
    private fun readTracks(t: Tracks) {
        fun collect(type: Int, label: (androidx.media3.common.Format, Int) -> String): List<TrackOption> {
            val out = ArrayList<TrackOption>()
            t.groups.forEachIndexed { gi, g ->
                if (g.type != type) return@forEachIndexed
                for (ti in 0 until g.length) {
                    if (!g.isTrackSupported(ti)) continue
                    out += TrackOption(label(g.getTrackFormat(ti), out.size), gi, ti, g.isTrackSelected(ti))
                }
            }
            return out
        }
        videoTracks = collect(C.TRACK_TYPE_VIDEO) { f, _ -> if (f.height > 0) "${f.height}p" else "Відео" }
            .distinctBy { it.label }.sortedByDescending { it.label.removeSuffix("p").toIntOrNull() ?: 0 }
        audioTracks = collect(C.TRACK_TYPE_AUDIO) { f, i -> f.label ?: f.language?.let { l -> "Звук: $l" } ?: "Звук ${i + 1}" }
        textTracks = collect(C.TRACK_TYPE_TEXT) { f, i -> f.label ?: f.language ?: "Субтитри ${i + 1}" }
    }

    fun selectTrack(type: Int, opt: TrackOption?) {
        val groups = player.currentTracks.groups
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) {
            params.clearOverridesOfType(type)
            if (type == C.TRACK_TYPE_TEXT) params.setTrackTypeDisabled(type, true)
        } else {
            val g = groups.getOrNull(opt.group) ?: return
            params.setTrackTypeDisabled(type, false)
            params.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, opt.index))
        }
        player.trackSelectionParameters = params.build()
    }

    fun autoQuality() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }

    override fun onCleared() {
        saveNow()
        player.release()
    }
}
