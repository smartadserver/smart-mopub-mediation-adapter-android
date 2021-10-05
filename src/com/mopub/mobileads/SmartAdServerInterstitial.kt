package com.mopub.mobileads

import com.mopub.mobileads.SmartAdServerMoPubUtil.configureSDKIfNeeded
import com.mopub.mobileads.SmartAdServerMoPubUtil.getAdPlacementFromServerParams
import com.smartadserver.android.library.ui.SASInterstitialManager
import com.smartadserver.android.library.model.SASAdPlacement
import com.mopub.common.LifecycleListener
import android.app.Activity
import android.content.Context
import com.smartadserver.android.library.ui.SASInterstitialManager.InterstitialListener
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.util.SASUtil
import com.mopub.common.logging.MoPubLog
import com.mopub.common.logging.MoPubLog.AdapterLogEvent
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.exception.SASAdTimeoutException
import java.lang.Exception

class SmartAdServerInterstitial : BaseAd() {

    companion object {
        private val ADAPTER_NAME = SmartAdServerInterstitial::class.java.simpleName
    }

    // Smart banner view that will handle the mediation ad call
    private var interstitialManager: SASInterstitialManager? = null

    // the placement of the last loadAd call
    private var adPlacement: SASAdPlacement? = null

    override fun getLifecycleListener(): LifecycleListener? {
        return null
    }

    override fun getAdNetworkId(): String {
        return if (adPlacement == null) "" else adPlacement.toString()
    }

    override fun checkAndInitializeSdk(launcherActivity: Activity, adData: AdData): Boolean {
        return configureSDKIfNeeded(launcherActivity, adData.extras)
    }

    override fun load(context: Context, adData: AdData) {
        adPlacement = getAdPlacementFromServerParams(adData.extras)
        adPlacement?.let { adPlacement ->

            // this adapter manually tracks clicks and impressions
            setAutomaticImpressionAndClickTracking(true)
            if (interstitialManager == null) {
                // Instantiate SASInterstitialManager that will perform the Smart ad call
                interstitialManager = SASInterstitialManager(context, adPlacement)

                // And set appropriate listener for callbacks
                interstitialManager?.interstitialListener = object : InterstitialListener {
                    override fun onInterstitialAdLoaded(
                        sasInterstitialManager: SASInterstitialManager,
                        sasAdElement: SASAdElement
                    ) {
                        SASUtil.getMainLooperHandler().post {
                            mLoadListener?.onAdLoaded()
                            MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_SUCCESS, ADAPTER_NAME)
                        }
                    }

                    override fun onInterstitialAdFailedToLoad(
                        sasInterstitialManager: SASInterstitialManager,
                        e: Exception
                    ) {
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

                    override fun onInterstitialAdShown(sasInterstitialManager: SASInterstitialManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.SHOW_SUCCESS, ADAPTER_NAME)
                        MoPubLog.log(adNetworkId, AdapterLogEvent.DID_APPEAR, ADAPTER_NAME)
                        mInteractionListener?.onAdShown()
                    }

                    override fun onInterstitialAdFailedToShow(
                        sasInterstitialManager: SASInterstitialManager,
                        e: Exception
                    ) {
                        // notify MoPub of interstitial show error
                        mInteractionListener?.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR)
                        MoPubLog.log(
                            adNetworkId, AdapterLogEvent.SHOW_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.intCode, MoPubErrorCode.NETWORK_NO_FILL
                        )
                        MoPubLog.log(adNetworkId, AdapterLogEvent.CUSTOM, ADAPTER_NAME, e.message)
                    }

                    override fun onInterstitialAdClicked(sasInterstitialManager: SASInterstitialManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.CLICKED, ADAPTER_NAME)
                        mInteractionListener?.onAdClicked()
                    }

                    override fun onInterstitialAdDismissed(sasInterstitialManager: SASInterstitialManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.DID_DISAPPEAR, ADAPTER_NAME)
                        mInteractionListener?.onAdDismissed()
                    }

                    override fun onInterstitialAdVideoEvent(
                        sasInterstitialManager: SASInterstitialManager,
                        i: Int
                    ) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server interstitial video event: $i"
                        )
                        // no equivalent
                    }
                }
            }

            // Now request ad for this SASInterstitialManager
            MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_ATTEMPTED, ADAPTER_NAME)
            interstitialManager?.loadAd()
        } ?: run {
            // Incorrect Smart placement
            val errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR
            mLoadListener?.onAdLoadFailed(errorCode)
        }
    }

    override fun show() {
        MoPubLog.log(adNetworkId, AdapterLogEvent.SHOW_ATTEMPTED, ADAPTER_NAME)
        if (interstitialManager != null && interstitialManager!!.isShowable) {
            interstitialManager!!.show()
        } else {
            // notify MoPub of interstitial show error
            mInteractionListener?.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR)

            MoPubLog.log(
                adNetworkId, AdapterLogEvent.SHOW_FAILED, ADAPTER_NAME,
                MoPubErrorCode.NETWORK_NO_FILL.intCode, MoPubErrorCode.NETWORK_NO_FILL
            )

            var errorReason = "Smart Ad Server interstitial manager show general error."
            if (interstitialManager == null) {
                errorReason = "Smart Ad Server interstitial manager not instantiated. Please load interstitial again."
            } else if (!interstitialManager!!.isShowable) {
                errorReason = "Smart Ad Server interstitial ad not loaded or expired. Please load interstitial again."
            }
            MoPubLog.log(adNetworkId, AdapterLogEvent.CUSTOM, ADAPTER_NAME, errorReason)
        }
    }

    override fun onInvalidate() {
        interstitialManager?.onDestroy()
        interstitialManager = null
    }
}