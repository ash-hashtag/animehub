package com.rashstudios.animehub;

import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES;
import static androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.view.SurfaceHolder;

import androidx.annotation.OptIn;
import androidx.leanback.media.PlaybackGlueHost;
import androidx.leanback.media.PlayerAdapter;
import androidx.leanback.media.SurfaceHolderGlueHost;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;

/**
 * This implementation extends the {@link PlayerAdapter} with a {@link SimpleExoPlayer}.
 */
public class ExoPlayerAdapter extends PlayerAdapter implements Player.Listener {

    Context mContext;
    final ExoPlayer mPlayer;
    SurfaceHolderGlueHost mSurfaceHolderGlueHost;
    final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getCallback().onCurrentPositionChanged(ExoPlayerAdapter.this);
            getCallback().onBufferedPositionChanged(ExoPlayerAdapter.this);
            mHandler.postDelayed(this, getUpdatePeriod());
        }
    };
    final Handler mHandler = new Handler();
    boolean mInitialized = false;
    Uri mMediaSourceUri = null;
    boolean mHasDisplay;
    boolean mBufferingStart;
    @C.StreamType int mAudioStreamType;

    boolean isHLS;

    /**
     * Constructor.
     */
    @OptIn(markerClass = UnstableApi.class)
    public ExoPlayerAdapter(Context context, Boolean hls) {
        mContext = context;

        HlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(
                FLAG_ALLOW_NON_IDR_KEYFRAMES | FLAG_DETECT_ACCESS_UNITS, false
        );
        MediaSource.Factory mediaSourceFactory =
                hls ? new DefaultMediaSourceFactory(new CacheDataSource.Factory()) :
                        new HlsMediaSource.Factory(new CacheDataSource.Factory())
                                .setExtractorFactory(extractorFactory);
        mPlayer = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        mPlayer.addListener(this);
        isHLS = hls;
    }

    @Override
    public void onAttachedToHost(PlaybackGlueHost host) {
        if (host instanceof SurfaceHolderGlueHost) {
            mSurfaceHolderGlueHost = ((SurfaceHolderGlueHost) host);
            mSurfaceHolderGlueHost.setSurfaceHolderCallback((SurfaceHolder.Callback) new VideoPlayerSurfaceHolderCallback());
        }
    }

    /**
     * Will reset the {@link ExoPlayer} and the glue such that a new file can be played. You are
     * not required to call this method before playing the first file. However you have to call it
     * before playing a second one.
     */
    public void reset() {
        changeToUninitialized();
        mPlayer.stop();
    }

    void changeToUninitialized() {
        if (mInitialized) {
            mInitialized = false;
            notifyBufferingStartEnd();
            if (mHasDisplay) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        }
    }


    /**
     * Notify the state of buffering. For example, an app may enable/disable a loading figure
     * according to the state of buffering.
     */
    void notifyBufferingStartEnd() {
        getCallback().onBufferingStateChanged(ExoPlayerAdapter.this,
                mBufferingStart || !mInitialized);
    }

    /**
     * Release internal {@link SimpleExoPlayer}. Should not use the object after call release().
     */
    public void release() {
        changeToUninitialized();
        mHasDisplay = false;
        mPlayer.release();
    }

    @Override
    public void onDetachedFromHost() {
        if (mSurfaceHolderGlueHost != null) {
            mSurfaceHolderGlueHost.setSurfaceHolderCallback(null);
            mSurfaceHolderGlueHost = null;
        }
        reset();
        release();
    }

    /**
     * @see SimpleExoPlayer#setVideoSurfaceHolder(SurfaceHolder)
     */
    void setDisplay(SurfaceHolder surfaceHolder) {
        boolean hadDisplay = mHasDisplay;
        mHasDisplay = surfaceHolder != null;
        if (hadDisplay == mHasDisplay) {
            return;
        }

        mPlayer.setVideoSurfaceHolder(surfaceHolder);
        if (mHasDisplay) {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        } else {
            if (mInitialized) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        }
    }

    @Override
    public void setProgressUpdatingEnabled(final boolean enabled) {
        mHandler.removeCallbacks(mRunnable);
        if (!enabled) {
            return;
        }
        mHandler.postDelayed(mRunnable, getUpdatePeriod());
    }

    int getUpdatePeriod() {
        return 16;
    }

    @Override
    public boolean isPlaying() {
        boolean exoPlayerIsPlaying = mPlayer.getPlaybackState() == ExoPlayer.STATE_READY
                && mPlayer.getPlayWhenReady();
        return mInitialized && exoPlayerIsPlaying;
    }

    @Override
    public long getDuration() {
        return mInitialized ? mPlayer.getDuration() : -1;
    }

    @Override
    public long getCurrentPosition() {
        return mInitialized ? mPlayer.getCurrentPosition() : -1;
    }


    @Override
    public void play() {
        if (!mInitialized || isPlaying()) {
            return;
        }

        mPlayer.setPlayWhenReady(true);
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
        getCallback().onCurrentPositionChanged(ExoPlayerAdapter.this);
    }

    @Override
    public void pause() {
        if (isPlaying()) {
            mPlayer.setPlayWhenReady(false);
            getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
        }
    }

    @Override
    public void seekTo(long newPosition) {
        if (!mInitialized) {
            return;
        }
        mPlayer.seekTo(newPosition);
    }

    @Override
    public long getBufferedPosition() {
        return mPlayer.getBufferedPosition();
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * Sets the media source of the player with a given URI.
     *
     * @return Returns <code>true</code> if uri represents a new media; <code>false</code>
     * otherwise.
     * @see ExoPlayer#prepare(MediaSource)
     */
    public boolean setDataSource(Uri uri) {
        if (mMediaSourceUri != null ? mMediaSourceUri.equals(uri) : uri == null) {
            return false;
        }
        mMediaSourceUri = uri;
        prepareMediaForPlaying();
        return true;
    }

    @UnstableApi
    public void setMediaItem(MediaItem mediaItem) {
        reset();
//        mPlayer.setMediaItem(mediaItem);
        MediaSource source = new DefaultMediaSourceFactory(mContext).createMediaSource(mediaItem);
        mPlayer.setMediaSource(source);
        mPlayer.prepare();
        notifyBufferingStartEnd();
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
    }

    public int getAudioStreamType() {
        return mAudioStreamType;
    }

    public void setAudioStreamType(@C.StreamType int audioStreamType) {
        mAudioStreamType = audioStreamType;
    }

    /**
     * Set {@link MediaSource} for {@link SimpleExoPlayer}. An app may override this method in order
     * to use different {@link MediaSource}.
     *
     * @param uri The url of media source
     * @return MediaSource for the player
     */
    @OptIn(markerClass = UnstableApi.class)
    public MediaSource onCreateMediaSource(Uri uri) {
        return new DefaultMediaSourceFactory(mContext).createMediaSource(MediaItem.fromUri(uri));
    }

    @OptIn(markerClass = UnstableApi.class)
    private void prepareMediaForPlaying() {
        reset();
        if (mMediaSourceUri != null) {
            MediaSource mediaSource = onCreateMediaSource(mMediaSourceUri);
//            mPlayer.prepare(mediaSource);
            mPlayer.setMediaSource(mediaSource);
            mPlayer.prepare();
        } else {
            return;
        }

        notifyBufferingStartEnd();
        getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
    }

    /**
     * @return True if ExoPlayer is ready and got a SurfaceHolder if
     * {@link PlaybackGlueHost} provides SurfaceHolder.
     */
    @Override
    public boolean isPrepared() {
        return mInitialized && (mSurfaceHolderGlueHost == null || mHasDisplay);
    }

    /**
     * Implements {@link SurfaceHolder.Callback} that can then be set on the
     * {@link PlaybackGlueHost}.
     */
    class VideoPlayerSurfaceHolderCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            setDisplay(surfaceHolder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            setDisplay(null);
        }
    }

    // ExoPlayer Event Listeners

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        mBufferingStart = false;
        if (playbackState == ExoPlayer.STATE_READY && !mInitialized) {
            mInitialized = true;
            if (mSurfaceHolderGlueHost == null || mHasDisplay) {
                getCallback().onPreparedStateChanged(ExoPlayerAdapter.this);
            }
        } else if (playbackState == ExoPlayer.STATE_BUFFERING) {
            mBufferingStart = true;
        } else if (playbackState == ExoPlayer.STATE_ENDED) {
            getCallback().onPlayStateChanged(ExoPlayerAdapter.this);
            getCallback().onPlayCompleted(ExoPlayerAdapter.this);
        }
        notifyBufferingStartEnd();
    }

//    @Override
//    public void onPlayerError(ExoPlaybackException error) {
//        getCallback().onError(ExoPlayerAdapter.this, error.type,
//                mContext.getString(R.string.lb_media_player_error,
//                        error.type,
//                        error.rendererIndex));
//    }

//    @Override
//    public void onLoadingChanged(boolean isLoading) {
//    }
//
//    @Override
//    public void onTimelineChanged(Timeline timeline, Object manifest) {
//    }
//
//    @Override
//    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
//    }
//
//    @Override
//    public void onPositionDiscontinuity() {
//    }


}
