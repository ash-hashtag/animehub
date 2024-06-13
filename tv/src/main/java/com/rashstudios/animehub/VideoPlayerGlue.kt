package com.rashstudios.animehub

import android.content.Context
import androidx.leanback.media.PlaybackTransportControlGlue
import androidx.leanback.media.PlayerAdapter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow

class VideoPlayerGlue<T : PlayerAdapter>(context: Context,
                                         impl: T,
                                         private val options: MyVideoOptions,
                                        private val playNextVideo: (MyVideoOptions) -> Unit,
                                        private val playPreviousVideo: (MyVideoOptions) -> Unit,
    ) :
    PlaybackTransportControlGlue<T>(context, impl) {

    private val previousAction: PlaybackControlsRow.SkipPreviousAction
    private val nextAction: PlaybackControlsRow.SkipNextAction

    init {
        previousAction = PlaybackControlsRow.SkipPreviousAction(context)
        nextAction = PlaybackControlsRow.SkipNextAction(context)
    }

    override fun onCreatePrimaryActions(primaryActionsAdapter: ArrayObjectAdapter) {
        primaryActionsAdapter.add(previousAction)
        super.onCreatePrimaryActions(primaryActionsAdapter)
        primaryActionsAdapter.add(nextAction)
    }

    override fun onActionClicked(action: Action) {
        println("VIDEO ACTION: ${action}")

        when (action) {
            nextAction -> {
                playNextVideo(options)
            }

            previousAction -> {
                playPreviousVideo(options)
            }

            else -> super.onActionClicked(action)
        }
    }

}