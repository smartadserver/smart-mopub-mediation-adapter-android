package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.util.Log;

import com.mopub.mobileads.CustomEventBanner;
import com.mopub.mobileads.MoPubErrorCode;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASBannerView;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Class that handles a MoPub mediation banner ad call to Smart Display SDK.
 */
public class SASMoPubCustomEventBanner extends CustomEventBanner {

    private static final String TAG = "SASMoPubCustomEvent";

    // Smart banner view that will handle the mediation ad call
    private SASBannerView sasBannerView;


    @Override
    protected void loadBanner(Context context, final CustomEventBannerListener customEventBannerListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {

        // First configure the SDK and retrieve the SASAdPlacement
        if (context == null || serverExtras == null || !SASMoPubCustomEventUtil.configureSDKIfNeeded(context, serverExtras)) {
            // Invalid parameters
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getAdPlacementFromServerParams(serverExtras);

        if (adPlacement == null) {
            // Incorrect Smart placement
            customEventBannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        if (sasBannerView == null) {
            // Instantiate SASBannerView that will perform the Smart ad call
            sasBannerView = new SASBannerView(context);
        }

        sasBannerView.setBannerListener(new SASBannerView.BannerListener() {
            @Override
            public void onBannerAdLoaded(SASBannerView sasBannerView, SASAdElement sasAdElement) {
                Log.d(TAG, "Smart banner is loaded.");
                customEventBannerListener.onBannerLoaded(sasBannerView);
            }

            @Override
            public void onBannerAdFailedToLoad(SASBannerView sasBannerView, Exception e) {
                Log.d(TAG, "smart banner failed to load.");

                MoPubErrorCode errorCode = MoPubErrorCode.UNSPECIFIED;
                if (e instanceof SASNoAdToDeliverException) {
                    // No ad to deliver
                    errorCode = MoPubErrorCode.NO_FILL;
                } else if (e instanceof SASAdTimeoutException) {
                    // Ad request timeout translates to AdMob network error
                    errorCode = MoPubErrorCode.NETWORK_TIMEOUT;
                }

                customEventBannerListener.onBannerFailed(errorCode);
            }

            @Override
            public void onBannerAdClicked(SASBannerView sasBannerView) {
                Log.d(TAG, "Smart banner clicked.");
                customEventBannerListener.onBannerClicked();
            }

            @Override
            public void onBannerAdExpanded(SASBannerView sasBannerView) {
                Log.d(TAG, "Smart banner expanded.");
                customEventBannerListener.onBannerExpanded();
            }

            @Override
            public void onBannerAdCollapsed(SASBannerView sasBannerView) {
                Log.d(TAG, "Smart banner collapsed.");
                customEventBannerListener.onBannerCollapsed();
            }

            @Override
            public void onBannerAdResized(SASBannerView sasBannerView) {
                Log.d(TAG, "Smart banner resized.");
                // no equivalent
            }

            @Override
            public void onBannerAdClosed(SASBannerView sasBannerView) {
                Log.d(TAG, "Smart banner closed.");
                // no equivalent
            }

            @Override
            public void onBannerAdVideoEvent(SASBannerView sasBannerView, int i) {
                Log.d(TAG, "Smart banner video event: " + i);
                // no equivalent
            }
        });

        // Now request ad for this SASBannerView
        sasBannerView.loadAd(adPlacement);
    }

    @Override
    protected synchronized void onInvalidate() {
        if (sasBannerView != null) {
            sasBannerView.onDestroy();
            sasBannerView = null;
        }
    }
}
