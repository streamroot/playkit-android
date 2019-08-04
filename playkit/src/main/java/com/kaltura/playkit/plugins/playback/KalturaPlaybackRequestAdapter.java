/*
 * ============================================================================
 * Copyright (C) 2017 Kaltura Inc.
 *
 * Licensed under the AGPLv3 license, unless a different license for a
 * particular library is specified in the applicable library path.
 *
 * You may obtain a copy of the License at
 * https://www.gnu.org/licenses/agpl-3.0.html
 * ============================================================================
 */

package com.kaltura.playkit.plugins.playback;

import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;

import com.kaltura.playkit.PKRequestParams;
import com.kaltura.playkit.Player;
import com.kaltura.playkit.player.PlayerSettings;

import static com.kaltura.playkit.PlayKitManager.CLIENT_TAG;
import static com.kaltura.playkit.Utils.toBase64;

/**
 * Created by Noam Tamim @ Kaltura on 28/03/2017.
 */
public class KalturaPlaybackRequestAdapter implements PKRequestParams.Adapter {

    private final String applicationName;
    private String playSessionId;

    public static void install(Player player, String applicationName) {
        if (player.getSettings() instanceof PlayerSettings &&
                ((PlayerSettings)player.getSettings()).getContentRequestAdapter() != null &&
                TextUtils.isEmpty(applicationName)) {
            applicationName = ((PlayerSettings)player.getSettings()).getContentRequestAdapter().getApplicationName();
        }

        KalturaPlaybackRequestAdapter decorator = new KalturaPlaybackRequestAdapter(applicationName, player);
        player.getSettings().setContentRequestAdapter(decorator);
    }

    private KalturaPlaybackRequestAdapter(String applicationName, Player player) {
        this.applicationName = applicationName;
        updateParams(player);
    }

    @NonNull
    @Override
    public PKRequestParams adapt(PKRequestParams requestParams) {
        Uri url = requestParams.url;

        if (url != null && url.getPath().contains("/playManifest/")) {
            Uri alt = url.buildUpon()
                    .appendQueryParameter("clientTag", CLIENT_TAG)
                    .appendQueryParameter("playSessionId", playSessionId).build();

            if (!TextUtils.isEmpty(applicationName)) {
                alt = alt.buildUpon().appendQueryParameter("referrer", toBase64(applicationName.getBytes())).build();
            }

            String lastPathSegment = requestParams.url.getLastPathSegment();
            if (lastPathSegment != null && lastPathSegment.endsWith(".wvm")) {
                // in old android device it will not play wvc if url is not ended in wvm
                alt = alt.buildUpon().appendQueryParameter("name", lastPathSegment).build();
            }
            return new PKRequestParams(alt, requestParams.headers);
        }

        return requestParams;
    }

    @Override
    public void updateParams(Player player) {
        this.playSessionId = player.getSessionId();
    }

    @Override
    public String getApplicationName() {
        return applicationName;
    }
}
