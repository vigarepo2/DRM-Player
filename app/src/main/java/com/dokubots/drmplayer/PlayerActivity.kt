package com.dokubots.drmplayer

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.URLUtil
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.dokubots.drmplayer.databinding.ActivityPlayerBinding

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private var rawUrl: String = ""
    private var resizeModeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUI()

        rawUrl = intent.getStringExtra("URL") ?: ""
        if (rawUrl.isEmpty()) {
            Toast.makeText(this, "Failed to load URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupOverlayControls()
        initializePlayer()
    }

    private fun setupOverlayControls() {
        binding.playerView.setControllerVisibilityListener(androidx.media3.ui.PlayerView.ControllerVisibilityListener { visibility ->
            binding.overlayControls.visibility = visibility
        })

        binding.btnAspectRatio.setOnClickListener {
            val modes = arrayOf(
                AspectRatioFrameLayout.RESIZE_MODE_FIT,
                AspectRatioFrameLayout.RESIZE_MODE_FILL,
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            )
            resizeModeIndex = (resizeModeIndex + 1) % modes.size
            binding.playerView.resizeMode = modes[resizeModeIndex]
            val modeName = arrayOf("Fit", "Fill", "Zoom", "Width", "Height")[resizeModeIndex]
            Toast.makeText(this, "Scale: $modeName", Toast.LENGTH_SHORT).show()
        }

        binding.btnRotate.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    private fun initializePlayer() {
        val config = StreamParser.parse(rawUrl)
        
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            if (config.headers.containsKey("User-Agent")) setUserAgent(config.headers["User-Agent"])
            setDefaultRequestProperties(config.headers)
        }

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build()

        binding.playerView.player = player

        var fileName = URLUtil.guessFileName(config.url, null, null)
        if (fileName.contains("?") || fileName.endsWith(".bin")) fileName = "Live Stream"
        
        HistoryManager.saveHistory(this, rawUrl, fileName)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(config.url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(fileName).build())

        if (config.drmType != "none" && config.drmLicense.isNotEmpty()) {
            val drmScheme = when (config.drmType.lowercase()) {
                "widevine" -> C.WIDEVINE_UUID
                "playready" -> C.PLAYREADY_UUID
                "clearkey" -> C.CLEARKEY_UUID
                else -> C.UUID_NIL
            }
            if (drmScheme != C.UUID_NIL) {
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(drmScheme)
                        .setLicenseUri(config.drmLicense)
                        .build()
                )
            }
        }

        player?.setMediaItem(mediaItemBuilder.build())

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val prefs = getSharedPreferences("PlayerHistory", Context.MODE_PRIVATE)
                    val savedPosition = prefs.getLong(config.url, 0L)
                    if (savedPosition > 0 && player?.isCurrentMediaItemLive == false) {
                        player?.seekTo(savedPosition)
                        Toast.makeText(this@PlayerActivity, "Resumed", Toast.LENGTH_SHORT).show()
                    }
                    player?.removeListener(this)
                }
            }
        })

        player?.prepare()
        player?.playWhenReady = true
    }

    private fun hideSystemUI() {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStop() {
        super.onStop()
        player?.let {
            if (!it.isCurrentMediaItemLive && configUrl().isNotEmpty()) {
                val prefs = getSharedPreferences("PlayerHistory", Context.MODE_PRIVATE)
                prefs.edit().putLong(configUrl(), it.currentPosition).apply()
            }
            it.pause()
        }
    }

    private fun configUrl() = StreamParser.parse(rawUrl).url

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
