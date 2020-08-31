package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.LifecycleListener;
import com.mopub.common.logging.MoPubLog;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASBannerView;
import com.smartadserver.android.library.ui.SASInterstitialManager;
import com.smartadserver.android.library.util.SASUtil;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.DID_APPEAR;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.DID_DISAPPEAR;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class SmartAdServerInterstitial extends BaseAd {

    private static final String ADAPTER_NAME = SmartAdServerInterstitial.class.getSimpleName();

    // Smart banner view that will handle the mediation ad call
    private SASInterstitialManager interstitialManager;

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

        if (interstitialManager == null) {
            // Instantiate SASInterstitialManager that will perform the Smart ad call
            interstitialManager = new SASInterstitialManager(context, adPlacement);

            // And set appropriate listener for callbacks
            interstitialManager.setInterstitialListener(new SASInterstitialManager.InterstitialListener() {
                @Override
                public void onInterstitialAdLoaded(SASInterstitialManager sasInterstitialManager, SASAdElement sasAdElement) {
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
                public void onInterstitialAdFailedToLoad(SASInterstitialManager sasInterstitialManager, Exception e) {
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
                public void onInterstitialAdShown(SASInterstitialManager sasInterstitialManager) {
                    MoPubLog.log(getAdNetworkId(), SHOW_SUCCESS, ADAPTER_NAME);
                    MoPubLog.log(getAdNetworkId(), DID_APPEAR, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdShown();
                    }
                }

                @Override
                public void onInterstitialAdFailedToShow(SASInterstitialManager sasInterstitialManager, Exception e) {
                    // notify MoPub of interstitial show error
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR);
                    }
                    MoPubLog.log(getAdNetworkId(), SHOW_FAILED, ADAPTER_NAME,
                            MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, e.getMessage());
                }

                @Override
                public void onInterstitialAdClicked(SASInterstitialManager sasInterstitialManager) {
                    MoPubLog.log(getAdNetworkId(), CLICKED, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdClicked();
                    }
                }

                @Override
                public void onInterstitialAdDismissed(SASInterstitialManager sasInterstitialManager) {
                    MoPubLog.log(getAdNetworkId(), DID_DISAPPEAR, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdDismissed();
                    }
                }

                @Override
                public void onInterstitialAdVideoEvent(SASInterstitialManager sasInterstitialManager, int i) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server interstitial video event: " + i);
                    // no equivalent
                }
            });

        }

        // Now request ad for this SASInterstitialManager
        MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);
        interstitialManager.loadAd();
    }

    @Override
    protected void show() {

        MoPubLog.log(getAdNetworkId(), SHOW_ATTEMPTED, ADAPTER_NAME);

        if (interstitialManager != null && interstitialManager.isShowable()) {
            interstitialManager.show();
        } else {

            // notify MoPub of interstitial show error
            if (mInteractionListener != null) {
                mInteractionListener.onAdFailed(MoPubErrorCode.FULLSCREEN_SHOW_ERROR);
            }

            MoPubLog.log(getAdNetworkId(), SHOW_FAILED, ADAPTER_NAME,
                    MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            String errorReason = "Smart Ad Server interstitial manager show general error.";
            if (interstitialManager == null) {
                errorReason = "Smart Ad Server interstitial manager not instantiated. Please load interstitial again.";
            } else if (!interstitialManager.isShowable()) {
                errorReason = "Smart Ad Server interstitial ad not loaded or expired. Please load interstitial again.";
            }
            MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, errorReason);
        }
    }

    @Override
    protected void onInvalidate() {
        if (interstitialManager != null) {
            interstitialManager.onDestroy();
            interstitialManager = null;
        }
    }
}
