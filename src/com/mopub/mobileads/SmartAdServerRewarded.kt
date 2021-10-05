package com.mopub.mobileads

import com.mopub.mobileads.SmartAdServerMoPubUtil.configureSDKIfNeeded
import com.mopub.mobileads.SmartAdServerMoPubUtil.getAdPlacementFromServerParams
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager
import com.smartadserver.android.library.model.SASAdPlacement
import com.mopub.common.LifecycleListener
import android.app.Activity
import android.content.Context
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager.RewardedVideoListener
import com.smartadserver.android.library.model.SASAdElement
import com.smartadserver.android.library.util.SASUtil
import com.mopub.common.logging.MoPubLog
import com.mopub.common.logging.MoPubLog.AdapterLogEvent
import com.smartadserver.android.library.exception.SASNoAdToDeliverException
import com.smartadserver.android.library.exception.SASAdTimeoutException
import com.smartadserver.android.library.model.SASReward
import com.mopub.common.MoPubReward
import android.view.ViewGroup
import java.lang.Exception

class SmartAdServerRewarded : BaseAd() {

    companion object {
        private val ADAPTER_NAME = SmartAdServerRewarded::class.java.simpleName
    }

    // Smart banner view that will handle the mediation ad call
    private var rewardedVideoManager: SASRewardedVideoManager? = null

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

            if (rewardedVideoManager == null) {
                // Instantiate SASRewardedVideoManager that will perform the Smart ad call
                rewardedVideoManager = SASRewardedVideoManager(context, adPlacement)

                // And set appropriate listener for callbacks
                rewardedVideoManager?.rewardedVideoListener = object : RewardedVideoListener {
                    override fun onRewardedVideoAdLoaded(
                        sasRewardedVideoManager: SASRewardedVideoManager,
                        sasAdElement: SASAdElement
                    ) {
                        SASUtil.getMainLooperHandler().post {
                            mLoadListener?.onAdLoaded()
                            MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_SUCCESS, ADAPTER_NAME)
                        }
                    }

                    override fun onRewardedVideoAdFailedToLoad(
                        sasRewardedVideoManager: SASRewardedVideoManager,
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

                    override fun onRewardedVideoAdShown(sasRewardedVideoManager: SASRewardedVideoManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.SHOW_SUCCESS, ADAPTER_NAME)
                        MoPubLog.log(adNetworkId, AdapterLogEvent.DID_APPEAR, ADAPTER_NAME)
                        mInteractionListener?.onAdShown()
                    }

                    override fun onRewardedVideoAdFailedToShow(
                        sasRewardedVideoManager: SASRewardedVideoManager,
                        e: Exception
                    ) {
                        // notify MoPub of rewarded video show error
                        mInteractionListener?.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR)
                        MoPubLog.log(
                            adNetworkId, AdapterLogEvent.SHOW_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.intCode, MoPubErrorCode.NETWORK_NO_FILL
                        )
                        MoPubLog.log(adNetworkId, AdapterLogEvent.CUSTOM, ADAPTER_NAME, e.message)
                    }

                    override fun onRewardedVideoAdClicked(sasRewardedVideoManager: SASRewardedVideoManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.CLICKED, ADAPTER_NAME)
                        mInteractionListener?.onAdClicked()
                    }

                    override fun onRewardedVideoAdClosed(sasRewardedVideoManager: SASRewardedVideoManager) {
                        MoPubLog.log(adNetworkId, AdapterLogEvent.DID_DISAPPEAR, ADAPTER_NAME)
                        mInteractionListener?.onAdDismissed()
                    }

                    override fun onRewardReceived(
                        sasRewardedVideoManager: SASRewardedVideoManager,
                        sasReward: SASReward
                    ) {
                        val reward = MoPubReward.success(sasReward.currency, sasReward.amount.toInt())
                        MoPubLog.log(
                            adNetworkId, AdapterLogEvent.SHOULD_REWARD, ADAPTER_NAME,
                            reward.amount, reward.label
                        )
                        mInteractionListener?.onAdComplete(reward)
                    }

                    override fun onRewardedVideoEvent(
                        sasRewardedVideoManager: SASRewardedVideoManager,
                        i: Int
                    ) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server rewarded video event: $i"
                        )
                        // no equivalent
                    }

                    override fun onRewardedVideoEndCardDisplayed(
                        sasRewardedVideoManager: SASRewardedVideoManager,
                        viewGroup: ViewGroup
                    ) {
                        MoPubLog.log(
                            adNetworkId,
                            AdapterLogEvent.CUSTOM,
                            ADAPTER_NAME,
                            "Smart Ad Server rewarded video end card displayed"
                        )
                        // no equivalent
                    }
                }
            }

            // Now request ad for this SASRewardedVideoManager
            MoPubLog.log(adNetworkId, AdapterLogEvent.LOAD_ATTEMPTED, ADAPTER_NAME)
            rewardedVideoManager?.loadRewardedVideo()
        } ?: run {
            // Incorrect Smart placement
            val errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR
            mLoadListener?.onAdLoadFailed(errorCode)
        }
    }

    override fun show() {
        MoPubLog.log(adNetworkId, AdapterLogEvent.SHOW_ATTEMPTED, ADAPTER_NAME)
        if (rewardedVideoManager != null && rewardedVideoManager!!.hasRewardedVideo()) {
            rewardedVideoManager!!.showRewardedVideo()
        } else {

            // notify MoPub of rewarded video show error
            mInteractionListener?.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR)

            MoPubLog.log(
                adNetworkId, AdapterLogEvent.SHOW_FAILED, ADAPTER_NAME,
                MoPubErrorCode.NETWORK_NO_FILL.intCode, MoPubErrorCode.NETWORK_NO_FILL
            )

            var errorReason = "Smart Ad Server rewarded video manager show general error."
            if (rewardedVideoManager == null) {
                errorReason = "Smart Ad Server rewarded video manager not instantiated. Please load rewarded video again."
            } else if (!rewardedVideoManager!!.hasRewardedVideo()) {
                errorReason = "Smart Ad Server rewarded video ad not loaded or expired. Please load rewarded video again."
            }
            MoPubLog.log(adNetworkId, AdapterLogEvent.CUSTOM, ADAPTER_NAME, errorReason)
        }
    }

    override fun onInvalidate() {
        rewardedVideoManager?.onDestroy()
        rewardedVideoManager = null
    }
}