package com.smartadserver.android.library.mediation;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPubReward;
import com.mopub.mobileads.CustomEventInterstitial;
import com.mopub.mobileads.CustomEventRewardedVideo;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubRewardedVideoManager;
import com.smartadserver.android.library.model.SASReward;
import com.smartadserver.android.library.ui.SASAdView;

import java.util.Map;

/**
 * Mopub adapter class for Smart AdServer SDK 6.6 rewarded video format
 */
public class SASMoPubCustomEventRewardedVideo extends CustomEventRewardedVideo {

    @Nullable
    @Override
    protected CustomEventRewardedVideoListener getVideoListenerForSdk() {
        return null;
    }

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

    /**
     * Return third party Id
     * @return
     */
    private String getThirdPartyId() {
        return getAdNetworkId();
    }


    @Override
    protected void onInvalidate() {
        if (moPubCustomEventInterstitial != null) {
            moPubCustomEventInterstitial.onInvalidate();
            moPubCustomEventInterstitial = null;
        }
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull Activity launcherActivity, @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) throws Exception {
        // nothing to initialize for smart SDK
        return true;
    }

    private boolean hasVideoAvailable = false;
    private SASMoPubCustomEventInterstitial moPubCustomEventInterstitial = new SASMoPubCustomEventInterstitial();
    ProxyListener proxyListener = new ProxyListener();

    private boolean wasShown = false;

    /**
     * Proxy class to delegate rewarded video ad call to a SASMoPubCustomEventInterstitial (as for Smart, rewarded video works as an interstitial)
     */
    class ProxyListener implements CustomEventInterstitial.CustomEventInterstitialListener, SASAdView.OnRewardListener {

        @Override
        public void onInterstitialLoaded() {
            hasVideoAvailable = moPubCustomEventInterstitial != null && moPubCustomEventInterstitial.hasVideo();
            if (hasVideoAvailable) {
                MoPubRewardedVideoManager.onRewardedVideoLoadSuccess(SASMoPubCustomEventRewardedVideo.class, getThirdPartyId());
            } else {
                onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            }
        }

        @Override
        public void onInterstitialFailed(MoPubErrorCode errorCode) {
            MoPubRewardedVideoManager.onRewardedVideoLoadFailure(SASMoPubCustomEventRewardedVideo.class,getThirdPartyId(),errorCode);
        }

        @Override
        public void onInterstitialShown() {
            MoPubRewardedVideoManager.onRewardedVideoStarted(SASMoPubCustomEventRewardedVideo.class,getThirdPartyId());
        }

        @Override
        public void onInterstitialClicked() {
            MoPubRewardedVideoManager.onRewardedVideoClicked(SASMoPubCustomEventRewardedVideo.class,getThirdPartyId());
        }

        @Override
        public void onLeaveApplication() {
            MoPubRewardedVideoManager.onRewardedVideoClicked(SASMoPubCustomEventRewardedVideo.class,getThirdPartyId());
        }

        @Override
        public void onInterstitialDismissed() {
            if (wasShown) {
                MoPubRewardedVideoManager.onRewardedVideoClosed(SASMoPubCustomEventRewardedVideo.class,getThirdPartyId());
            }
        }

        @Override
        public void onReward(SASReward sasReward) {
            MoPubReward reward = MoPubReward.success(sasReward.getCurrency(), (int)sasReward.getAmount());
            MoPubRewardedVideoManager.onRewardedVideoCompleted(SASMoPubCustomEventRewardedVideo.class, getThirdPartyId(), reward);
        }
    }


    @Override
    protected void loadWithSdkInitialized(@NonNull Activity activity, @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) throws Exception {

        // reset state variable
        hasVideoAvailable = false;
        wasShown = false;
        moPubCustomEventInterstitial.loadInterstitial(activity,proxyListener,localExtras,serverExtras);
    }

    @Override
    protected boolean hasVideoAvailable() {
        return hasVideoAvailable;
    }

    @Override
    protected void showVideo() {
        moPubCustomEventInterstitial.showInterstitial();
        wasShown = true;
    }
}
