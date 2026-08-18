package pt.rebeliptv.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import java.util.concurrent.TimeUnit

class PlayerManager(
    private val context: Context,
    private val playerView: PlayerView,
    private val onError: (String) -> Unit
) {

    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
    private var reconnectAttempts = 0

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_DELAY_MS = 2000L
    }

    init {
        createPlayer()
    }

    private fun createPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(TimeUnit.SECONDS.toMillis(15).toInt())
            .setReadTimeoutMs(TimeUnit.SECONDS.toMillis(30).toInt())
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                HlsMediaSource.Factory(httpDataSourceFactory)
            )
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer

                exoPlayer.addListener(
                    object : Player.Listener {

                        override fun onPlayerError(
                            error: androidx.media3.common.PlaybackException
                        ) {
                            handlePlaybackError()
                        }
                    }
                )
            }
    }

    fun play(url: String) {
        currentUrl = url
        reconnectAttempts = 0

        startPlayback(url)
    }

    private fun startPlayback(url: String) {
        val exoPlayer = player ?: return

        val mediaItem = MediaItem.fromUri(url)

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun handlePlaybackError() {
        val url = currentUrl ?: return

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            onError(
                "Não foi possível reproduzir este canal."
            )
            return
        }

        reconnectAttempts++

        player?.stop()

        playerView.postDelayed(
            {
                startPlayback(url)
            },
            RECONNECT_DELAY_MS
        )
    }

    fun stop() {
        currentUrl = null
        reconnectAttempts = 0
        player?.stop()
        player?.clearMediaItems()
    }

    fun release() {
        currentUrl = null
        player?.release()
        player = null
        playerView.player = null
    }
}
