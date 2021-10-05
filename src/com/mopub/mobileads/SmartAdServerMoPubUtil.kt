package com.mopub.mobileads

import android.annotation.SuppressLint
import android.content.Context
import com.smartadserver.android.library.util.SASConfiguration
import com.mopub.common.MoPub
import com.smartadserver.android.coresdk.util.SCSConfiguration
import com.smartadserver.android.library.model.SASAdPlacement

/**
 * Utility class needed by all SASMoPubCustomEvent adapters. Handles Smart Display SDK configuration.
 */
object SmartAdServerMoPubUtil {

    private const val SITE_ID_KEY = "siteid"
    private const val PAGE_ID_KEY = "pageid"
    private const val FORMAT_ID_KEY = "formatid"
    private const val TARGET_KEY = "target"

    /**
     * Configures the Smart Display SDK if needed.
     *
     * @param context     The current application context.
     * @param moPubParams MoPub's serverExtra parameters.
     * @return true if the SDK is correctly configured, false otherwise.
     */
    @JvmStatic
    @SuppressLint("Range")
    fun configureSDKIfNeeded(context: Context, moPubParams: Map<String, String>): Boolean {
        var siteId = -1
        val rawSiteId = moPubParams[SITE_ID_KEY]
        if (rawSiteId != null) {
            siteId = rawSiteId.toInt()
        }
        if (siteId <= 0) {
            // Invalid placement
            return false
        }
        val sasConfiguration = SASConfiguration.getSharedInstance()

        // We configure the Smart Display SDK if not already done.
        if (!sasConfiguration.isConfigured) {
            try {
                sasConfiguration.configure(context, siteId)
                sasConfiguration.isLoggingEnabled = true
                sasConfiguration.isPrimarySdk = false
            } catch (e: SCSConfiguration.ConfigurationException) {
                e.printStackTrace()
                return false
            }
        }
        val locationEnabled = MoPub.getLocationAwareness() != MoPub.LocationAwareness.DISABLED
        sasConfiguration.isAutomaticLocationDetectionAllowed = locationEnabled
        return true
    }

    /**
     * Returns an instance of SASAdPlacement from the given MoPub's server params.
     *
     * @param moPubParams MoPub's server params.
     * @return An instance of SASAdPlacement, or null if MoPub's params are invalid.
     */
    @JvmStatic
    fun getAdPlacementFromServerParams(moPubParams: Map<String, String>): SASAdPlacement? {
        var siteId: Long = -1
        var formatId: Long = -1
        val pageId = moPubParams[PAGE_ID_KEY]
        val targeting = moPubParams[TARGET_KEY]
        val rawSiteId = moPubParams[SITE_ID_KEY]
        val rawFormatId = moPubParams[FORMAT_ID_KEY]
        if (rawSiteId != null && rawFormatId != null) {
            siteId = rawSiteId.toLong()
            formatId = rawFormatId.toLong()
        }

        return if (pageId != null && pageId.isNotEmpty() && siteId > 0 && formatId > 0) {
            SASAdPlacement(siteId, pageId, formatId, targeting)
        } else null // Invalid placement
    }
}