package com.isladinamica.app

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

/**
 * Vigila la app de música que esté sonando (Spotify, YouTube Music, etc.) y la muestra en la isla:
 * en reposo, carátula + ecualizador; al tocarla o al cambiar de canción, controles de anterior / pausa / siguiente.
 * Necesita el acceso a notificaciones, que ya se pide en la app.
 */
class MusicWatcher(private val ctx: Context, private val component: ComponentName) {

    companion object {
        private const val PREV = "⏮︎"
        private const val NEXT = "⏭︎"
        private const val PLAY = "▶︎"
        private const val PAUSE = "⏸︎"
        private const val GRAY = 0xFF3A3A3C.toInt()
        private const val OWNER = "music"
    }

    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var controller: MediaController? = null
    private var lastTitle: String? = null
    private var lastPlaying = false

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = pick(sessions())
    }

    private fun island() = IslandAccessibilityService.instance?.island

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component)
        } catch (_: SecurityException) {
            return
        }
        pick(sessions())
    }

    fun stop() {
        try {
            msm.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        controller?.unregisterCallback(callback)
        controller = null
        island()?.setMusic(null)
    }

    private fun sessions(): List<MediaController>? = try {
        msm.getActiveSessions(component)
    } catch (_: SecurityException) {
        null
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            controller?.registerCallback(callback)
            lastTitle = null
            lastPlaying = false
        }
        refresh()
    }

    private fun readState(c: MediaController): MusicState? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrEmpty()) return null
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return MusicState(art, title, artist, playing)
    }

    private fun refresh() {
        val island = island() ?: return
        val c = controller
        val st = if (c != null && Prefs.showMusic(ctx)) readState(c) else null
        if (st == null) {
            island.compactTap = null
            island.setMusic(null)
            return
        }
        island.compactTap = { expand() }
        island.setMusic(st)
        island.setActionLabel(OWNER, 1, if (st.playing) PAUSE else PLAY)
        if (st.playing && (st.title != lastTitle || !lastPlaying)) expand()
        lastTitle = st.title
        lastPlaying = st.playing
    }

    private fun expand() {
        val c = controller ?: return
        val island = island() ?: return
        val st = readState(c) ?: return
        val tc = c.transportControls
        island.present(
            st.art?.let { BitmapDrawable(ctx.resources, it) },
            st.title,
            st.artist,
            {
                try {
                    c.sessionActivity?.send()
                } catch (_: Exception) {
                }
            },
            5000L,
            true,
            listOf(
                IslandAction(PREV, GRAY) {
                    tc.skipToPrevious()
                    island.extendHold(4000L)
                },
                IslandAction(if (st.playing) PAUSE else PLAY, GRAY) {
                    if (c.playbackState?.state == PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
                    island.extendHold(4000L)
                },
                IslandAction(NEXT, GRAY) {
                    tc.skipToNext()
                    island.extendHold(4000L)
                }
            ),
            false,
            false,
            OWNER
        )
    }
}
