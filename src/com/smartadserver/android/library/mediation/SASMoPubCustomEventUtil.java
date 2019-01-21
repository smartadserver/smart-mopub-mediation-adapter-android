package com.smartadserver.android.library.mediation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mopub.common.MoPub;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.util.SASConfiguration;

import java.util.Map;

/**
 * Utility class needed by all SASMoPubCustomEvent adapters. Handles Smart Display SDK configuration.
 */
public class SASMoPubCustomEventUtil {

    private static final String BASE_URL = "https://mobile.smartadserver.com"; // TODO replace it with your own base URL.

    private static final String SITE_ID_KEY = "siteid";
    private static final String PAGE_ID_KEY = "pageid";
    private static final String FORMAT_ID_KEY = "formatid";
    private static final String TARGET_KEY = "target";

    /**
     * Configures the Smart Display SDK if needed.
     *
     * @param context     The current application context.
     * @param moPubParams MoPub's serverExtra parameters.
     * @return true if the SDK is correctly configured, false otherwise.
     */
    @SuppressLint("Range")
    public static boolean configureSDKIfNeeded(@NonNull Context context, @NonNull Map<String, String> moPubParams) {

        int siteId = -1;
        String rawSiteId = moPubParams.get(SITE_ID_KEY);

        if (rawSiteId != null) {
            siteId = Integer.parseInt(rawSiteId);
        }

        if (siteId <= 0) {
            // Invalid placement
            return false;
        }

        SASConfiguration sasConfiguration = SASConfiguration.getSharedInstance();

        // We configure the Smart Display SDK if not already done.
        if (!sasConfiguration.isConfigured()) {
            try {
                sasConfiguration.configure(context, siteId, BASE_URL);
                sasConfiguration.setLoggingEnabled(true);
            } catch (SASConfiguration.ConfigurationException e) {
                e.printStackTrace();
                return false;
            }
        }

        boolean locationEnabled = !(MoPub.getLocationAwareness() == MoPub.LocationAwareness.DISABLED);
        sasConfiguration.setAutomaticLocationAllowed(locationEnabled);

        return true;
    }

    /**
     * Returns an instance of SASAdPlacement from the given MoPub's server params.
     *
     * @param moPubParams MoPub's server params.
     * @return An instance of SASAdPlacement, or null if MoPub's params are invalid.
     */
    @Nullable
    public static SASAdPlacement getAdPlacementFromServerParams(@NonNull Map<String, String> moPubParams) {
        int siteId = -1;
        int formatId = -1;
        String pageId = moPubParams.get(PAGE_ID_KEY);
        String targeting = moPubParams.get(TARGET_KEY);

        String rawSiteId = moPubParams.get(SITE_ID_KEY);
        String rawFormatId = moPubParams.get(FORMAT_ID_KEY);

        if (rawSiteId != null && rawFormatId != null) {
            siteId = Integer.parseInt(rawSiteId);
            formatId = Integer.parseInt(rawFormatId);
        }

        if (pageId == null || pageId.length() == 0 || siteId <= 0 || formatId <= 0) {
            // Invalid placement
            return null;
        }

        return new SASAdPlacement(siteId, pageId, formatId, targeting);
    }
}
