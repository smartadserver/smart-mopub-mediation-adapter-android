package com.smartadserver.android.library.mediation;

import android.content.Context;

import com.mopub.mobileads.CustomEventInterstitial;
import com.mopub.mobileads.MoPubErrorCode;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASInterstitialManager;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Class that handles a MoPub mediation interstitial ad call to Smart Display SDK.
 */
public class SASMoPubCustomEventInterstitial extends CustomEventInterstitial {

    private static final String TAG = "SASMoPubCustomEvent";

    // Smart interstitial manager that will handle the mediation ad call
    private SASInterstitialManager interstitialManager;

    @Override
    protected void loadInterstitial(Context context, final CustomEventInterstitialListener customEventInterstitialListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {

        // First configure the SDK and retrieve the SASAdPlacement
        if (context == null || serverExtras == null || !SASMoPubCustomEventUtil.configureSDKIfNeeded(context, serverExtras)) {
            // Invalid parameters
            customEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getAdPlacementFromServerParams(serverExtras);

        if (adPlacement == null) {
            // Incorrect Smart placement
            customEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        // Instantiate the Smart interstitial manager
        interstitialManager = new SASInterstitialManager(context, adPlacement);

        // Then set interstitial manager's listener
        interstitialManager.setInterstitialListener(new SASInterstitialManager.InterstitialListener() {
            @Override
            public void onInterstitialAdLoaded(SASInterstitialManager sasInterstitialManager, SASAdElement sasAdElement) {
                SASUtil.logDebug(TAG, "Smart interstitial is loaded.");
                customEventInterstitialListener.onInterstitialLoaded();
            }

            @Override
            public void onInterstitialAdFailedToLoad(SASInterstitialManager sasInterstitialManager, Exception e) {
                SASUtil.logDebug(TAG, "Smart intersititial failed to load.");

                // Default generic error code
                MoPubErrorCode errorCode = MoPubErrorCode.UNSPECIFIED;
                if (e instanceof SASNoAdToDeliverException) {
                    // No ad to deliver
                    errorCode = MoPubErrorCode.NO_FILL;
                } else if (e instanceof SASAdTimeoutException) {
                    // Ad request timeout translates to AdMob network error
                    errorCode = MoPubErrorCode.NETWORK_TIMEOUT;
                }

                customEventInterstitialListener.onInterstitialFailed(errorCode);
            }

            @Override
            public void onInterstitialAdShown(SASInterstitialManager sasInterstitialManager) {
                SASUtil.logDebug(TAG, "Smart interstitial shown.");
                customEventInterstitialListener.onInterstitialShown();
            }

            @Override
            public void onInterstitialAdFailedToShow(SASInterstitialManager sasInterstitialManager, Exception e) {
                SASUtil.logDebug(TAG, "Smart interstitial failed to show.");
                customEventInterstitialListener.onInterstitialFailed(MoPubErrorCode.VIDEO_PLAYBACK_ERROR);
            }

            @Override
            public void onInterstitialAdClicked(SASInterstitialManager sasInterstitialManager) {
                SASUtil.logDebug(TAG, "Smart interstitial clicked.");
                customEventInterstitialListener.onInterstitialClicked();
            }

            @Override
            public void onInterstitialAdDismissed(SASInterstitialManager sasInterstitialManager) {
                SASUtil.logDebug(TAG, "Smart interstitial dismissed.");
                customEventInterstitialListener.onInterstitialDismissed();
            }

            @Override
            public void onInterstitialAdVideoEvent(SASInterstitialManager sasInterstitialManager, int i) {
                SASUtil.logDebug(TAG, "Smart interstitial video event: " + i);
                // No equivalent
            }
        });

        // Load the interstitial
        interstitialManager.loadAd();
    }

    @Override
    protected void showInterstitial() {
        if (interstitialManager != null && interstitialManager.isShowable()) {
            interstitialManager.show();
        }
    }

    @Override
    protected synchronized void onInvalidate() {
        if (interstitialManager != null) {
            interstitialManager.setInterstitialListener(null);
            interstitialManager.onDestroy();
            interstitialManager = null;
        }
    }
}
