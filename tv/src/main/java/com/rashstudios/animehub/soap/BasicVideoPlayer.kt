package com.rashstudios.animehub.soap

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.VideoSupportFragment
import androidx.leanback.app.VideoSupportFragmentGlueHost
import androidx.leanback.media.PlaybackGlue
import androidx.leanback.widget.PlaybackSeekDataProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.rashstudios.animehub.ExoPlayerAdapter
import com.rashstudios.animehub.MyVideoFragment
import com.rashstudios.animehub.MyVideoOptions
import com.rashstudios.animehub.VideoPlayerGlue
import com.rashstudios.animehub.dispatcher
import com.rashstudios.animehub.getEpisodeUrl
import com.rashstudios.animehub.updateRecentlyWatched
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.ArrayList


class BasicPlaybackActivity() : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra("url")!!
        val sub = intent.getStringExtra("sub")
        val vtt = intent.getStringExtra("vtt")
        val title = intent.getStringExtra("title")
        val subtitle = intent.getStringExtra("subtitle")

        val options = MovieVideo(url, sub, vtt, title, subtitle)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(
                    android.R.id.content,
                    BasicVideoFragment(options)
                ).commit()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

class BasicVideoFragment(var mOptions: MovieVideo) : VideoSupportFragment() {

    var mPlayerGlue: VideoPlayerGlue<ExoPlayerAdapter>? = null

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundType = BG_LIGHT;
        initPlayerGlue(true)
        val url = mOptions.url
        println("Video Url ${url}")

        val subs = if (mOptions.sub != null) listOf(
            SubtitleConfiguration.Builder(Uri.parse(mOptions.sub))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        ) else listOf()
        val mediaItem =
            MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setSubtitleConfigurations(subs)
                .build()
        mPlayerGlue!!.title = mOptions.title
        mPlayerGlue!!.subtitle = mOptions.subtitle
        mPlayerGlue!!.playerAdapter.setMediaItem(mediaItem)
    }

    private fun initPlayerGlue(isHls: Boolean) {
        val playerAdapter = ExoPlayerAdapter(requireContext(), isHls)
        val playerGlue = VideoPlayerGlue(
            requireContext(), playerAdapter, MyVideoOptions("", "", listOf(), ""),
            ::playNextVideo, ::playPreviousVideo
        )
        playerGlue.setHost(VideoSupportFragmentGlueHost(this))
        playerGlue.addPlayerCallback(object : PlaybackGlue.PlayerCallback() {
            override fun onPreparedStateChanged(glue: PlaybackGlue) {
                if (glue.isPrepared()) {
                    playerGlue.seekProvider = PlaybackSeekDataProvider()
                    playerGlue.play()
                    println("Playing Video ${glue}")
                }
            }
        })
        mPlayerGlue = playerGlue
    }


    fun playNextVideo(options: MyVideoOptions) {
    }

    fun playPreviousVideo(options: MyVideoOptions) {

    }

    override fun onDestroy() {
        super.onDestroy()
        val currentPosition = mPlayerGlue?.currentPosition
        mPlayerGlue?.playerAdapter?.release()
    }
}

fun openBasicVideoPlayPage(context: Context, mOptions: MovieVideo) {

    val intent = Intent(context, BasicPlaybackActivity::class.java)
    intent.putExtra("url", mOptions.url)
    intent.putExtra("sub", mOptions.sub)
    intent.putExtra("vtt", mOptions.vtt)
    intent.putExtra("title", mOptions.title)
    intent.putExtra("subtitle", mOptions.subtitle)

    context.startActivity(intent)
}