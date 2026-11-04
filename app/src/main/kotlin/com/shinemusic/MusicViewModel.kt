package com.shinemusic

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.shinemusic.data.FavoritesStore
import com.shinemusic.data.LrcLib
import com.shinemusic.data.LyricLine
import com.shinemusic.data.SaavnApi
import com.shinemusic.data.SpotifyApi
import com.shinemusic.data.UpdateChecker
import com.shinemusic.data.UpdateInfo
import com.shinemusic.data.UpdateChecker
import com.shinemusic.data.UpdateInfo
import com.shinemusic.data.Song
import com.shinemusic.data.key
import com.shinemusic.data.uniqueSongs
import com.shinemusic.player.PlaybackService
import com.shinemusic.player.PlayerHolder
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    val player = PlayerHolder.get(app)
    private val prefs = app.getSharedPreferences("shine", 0)

    // ---- browse state
    var sections by mutableStateOf<List<Pair<String, List<Song>>>>(emptyList())
    var loadingHome by mutableStateOf(true)
    var activeChip by mutableStateOf<String?>(null)
    var chipResults by mutableStateOf<List<Song>>(emptyList())
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searching by mutableStateOf(false)

    // ---- app update (shown as a dialog)
    var update by mutableStateOf<UpdateInfo?>(null)

    // ---- library
    val favorites = mutableStateListOf<Song>()
    val recents = mutableStateListOf<Song>()

    // ---- player state
    var queue by mutableStateOf<List<Song>>(emptyList())
    var current by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeatOne by mutableStateOf(false)
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var sleepMinutes by mutableStateOf(0)
    private var sleepJob: Job? = null
    private var searchJob: Job? = null
    private var chipJob: Job? = null
    private var homeJob: Job? = null
    private var errorStreak = 0

    // ---- app updates (GitHub Releases)
    var update by mutableStateOf<UpdateInfo?>(null)
    var showUpdate by mutableStateOf(false)
    var updateProgress by mutableStateOf(-1f)               // -1 = not downloading, else 0..1
    var updateFile by mutableStateOf<java.io.File?>(null)   // set once the APK is downloaded
    var updateError by mutableStateOf<String?>(null)
    private var updateJob: Job? = null

    private var controller: MediaController? = null

    init {
        reloadFavorites()
        // notification / lock-screen favorite button changes land here too
        viewModelScope.launch { FavoritesStore.version.collect { reloadFavorites() } }
        loadList(prefs.getString("rec", null), recents)

        // Bind the media service so the notification + background playback work.
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        MediaController.Builder(app, token).buildAsync().also { f ->
            f.addListener({ controller = runCatching { f.get() }.getOrNull() }, androidx.core.content.ContextCompat.getMainExecutor(app))
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) errorStreak = 0
            }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }   // toggled from the notification
            override fun onPlayerError(error: PlaybackException) {
                // A dead / blocked stream: skip to the next song instead of freezing. Stop if everything fails.
                errorStreak++
                if (errorStreak >= queue.size.coerceAtLeast(1) || !player.hasNextMediaItem()) {
                    Toast.makeText(app, "Couldn't play this song. Check your connection.", Toast.LENGTH_SHORT).show()
                    isPlaying = false
                    return
                }
                Toast.makeText(app, "Couldn't play this song, skipping…", Toast.LENGTH_SHORT).show()
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // match by mediaId (not by position) so shuffle / queue edits can never show the wrong song
                val s = item?.mediaId?.let { id -> queue.firstOrNull { it.id == id } }
                    ?: queue.getOrNull(player.currentMediaItemIndex) ?: return
                current = s
                positionMs = 0L
                addRecent(s)
                viewModelScope.launch { lyrics = emptyList(); lyrics = LrcLib.fetch(s) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                durationMs = player.duration.coerceAtLeast(0)
            }
        })

        syncFromPlayer()

        viewModelScope.launch {
            while (true) {
                positionMs = player.currentPosition
                if (durationMs <= 0) durationMs = player.duration.coerceAtLeast(0)
                delay(250)
            }
        }
        loadHome()
        checkUpdate()
    }

    private fun checkUpdate() = viewModelScope.launch {
        val info = UpdateChecker.check() ?: return@launch
        // "Later" is remembered per version, except for forced updates
        if (!info.force && prefs.getInt("skipped_update", 0) == info.versionCode) return@launch
        update = info
    }

    fun dismissUpdate() {
        update?.let { if (!it.force) prefs.edit().putInt("skipped_update", it.versionCode).apply() }
        update = null
    }

    /** If the service is still playing (activity was closed/recreated), rebuild UI state from the player. */
    private fun syncFromPlayer() {
        val n = player.mediaItemCount
        if (n == 0) return
        val known = (favorites + recents).associateBy { it.id }
        val list = (0 until n).map { player.getMediaItemAt(it) }.map { mi ->
            known[mi.mediaId] ?: Song(
                id = mi.mediaId,
                title = mi.mediaMetadata.title?.toString() ?: "",
                artist = mi.mediaMetadata.artist?.toString() ?: "",
                image = mi.mediaMetadata.artworkUri?.toString() ?: "",
                url = mi.localConfiguration?.uri?.toString() ?: ""
            )
        }
        queue = list
        current = list.getOrNull(player.currentMediaItemIndex)
        isPlaying = player.isPlaying
        durationMs = player.duration.coerceAtLeast(0)
        positionMs = player.currentPosition
        shuffle = player.shuffleModeEnabled
        repeatOne = player.repeatMode == Player.REPEAT_MODE_ONE
        current?.let { s -> viewModelScope.launch { lyrics = LrcLib.fetch(s) } }
    }

    // ---------------- app updates
    fun checkForUpdates() {
        viewModelScope.launch {
            delay(3000)                       // let Home appear first
            val u = UpdateChecker.check() ?: return@launch
            update = u
            showUpdate = true
        }
    }

    fun dismissUpdate() { showUpdate = false }

    /** Download (first tap) and then install (second tap / automatically opened). */
    fun downloadUpdate() {
        val info = update ?: return
        updateFile?.let { installUpdate(); return }
        if (updateJob?.isActive == true) return
        updateError = null
        updateProgress = 0f
        updateJob = viewModelScope.launch {
            try {
                val f = UpdateChecker.download(getApplication<Application>(), info) { updateProgress = it }
                updateFile = f
                updateProgress = -1f
                showUpdate = true
                installUpdate()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateProgress = -1f
                updateError = "Download failed. Check your connection and try again."
            }
        }
    }

    fun installUpdate() {
        val f = updateFile ?: return
        if (!UpdateChecker.install(getApplication<Application>(), f)) {
            Toast.makeText(getApplication<Application>(), "Allow \"Install unknown apps\" for ShineMusic, then tap Install again", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------- data loading
    fun loadHome() {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            loadingHome = true
            val defs = listOf(
                "Punjabi vibes" to "punjabi hits",
                "Romantic" to "romantic hindi",
                "Party mode" to "party songs",
                "Chill & lo-fi" to "lofi chill"
            )
            val restJobs = defs.map { (t, q) -> async { t to SaavnApi.search(q, 25) } }

            // Quick picks come from Spotify (two genre queries + two popular artists, interleaved for variety).
            // Saavn is only the fallback if Spotify is off, limited, or too slow (5 s).
            val spotifyHero = async<List<Song>> {
                if (!SpotifyApi.enabled) emptyList()
                else {
                    val lists = listOf(
                        "year:2026 genre:bollywood", "year:2025-2026 genre:indian", "arijit singh", "diljit dosanjh"
                    ).map { q -> async { SpotifyApi.searchSongs(q, 10) } }.map { it.await() }
                    interleave(lists)
                }
            }
            val saavnHero = async {
                listOf("trending hindi songs", "latest bollywood hits", "new hindi songs", "top bollywood songs")
                    .map { q -> async { SaavnApi.search(q, 20) } }.flatMap { it.await() }
            }

            // One shared "seen" set: a song can appear only once on the whole home screen.
            val seen = HashSet<String>()
            val seenIds = HashSet<String>()
            fun takeFresh(src: List<Song>, max: Int): List<Song> {
                val out = ArrayList<Song>()
                for (s in src) {
                    if (out.size >= max) break
                    if (seenIds.add(s.id) && seen.add(s.key())) out.add(s)
                }
                return out
            }

            val fromSpotify = (withTimeoutOrNull(5000) { spotifyHero.await() } ?: emptyList())
                .uniqueSongs().distinctBy { it.image }
            val hero = if (fromSpotify.size >= 4) takeFresh(fromSpotify, 12) else {
                val all = saavnHero.await().uniqueSongs()
                val distinctCovers = all.distinctBy { it.image }
                takeFresh(if (distinctCovers.size >= 4) distinctCovers else all, 12)
            }
            val rest = restJobs.map { it.await() }.map { (t, list) -> t to takeFresh(list, 20) }

            val result = (listOf("Quick picks" to hero) + rest).filter { it.second.isNotEmpty() }
            // keep the old content if a refresh failed completely
            if (result.isNotEmpty() || sections.isEmpty()) sections = result
            loadingHome = false

            // Spotify row loads afterwards so it never delays the first paint.
            if (SpotifyApi.enabled) {
                val queries = listOf("new hindi songs", "latest punjabi songs", "romantic hindi")
                var picks: List<Song> = emptyList()
                for (q in queries) {
                    picks = takeFresh(SpotifyApi.searchSongs(q, 20), 15)
                    if (picks.size >= 3) break
                }
                if (picks.size >= 3 && sections.none { it.first == "Fresh on Spotify" }) {
                    val list = sections.toMutableList()
                    list.add(minOf(2, list.size), "Fresh on Spotify" to picks)
                    sections = list
                }
            }
        }
    }

    /** a1, b1, c1, a2, b2, c2 ... so the carousel mixes sources instead of showing one artist in a row */
    private fun interleave(lists: List<List<Song>>): List<Song> {
        val out = ArrayList<Song>()
        var i = 0
        while (lists.any { i < it.size }) { lists.forEach { l -> l.getOrNull(i)?.let(out::add) }; i++ }
        return out
    }

    /**
     * Saavn and Spotify run in parallel. Saavn's list is shown immediately ([onQuick]); when the
     * Spotify-ranked results (HD covers, clean titles) arrive they go first, followed by the rest.
     * Duplicates are removed by id and by title + artist.
     */
    private suspend fun hybridSearch(q: String, limit: Int, onQuick: (List<Song>) -> Unit = {}): List<Song> = coroutineScope {
        val spotify = async { SpotifyApi.searchSongs(q, 20) }
        val saavn = SaavnApi.search(q, limit)
        onQuick(saavn)
        (spotify.await() + saavn).uniqueSongs().take(limit.coerceAtLeast(25))
    }

    fun selectChip(label: String) {
        chipJob?.cancel()   // a slow earlier chip can no longer overwrite a newer one
        if (activeChip == label) { activeChip = null; chipResults = emptyList(); return }
        activeChip = label
        chipResults = emptyList()
        chipJob = viewModelScope.launch { chipResults = hybridSearch("$label songs", 25) { chipResults = it } }
    }

    fun search(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { searchResults = emptyList(); searching = false; return }
        searchJob = viewModelScope.launch {
            searching = true
            delay(350)
            searchResults = hybridSearch(q, 30) { searchResults = it }   // Saavn shows instantly, Spotify refines
            searching = false
        }
    }

    // ---------------- playback
    fun play(list: List<Song>, index: Int) {
        val target = list.getOrNull(index) ?: return

        // Tapping the song that is already loaded just toggles it instead of restarting it.
        // Only when the player is healthy: after an error / restart it is IDLE and play() alone does nothing,
        // so in that case fall through and load the queue again.
        if (player.mediaItemCount > 0 && player.currentMediaItem?.mediaId == target.id &&
            player.playbackState != Player.STATE_IDLE && player.playerError == null
        ) {
            if (!player.isPlaying) player.play()
            return
        }

        val clean = list.uniqueSongs().let { u -> if (u.any { it.id == target.id }) u else u + target }
        val start = clean.indexOfFirst { it.id == target.id }.coerceAtLeast(0)

        errorStreak = 0
        queue = clean
        current = target
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(clean.map { it.toMediaItem() }, start, 0)
        player.prepare()
        player.play()
    }

    fun togglePlay() {
        if (player.isPlaying) { player.pause(); return }
        // a restored / failed player is IDLE: it must be prepared before play() can do anything
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
        player.play()
    }
    fun next() { if (player.hasNextMediaItem()) player.seekToNext() else player.seekTo(0, 0) }
    fun previous() { if (player.currentPosition > 3000) player.seekTo(0) else player.seekToPrevious() }
    fun seekTo(ms: Long) { player.seekTo(ms); positionMs = ms }
    fun toggleShuffle() { shuffle = !shuffle; player.shuffleModeEnabled = shuffle }
    fun toggleRepeat() {
        repeatOne = !repeatOne
        player.repeatMode = if (repeatOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_ALL
    }
    fun playQueueItem(i: Int) { player.seekTo(i, 0); player.play() }

    fun setSleepTimer(min: Int) {
        sleepJob?.cancel()
        sleepMinutes = min
        if (min > 0) sleepJob = viewModelScope.launch {
            delay(min * 60_000L); player.pause(); sleepMinutes = 0
        }
    }

    // ---------------- library
    fun isFav(s: Song?) = s != null && favorites.any { it.id == s.id }
    fun toggleFav(s: Song) {
        FavoritesStore.toggle(getApplication<Application>(), s)
        reloadFavorites()
    }
    private fun reloadFavorites() {
        val fresh = FavoritesStore.load(getApplication<Application>())
        if (fresh.map { it.id } != favorites.map { it.id }) { favorites.clear(); favorites.addAll(fresh) }
    }
    private fun addRecent(s: Song) {
        recents.removeAll { it.id == s.id }
        recents.add(0, s)
        while (recents.size > 50) recents.removeAt(recents.lastIndex)
        prefs.edit().putString("rec", toJson(recents)).apply()
    }

    // ---------------- helpers
    private fun Song.toMediaItem() = MediaItem.Builder()
        .setMediaId(id)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist)
                .apply { if (image.isNotBlank()) setArtworkUri(android.net.Uri.parse(image)) }.build()
        ).build()

    private fun toJson(l: List<Song>) = JSONArray().also { a ->
        l.forEach {
            a.put(JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist)
                .put("i", it.image).put("u", it.url).put("d", it.durationSec))
        }
    }.toString()

    private fun loadList(json: String?, into: MutableList<Song>) {
        if (json == null) return
        runCatching {
            val a = JSONArray(json)
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                into.add(Song(it.getString("id"), it.getString("t"), it.getString("a"),
                    it.getString("i"), it.getString("u"), it.optInt("d")))
            }
        }
    }
}
