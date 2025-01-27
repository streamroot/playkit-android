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

package com.kaltura.playkit.drm;

import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;

import com.kaltura.android.exoplayer2.C;
import com.kaltura.android.exoplayer2.Format;
import com.kaltura.android.exoplayer2.drm.DrmInitData;
import com.kaltura.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.kaltura.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.kaltura.android.exoplayer2.source.chunk.ChunkExtractorWrapper;
import com.kaltura.android.exoplayer2.source.chunk.InitializationChunk;
import com.kaltura.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.kaltura.android.exoplayer2.source.dash.manifest.DashManifest;
import com.kaltura.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.kaltura.android.exoplayer2.source.dash.manifest.Period;
import com.kaltura.android.exoplayer2.source.dash.manifest.Representation;
import com.kaltura.android.exoplayer2.upstream.DataSpec;
import com.kaltura.android.exoplayer2.upstream.FileDataSource;
import com.kaltura.playkit.PKLog;
import com.kaltura.playkit.player.MediaSupport;
import com.kaltura.playkit.utils.Consts;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Created by anton.afanasiev on 13/12/2016.
 * <p>
 * A simple (limited) dash parser. Extracts Format and DrmInitData from the manifest and/or initialization chink.
 * Currently only reads the first Representation of the video AdaptationSet of the first Period.
 */

class SimpleDashParser {

    private static final PKLog log = PKLog.get("SimpleDashParser");

    Format format;// format of the first Representation of the video AdaptationSet.
    byte[] widevineInitData;
    private DrmInitData drmInitData;
    boolean hasContentProtection;

    SimpleDashParser parse(String localPath, String assetId) throws IOException {

        InputStream inputStream = new BufferedInputStream(new FileInputStream(localPath));

        DashManifestParser mpdParser = new DashManifestParser();
        DashManifest mpd = mpdParser.parse(Uri.parse(localPath), inputStream);

        if (mpd.getPeriodCount() < 1) {
            throw new IOException("At least one period is required");
        }

        Period period = mpd.getPeriod(0);
        List<AdaptationSet> adaptationSets = period.adaptationSets;
        AdaptationSet videoAdaptation = adaptationSets.get(Consts.TRACK_TYPE_VIDEO);

        List<Representation> representations = videoAdaptation.representations;

        if (representations == null || representations.isEmpty()) {
            throw new IOException("At least one video representation is required");
        }

        Representation representation = representations.get(0);

        if (representation != null) {
            format = representation.format;
            if (format != null) {
                drmInitData = format.drmInitData;
            }
            
            if (drmInitData != null && drmInitData.schemeDataCount > 0) {
                hasContentProtection = true;
                loadDrmInitData(representation);
            } else {
                log.i("no content protection found");
            }
        }
        return this;
    }

    private void loadDrmInitData(Representation representation) throws IOException {

        Uri initFile = representation.getInitializationUri().resolveUri(representation.baseUrl);

        FileDataSource initChunkSource = new FileDataSource();
        DataSpec initDataSpec = new DataSpec(initFile);
        int trigger = C.SELECTION_REASON_MANUAL;
        ChunkExtractorWrapper extractorWrapper = new ChunkExtractorWrapper(new FragmentedMp4Extractor(), C.TRACK_TYPE_DEFAULT, format);
        InitializationChunk chunk = new InitializationChunk(initChunkSource, initDataSpec, format, trigger, format, extractorWrapper); // TODO why do we need the 5 -fth argument
        try {
            chunk.load();
        } catch (InterruptedException e) {
            log.e("Interrupted! " + e.getMessage());
        }

        if (extractorWrapper.getSampleFormats() != null && extractorWrapper.getSampleFormats().length >= 1) {
            drmInitData = extractorWrapper.getSampleFormats()[0].drmInitData;
            if (drmInitData != null) {
                DrmInitData.SchemeData schemeInitData = getWidevineInitData(drmInitData);
                if (schemeInitData != null) {
                    widevineInitData = schemeInitData.data;
                }
            }
        }
    }

    @Nullable
    private DrmInitData.SchemeData getWidevineInitData(DrmInitData drmInitData) {
        UUID widevineUUID = MediaSupport.WIDEVINE_UUID;
        if (drmInitData == null) {
            log.e("No PSSH in media");
            return null;
        }

        DrmInitData.SchemeData schemeData = null;
        for (int i = 0 ; i < drmInitData.schemeDataCount ; i++) {
            if (drmInitData.get(i) != null && drmInitData.get(i).matches(widevineUUID)) {
                schemeData = drmInitData.get(i);
            }
        }

        if (schemeData == null) {
            log.e("No Widevine PSSH in media");
            return null;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // Prior to L the Widevine CDM required data to be extracted from the PSSH atom.
            byte[] psshData = PsshAtomUtil.parseSchemeSpecificData(schemeData.data, widevineUUID);
            if (psshData != null) {
                schemeData = new DrmInitData.SchemeData(widevineUUID, schemeData.mimeType, psshData);
            }
        }
        return schemeData;
    }
}
