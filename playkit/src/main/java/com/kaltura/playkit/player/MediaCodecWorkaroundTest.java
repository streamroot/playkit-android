package com.kaltura.playkit.player;

/**
 * Created by anton.afanasiev on 27/02/2018.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.ExoMediaDrm;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.MediaDrmCallback;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.util.UUID;

public class MediaCodecWorkaroundTest {

    public static boolean workaroundRequired;

    private static final String WORKAROUND_REQUIRED_KEY = "PlaykitWorkaroundRequired";
    private static final String WORKAROUND_WAS_TESTED_KEY = "PlaykitWorkaroundWasTested";
    private static final String WORKAROUND_SHARED_PREFS = "PlaykitWorkaroundSharedPrefs";

    private static final String URL = "asset:///DRMTest/index.mpd";

    private static MediaDrmCallback fakeDrmCallback = new MediaDrmCallback() {
        @Override
        public byte[] executeProvisionRequest(UUID uuid, ExoMediaDrm.ProvisionRequest request) throws Exception {
            return null;
        }

        @Override
        public byte[] executeKeyRequest(UUID uuid, ExoMediaDrm.KeyRequest request) throws Exception {
            Thread.sleep(10000);
            return null;
        }
    };

    static void executeTest(Context context) {

        final SharedPreferences sharedPreferences = context.getSharedPreferences(WORKAROUND_SHARED_PREFS, 0);
        boolean wasTested = sharedPreferences.getBoolean(WORKAROUND_WAS_TESTED_KEY, false);

        if (wasTested) {
            workaroundRequired = sharedPreferences.getBoolean(WORKAROUND_REQUIRED_KEY, false);
            return;
        }

        DataSource.Factory mediaDataSourceFactory = new DefaultDataSourceFactory(context, "whatever");

        Handler mainHandler = new Handler(Looper.getMainLooper());

        DefaultTrackSelector trackSelector = new DefaultTrackSelector(new AdaptiveTrackSelection.Factory(null));

        DefaultDrmSessionManager<FrameworkMediaCrypto> drmSessionManager =
                getDrmSessionManager(mainHandler);

        if (drmSessionManager == null) {
            return;
        }

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context, drmSessionManager);
        final SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
        player.addListener(new Player.DefaultEventListener() {
            @Override
            public void onPlayerError(ExoPlaybackException error) {
                if (error.getCause() instanceof MediaCodecRenderer.DecoderInitializationException) {

                    workaroundRequired = true;
                    player.release();
                    sharedPreferences.edit()
                            .putBoolean(WORKAROUND_WAS_TESTED_KEY, true)
                            .putBoolean(WORKAROUND_REQUIRED_KEY, true)
                            .apply();
                }
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch (playbackState) {
                    case Player.STATE_READY:
                        //If we receive player state ready, we can assume that now workaround required.
                        // So set the workaroundRequired flag to false and save it for previous uses.
                        workaroundRequired = false;
                        player.release();
                        sharedPreferences.edit()
                                .putBoolean(WORKAROUND_WAS_TESTED_KEY, true)
                                .putBoolean(WORKAROUND_REQUIRED_KEY, false)
                                .apply();
                        break;
                }
            }
        });

        MediaSource mediaSource = new DashMediaSource(Uri.parse(URL), mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);

        player.prepare(mediaSource);
    }

    private static DefaultDrmSessionManager<FrameworkMediaCrypto> getDrmSessionManager(Handler mainHandler) {
        try {
            return DefaultDrmSessionManager.newWidevineInstance(fakeDrmCallback, null, mainHandler, null);
        } catch (UnsupportedDrmException e) {
            e.printStackTrace();
            return null;
        }
    }
}
