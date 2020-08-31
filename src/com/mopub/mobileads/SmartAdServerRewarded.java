package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPubReward;
import com.mopub.common.logging.MoPubLog;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager;
import com.smartadserver.android.library.util.SASUtil;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.DID_APPEAR;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.DID_DISAPPEAR;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOULD_REWARD;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class SmartAdServerRewarded extends BaseAd {

    private static final String ADAPTER_NAME = SmartAdServerRewarded.class.getSimpleName();

    // Smart banner view that will handle the mediation ad call
    private SASRewardedVideoManager rewardedVideoManager;

    // the placement of the last loadAd call
    private SASAdPlacement adPlacement;


    @Nullable
    @Override
    protected LifecycleListener getLifecycleListener() {
        return null;
    }

    @NonNull
    @Override
    protected String getAdNetworkId() {
        return adPlacement == null ? "" : adPlacement.toString();
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull Activity launcherActivity, @NonNull AdData adData) throws Exception {
        return SmartAdServerMoPubUtil.configureSDKIfNeeded(launcherActivity, adData.getExtras());
    }

    @Override
    protected void load(@NonNull Context context, @NonNull AdData adData) throws Exception {

        adPlacement = SmartAdServerMoPubUtil.getAdPlacementFromServerParams(adData.getExtras());

        if (adPlacement == null) {
            // Incorrect Smart placement
            MoPubErrorCode errorCode = MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR;
            if (mLoadListener != null) {
                mLoadListener.onAdLoadFailed(errorCode);
            }
            return;
        }

        // this adapter manually tracks clicks and impressions
        setAutomaticImpressionAndClickTracking(true);

        if (rewardedVideoManager == null) {
            // Instantiate SASRewardedVideoManager that will perform the Smart ad call
            rewardedVideoManager = new SASRewardedVideoManager(context, adPlacement);

            // And set appropriate listener for callbacks
            rewardedVideoManager.setRewardedVideoListener(new SASRewardedVideoManager.RewardedVideoListener() {
                @Override
                public void onRewardedVideoAdLoaded(SASRewardedVideoManager sasRewardedVideoManager, SASAdElement sasAdElement) {
                    if (mLoadListener != null) {
                        SASUtil.getMainLooperHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mLoadListener.onAdLoaded();
                                MoPubLog.log(getAdNetworkId(), LOAD_SUCCESS, ADAPTER_NAME);
                            }
                        });
                    }
                }

                @Override
                public void onRewardedVideoAdFailedToLoad(SASRewardedVideoManager sasRewardedVideoManager, Exception e) {
                    final MoPubErrorCode errorCode;
                    if (e instanceof SASNoAdToDeliverException) {
                        // No ad to deliver
                        errorCode = MoPubErrorCode.NO_FILL;
                    } else if (e instanceof SASAdTimeoutException) {
                        // Ad request timeout translates to AdMob network error
                        errorCode = MoPubErrorCode.NETWORK_TIMEOUT;
                    } else {
                        errorCode = MoPubErrorCode.UNSPECIFIED;
                    }

                    SASUtil.getMainLooperHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            MoPubLog.log(getAdNetworkId(), LOAD_FAILED, ADAPTER_NAME,
                                    errorCode.getIntCode(), errorCode);
                            mLoadListener.onAdLoadFailed(errorCode);
                        }
                    });
                }

                @Override
                public void onRewardedVideoAdShown(SASRewardedVideoManager sasRewardedVideoManager) {
                    MoPubLog.log(getAdNetworkId(), SHOW_SUCCESS, ADAPTER_NAME);
                    MoPubLog.log(getAdNetworkId(), DID_APPEAR, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdShown();
                    }
                }

                @Override
                public void onRewardedVideoAdFailedToShow(SASRewardedVideoManager sasRewardedVideoManager, Exception e) {
                    // notify MoPub of rewarded video show error
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR);
                    }
                    MoPubLog.log(getAdNetworkId(), SHOW_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, e.getMessage());
                }

                @Override
                public void onRewardedVideoAdClicked(SASRewardedVideoManager sasRewardedVideoManager) {
                    MoPubLog.log(getAdNetworkId(), CLICKED, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdClicked();
                    }
                }

                @Override
                public void onRewardedVideoAdClosed(SASRewardedVideoManager sasRewardedVideoManager) {
                    MoPubLog.log(getAdNetworkId(), DID_DISAPPEAR, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdDismissed();
                    }
                }

                @Override
                public void onRewardReceived(SASRewardedVideoManager sasRewardedVideoManager, SASReward sasReward) {

                    MoPubReward reward = MoPubReward.success(sasReward.getCurrency(), (int) sasReward.getAmount());

                    MoPubLog.log(getAdNetworkId(), SHOULD_REWARD, ADAPTER_NAME,
                            reward.getAmount(), reward.getLabel());

                    if (mInteractionListener != null) {
                        mInteractionListener.onAdComplete(reward);
                    }
                }

                @Override
                public void onRewardedVideoEvent(SASRewardedVideoManager sasRewardedVideoManager, int i) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server rewarded video event: " + i);
                    // no equivalent
                }

                @Override
                public void onRewardedVideoEndCardDisplayed(SASRewardedVideoManager sasRewardedVideoManager, ViewGroup viewGroup) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server rewarded video end card displayed");
                    // no equivalent
                }
            });

        }

        // Now request ad for this SASRewardedVideoManager
        MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
        rewardedVideoManager.loadRewardedVideo();
    }

    @Override
    protected void show() {

        MoPubLog.log(getAdNetworkId(), SHOW_ATTEMPTED, ADAPTER_NAME);

        if (rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo()) {
            rewardedVideoManager.showRewardedVideo();
        } else {

            // notify MoPub of rewarded video show error
            if (mInteractionListener != null) {
                mInteractionListener.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR);
            }

            MoPubLog.log(getAdNetworkId(), SHOW_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            String errorReason = "Smart Ad Server rewarded video manager show general error.";
            if (rewardedVideoManager == null) {
                errorReason = "Smart Ad Server rewarded video manager not instantiated. Please load rewarded video again.";
            } else if (!rewardedVideoManager.hasRewardedVideo()) {
                errorReason = "Smart Ad Server rewarded video ad not loaded or expired. Please load rewarded video again.";
            }
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, errorReason);
        }
    }

    @Override
    protected void onInvalidate() {
        if (rewardedVideoManager != null) {
            rewardedVideoManager.onDestroy();
            rewardedVideoManager = null;
        }
    }
}
