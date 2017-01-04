package com.smartadserver.android.library.mediation;

import com.google.android.gms.ads.mediation.MediationAdRequest;

import java.util.Map;
import java.util.Set;

/**
 * Utility class
 */
public class SASMoPubCustomEventUtil {

    private static final String siteIdKey = "siteid";
    private static final String pageIdKey = "pageid";
    private static final String formatIdKey = "formatid";
    private static final String targetKey = "target";

    /**
     * Convenience holder class for ad placement parameters
     */
    public static class SASAdPlacement {
        public int siteId;
        public String pageId = "";
        public int formatId;
        public String targeting = "";
    }

    /**
     * Returns a SASAdPlacement instance from passed parameters
     */
    public static SASAdPlacement getPlacementFromMap(Map<String, String> moPubParams) {

        SASAdPlacement adPlacement = new SASAdPlacement();
        try {
            adPlacement.siteId = Integer.parseInt(moPubParams.get(siteIdKey));
            adPlacement.formatId = Integer.parseInt(moPubParams.get(formatIdKey));
            adPlacement.pageId = moPubParams.get(pageIdKey);
            adPlacement.targeting = moPubParams.get(targetKey);
            if (adPlacement.pageId == null || adPlacement.pageId.length() == 0) {
                throw new IllegalArgumentException(); // will end up in catch block..
            }

        }  catch (Exception e) {
            adPlacement = null;
        }

        return adPlacement;
    }
}
