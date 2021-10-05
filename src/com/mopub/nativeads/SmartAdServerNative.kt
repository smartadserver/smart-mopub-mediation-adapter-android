package com.mopub.nativeads

import android.content.Context
import android.view.View
import com.mopub.mobileads.SmartAdServerMoPubUtil.configureSDKIfNeeded
import com.mopub.mobileads.SmartAdServerMoPubUtil.getAdPlacementFromServerParams
import com.smartadserver.android.library.model.SASNativeAdElement
import com.smartadserver.android.library.model.SASNativeAdManager
import com.smartadserver.android.library.model.SASNativeAdManager.NativeAdListener
import com.smartadserver.android.library.util.SASUtil
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.exception.SASAdTimeoutException
import java.lang.Exception

/**
 * Class that handles a MoPub mediation native ad call to Smart Display SDK.
 */
class SmartAdServerNative : CustomEventNative() {
    /**
     * Class representing a MoPub native ad with static content (no video).
     */
    class SASStaticNativeAd(var sasNativeAdElement: SASNativeAdElement) : StaticNativeAd() {
        override fun prepare(view: View) {
            notifyAdImpressed()
            sasNativeAdElement.registerView(view)
        }

        override fun clear(view: View) {
            sasNativeAdElement.unregisterView(view)
        }

        init {
            sasNativeAdElement.onClickListener = SASNativeAdElement.OnClickListener { _, _ ->
                notifyAdClicked()
            }

            callToAction = sasNativeAdElement.calltoAction
            clickDestinationUrl = sasNativeAdElement.clickUrl
            title = sasNativeAdElement.title
            text = sasNativeAdElement.subtitle
            starRating = sasNativeAdElement.rating.toDouble()
            if (sasNativeAdElement.icon != null) {
                iconImageUrl = sasNativeAdElement.icon!!.url
            }
            if (sasNativeAdElement.coverImage != null) {
                mainImageUrl = sasNativeAdElement.coverImage!!.url
            }
        }
    }

    /**
     * Class representing a MoPub native ad with video content.
     */
    class SASVideoNativeAd(var sASNativeAd: SASNativeAdElement) : BaseNativeAd() {
        override fun prepare(view: View) {
            notifyAdImpressed()
            sASNativeAd.registerView(view)
        }

        override fun clear(view: View) {
            sASNativeAd.unregisterView(view)
        }

        override fun destroy() {
            // nothing to do
        }

        val title: String?
            get() = sASNativeAd.title
        val text: String?
            get() = sASNativeAd.subtitle
        val callToAction: String?
            get() = sASNativeAd.calltoAction
        val mainImageUrl: String?
            get() {
                var mainImageUrl: String? = null
                if (sASNativeAd.coverImage != null) {
                    mainImageUrl = sASNativeAd.coverImage!!.url
                }
                return mainImageUrl
            }
        val iconImageUrl: String?
            get() {
                var iconUrl: String? = null
                if (sASNativeAd.icon != null) {
                    iconUrl = sASNativeAd.icon!!.url
                }
                return iconUrl
            }
        val privacyInformationIconImageUrl: String?
            get() = null
        val privacyInformationIconClickThroughUrl: String
            get() = "https://smartadserver.com/company/privacy-policy/"

        init {
            sASNativeAd.onClickListener = SASNativeAdElement.OnClickListener { _, _ ->
                notifyAdClicked()
            }
        }
    }

    override fun loadNativeAd(
        context: Context,
        customEventNativeListener: CustomEventNativeListener,
        localExtras: Map<String, Any>,
        serverExtras: Map<String, String>
    ) {

        // First, configure the Smart Display SDK
        if (!configureSDKIfNeeded(context, serverExtras)) {
            // Error during configuration
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR)
            return
        }

        // Get the Smart ad placement
        val adPlacement = getAdPlacementFromServerParams(serverExtras)
        if (adPlacement == null) {
            // Invalid Smart ad placement
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR)
            return
        }

        // Create native ad manager
        val nativeAdManager = SASNativeAdManager(context, adPlacement)

        // Set the native ad manager listener
        nativeAdManager.nativeAdListener = object : NativeAdListener {
            override fun onNativeAdLoaded(sasNativeAdElement: SASNativeAdElement) {
                val baseNativeAd: BaseNativeAd
                val videoAdElement = sasNativeAdElement.mediaElement
                var videoRendererAvailable = false
                try {
                    Class.forName("com.mopub.nativeads.SmartAdServerNativeVideoAdRenderer")
                    videoRendererAvailable = true
                } catch (ignored: ClassNotFoundException) {
                }

                // Create a native video ad only if renderer is available
                baseNativeAd = if (videoAdElement != null && videoRendererAvailable) {
                    SASVideoNativeAd(sasNativeAdElement)
                } else {
                    SASStaticNativeAd(sasNativeAdElement)
                }
                // Must be executed in Main thread
                SASUtil.getMainLooperHandler().post {
                    customEventNativeListener.onNativeAdLoaded(
                        baseNativeAd
                    )
                }
            }

            override fun onNativeAdFailedToLoad(e: Exception) {
                customEventNativeListener.onNativeAdFailed(NativeErrorCode.NETWORK_NO_FILL)
                var errorCode = NativeErrorCode.UNSPECIFIED
                if (e is SASNoAdToDeliverException) {
                    // No ad to deliver
                    errorCode = NativeErrorCode.NETWORK_NO_FILL
                } else if (e is SASAdTimeoutException) {
                    // Ad request timeout translates to admob network error
                    errorCode = NativeErrorCode.NETWORK_TIMEOUT
                }

                // Must be executed in Main thread
                val finalCode = errorCode
                SASUtil.getMainLooperHandler()
                    .post { customEventNativeListener.onNativeAdFailed(finalCode) }
            }
        }
        nativeAdManager.loadNativeAd()
    }
}