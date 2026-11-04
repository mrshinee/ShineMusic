package com.shinemusic.player

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import coil.Coil
import coil.request.ImageRequest
import com.shinemusic.R
import com.shinemusic.data.FavoritesStore
import com.shinemusic.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Background playback + notification / lock-screen / OriginOS island controls.
 * Layout: [shuffle]  previous  play/pause  next  [favorite]
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val shuffleCmd = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
    private val favCmd = SessionCommand(ACTION_FAV, Bundle.EMPTY)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            refreshLayout()
            prefetchCovers()
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refreshLayout()
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCmd).add(favCmd).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(buildLayout())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession, controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                ACTION_FAV -> currentSong()?.let { FavoritesStore.toggle(applicationContext, it) }
            }
            refreshLayout()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.get(this)
        // Fresh channel id: a channel that was ever switched off on this phone can't hide the notification.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel)
                .build()
        )
        session = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setBitmapLoader(CoilBitmapLoader(this))   // cover art for notification / lock screen / media card
            .build()
        session?.setCustomLayout(buildLayout())
        player.addListener(playerListener)
        prefetchCovers()
        // favorite toggled inside the app -> update the check icon here
        scope.launch { FavoritesStore.version.drop(1).collect { refreshLayout() } }
    }

    private fun currentSong(): Song? {
        val mi = PlayerHolder.get(this).currentMediaItem ?: return null
        val md = mi.mediaMetadata
        return Song(
            id = mi.mediaId,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            image = md.artworkUri?.toString().orEmpty(),
            url = mi.localConfiguration?.uri?.toString().orEmpty()
        )
    }

    private fun buildLayout(): ImmutableList<CommandButton> {
        val player = PlayerHolder.get(this)
        val shuffleOn = player.shuffleModeEnabled
        val fav = FavoritesStore.isFav(this, player.currentMediaItem?.mediaId)
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
                .setIconResId(if (shuffleOn) R.drawable.ic_notif_shuffle_on else R.drawable.ic_notif_shuffle_off)
                .setSessionCommand(shuffleCmd)
                .build(),
            CommandButton.Builder()
                .setDisplayName(if (fav) "Remove from favorites" else "Add to favorites")
                .setIconResId(if (fav) R.drawable.ic_notif_fav_added else R.drawable.ic_notif_fav_add)
                .setSessionCommand(favCmd)
                .build()
        )
    }

    /** Warm the image cache for the current + next songs so the notification cover appears instantly. */
    private fun prefetchCovers() {
        val p = PlayerHolder.get(this)
        val loader = Coil.imageLoader(this)
        val i = p.currentMediaItemIndex
        for (idx in listOf(i, p.nextMediaItemIndex)) {
            if (idx < 0 || idx >= p.mediaItemCount) continue
            val uri = p.getMediaItemAt(idx).mediaMetadata.artworkUri ?: continue
            loader.enqueue(ImageRequest.Builder(this).data(uri.toString()).size(512, 512).allowHardware(false).build())
        }
    }

    private fun refreshLayout() { session?.setCustomLayout(buildLayout()) }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = PlayerHolder.get(this)
        PlaybackStore.save(this, p)
        if (!p.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        PlaybackStore.save(this, PlayerHolder.get(this))
        PlayerHolder.get(this).removeListener(playerListener)
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "shinemusic_playback"
        const val ACTION_SHUFFLE = "com.shinemusic.action.SHUFFLE"
        const val ACTION_FAV = "com.shinemusic.action.FAV"
    }
}
