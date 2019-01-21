package com.smartadserver.android.library.mediation;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewGroup;

import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPubReward;
import com.mopub.mobileads.CustomEventRewardedVideo;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideoManager;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.rewarded.SASRewardedVideoManager;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Class that handles a MoPub mediation rewarded video ad call to Smart Display SDK.
 */
public class SASMoPubCustomEventRewardedVideo extends CustomEventRewardedVideo {

    static private final String TAG = "SASMoPubCustomEvent";

    // Smart rewarded video manager that will handle the mediation ad call
    private SASRewardedVideoManager rewardedVideoManager;

    @Nullable
    @Override
    protected LifecycleListener getLifecycleListener() {
        return null;
    }

    @NonNull
    @Override
    protected String getAdNetworkId() {
        return "Smart AdServer";
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull Activity launcherActivity, @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) throws Exception {
        // Configure the Smart Display SDK
        return SASMoPubCustomEventUtil.configureSDKIfNeeded(launcherActivity, serverExtras);
    }

    @Override
    protected void loadWithSdkInitialized(@NonNull Activity activity, @NonNull final Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) throws Exception {
        // SDK already configured, we just have to retrieve the ad placement
        SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getAdPlacementFromServerParams(serverExtras);

        if (adPlacement == null) {
            // Invalid Smart ad placement object
            return;
        }

        // Instantiate the Smart rewarded video manager
        rewardedVideoManager = new SASRewardedVideoManager(activity, adPlacement);

        // Then set the rewarded video manager's listener
        rewardedVideoManager.setRewardedVideoListener(new SASRewardedVideoManager.RewardedVideoListener() {
            @Override
            public void onRewardedVideoAdLoaded(SASRewardedVideoManager sasRewardedVideoManager, SASAdElement sasAdElement) {
                SASUtil.logDebug(TAG, "Smart rewarded video loaded");
                MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId());
            }

            @Override
            public void onRewardedVideoAdFailedToLoad(SASRewardedVideoManager sasRewardedVideoManager, Exception e) {
                SASUtil.logDebug(TAG, "Smart rewarded video failed to load");

                // Default generic error code
                MoPubErrorCode errorCode = MoPubErrorCode.UNSPECIFIED;
                if (e instanceof SASNoAdToDeliverException) {
                    // No ad to deliver
                    errorCode = MoPubErrorCode.NO_FILL;
                } else if (e instanceof SASAdTimeoutException) {
                    // Ad request timeout translates to AdMob network error
                    errorCode = MoPubErrorCode.NETWORK_TIMEOUT;
                }

                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId(), errorCode);
            }

            @Override
            public void onRewardedVideoAdShown(SASRewardedVideoManager sasRewardedVideoManager) {
                SASUtil.logDebug(TAG, "Smart rewarded video shown");
                MoPubRewardedVideoManager.onRewardedVideoStarted(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId());
            }

            @Override
            public void onRewardedVideoAdFailedToShow(SASRewardedVideoManager sasRewardedVideoManager, Exception e) {
                SASUtil.logDebug(TAG, "Smart rewarded video failed to show");
                MoPubRewardedVideoManager.onRewardedVideoLoadFailure(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId(), MoPubErrorCode.VIDEO_PLAYBACK_ERROR);
            }

            @Override
            public void onRewardedVideoAdClosed(SASRewardedVideoManager sasRewardedVideoManager) {
                SASUtil.logDebug(TAG, "Smart rewarded video closed");
                MoPubRewardedVideoManager.onRewardedVideoClosed(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId());
            }

            @Override
            public void onRewardReceived(SASRewardedVideoManager sasRewardedVideoManager, SASReward sasReward) {
                SASUtil.logDebug(TAG, "Smart rewarded video received reward: " + sasReward);
                MoPubReward reward = MoPubReward.success(sasReward.getCurrency(), (int) sasReward.getAmount());
                MoPubRewardedVideoManager.onRewardedVideoCompleted(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId(), reward);
            }

            @Override
            public void onRewardedVideoAdClicked(SASRewardedVideoManager sasRewardedVideoManager) {
                SASUtil.logDebug(TAG, "Smart rewarded video clicked");
                MoPubRewardedVideoManager.onRewardedVideoClicked(SASMoPubCustomEventRewardedVideo.class, getAdNetworkId());
            }

            @Override
            public void onRewardedVideoEvent(SASRewardedVideoManager sasRewardedVideoManager, int i) {
                SASUtil.logDebug(TAG, "Smart rewarded video event: " + i);
            }

            @Override
            public void onRewardedVideoEndCardDisplayed(SASRewardedVideoManager sasRewardedVideoManager, ViewGroup viewGroup) {
                SASUtil.logDebug(TAG, "Smart rewarded video loaded");
                // No equivalent
            }
        });

        rewardedVideoManager.loadRewardedVideo();
    }

    @Override
    protected void showVideo() {
        if (rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo()) {
            rewardedVideoManager.showRewardedVideo();
        }
    }

    @Override
    protected boolean hasVideoAvailable() {
        return rewardedVideoManager != null && rewardedVideoManager.hasRewardedVideo();
    }

    @Override
    protected void onInvalidate() {
        if (rewardedVideoManager != null) {
            rewardedVideoManager.setRewardedVideoListener(null);
            rewardedVideoManager.onDestroy();
            rewardedVideoManager = null;
        }
    }


}
