package com.mopub.mobileads

import com.smartadserver.android.library.ui.SASBannerView
import com.smartadserver.android.library.model.SASAdPlacement
import com.mopub.common.LifecycleListener
import android.app.Activity
import android.content.Context
import android.view.View
import com.smartadserver.android.library.ui.SASBannerView.BannerListener
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.util.SASUtil
import com.mopub.common.logging.MoPubLog
import com.mopub.common.logging.MoPubLog.AdapterLogEvent
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.exception.SASAdTimeoutException
import java.lang.Exception

class SmartAdServerBanner : BaseAd() {

    companion object {
        private val ADAPTER_NAME = SmartAdServerBanner::class.java.simpleName
    }

    // Smart banner view that will handle the mediation ad call
    private var sasBannerView: SASBannerView? = null

    // the placement of the last loadAd call
    private var adPlacement: SASAdPlacement? = null

    override fun getLifecycleListener(): LifecycleListener? {
        return null
    }

    override fun getAdNetworkId(): String {
        return if (adPlacement == null) "" else adPlacement.toString()
    }

    override fun checkAndInitializeSdk(launcherActivity: Activity, adData: AdData): Boolean {
        return SmartAdServerMoPubUtil.configureSDKIfNeeded(launcherActivity, adData.extras)
    }

    override fun load(context: Context, adData: AdData) {
        SmartAdServerMoPubUtil.getAdPlacementFromServerParams(adData.extras)?.let { placementData ->
            adPlacement = placementData

            // this adapter manually tracks clicks and impressions
            setAutomaticImpressionAndClickTracking(true)
            if (sasBannerView == null) {
                // Instantiate SASBannerView that will perform the Smart ad call
                sasBannerView = SASBannerView(context)

                // and set appropriate banner listener for callbacks
                sasBannerView?.bannerListener = object : BannerListener {
                    override fun onBannerAdLoaded(
                        sasBannerView: SASBannerView,
                        sasAdElement: SASAdElement
                    ) {
                        mLoadListener?.let { loadListener ->
                            SASUtil.getMainLooperHandler().post {
                                loadListener.onAdLoaded()
                                MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_SUCCESS, ADAPTER_NAME)
                            }
                        }
                    }

                    override fun onBannerAdFailedToLoad(sasBannerView: SASBannerView, e: Exception) {
                        val errorCode: MoPubErrorCode = when (e) {
                            is SASNoAdToDeliverException -> {
                                // No ad to deliver
                                MoPubErrorCode.NO_FILL
                            }
                            is SASAdTimeoutException -> {
                                // Ad request timeout translates to AdMob network error
                                MoPubErrorCode.NETWORK_TIMEOUT
                            }
                            else -> {
                                MoPubErrorCode.UNSPECIFIED
                            }
                        }

                        SASUtil.getMainLooperHandler().post {
                            MoPubLog.log(
                                adNetworkId, AdapterLogEvent.LOAD_FAILED, ADAPTER_NAME,
                                errorCode.intCode, errorCode
                            )
                            mLoadListener?.onAdLoadFailed(errorCode)
                        }
                    }

                    override fun onBannerAdClicked(sasBannerView: SASBannerView) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.CLICKED, ADAPTER_NAME)
                        if (mInteractionListener != null) {
                            mInteractionListener!!.onAdClicked()
                        }
                    }

                    override fun onBannerAdExpanded(sasBannerView: SASBannerView) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server banner ad expanded"
                        )
                        mInteractionListener?.onAdExpanded()
                    }

                    override fun onBannerAdCollapsed(sasBannerView: SASBannerView) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server banner ad collapsed"
                        )
                        mInteractionListener?.onAdCollapsed()
                    }

                    override fun onBannerAdResized(sasBannerView: SASBannerView) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server banner ad resized"
                        )
                        // no equivalent
                    }

                    override fun onBannerAdClosed(sasBannerView: SASBannerView) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server banner ad closed"
                        )
                        mInteractionListener?.onAdDismissed()
                    }

                    override fun onBannerAdVideoEvent(sasBannerView: SASBannerView, i: Int) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server banner video event: $i"
                        )
                        // no equivalent
                    }
                }
            }

            // Now request ad for this SASBannerView
            MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_ATTEMPTED, ADAPTER_NAME)
            sasBannerView?.loadAd(adPlacement!!)
        } ?: run {
            // Incorrect Smart placement
            val errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR
            mLoadListener?.onAdLoadFailed(errorCode)
        }
    }

    override fun getAdView(): View? {
        return sasBannerView
    }

    override fun onInvalidate() {
        sasBannerView?.onDestroy()
        sasBannerView = null
    }
}