package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.LifecycleListener;
import com.mopub.common.logging.MoPubLog;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.headerbidding.SASBiddingAdResponse;
import com.smartadserver.android.library.headerbidding.SASBiddingAdStorage;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.ui.SASBannerView;
import com.smartadserver.android.library.util.SASUtil;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;

public class SmartAdServerBanner extends BaseAd {

    private static final String ADAPTER_NAME = SmartAdServerBanner.class.getSimpleName();

    // Smart banner view that will handle the mediation ad call
    private SASBannerView sasBannerView;

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

        if (sasBannerView == null) {
            // Instantiate SASBannerView that will perform the Smart ad call
            sasBannerView = new SASBannerView(context);

            // and set appropriate banner listener for callbacks
            sasBannerView.setBannerListener(new SASBannerView.BannerListener() {

                @Override
                public void onBannerAdLoaded(SASBannerView sasBannerView, SASAdElement sasAdElement) {
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
                public void onBannerAdFailedToLoad(SASBannerView sasBannerView, Exception e) {
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
                public void onBannerAdClicked(SASBannerView sasBannerView) {
                    MoPubLog.log(getAdNetworkId(), CLICKED, ADAPTER_NAME);
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdClicked();
                    }
                }

                @Override
                public void onBannerAdExpanded(SASBannerView sasBannerView) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server banner ad expanded");
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdExpanded();
                    }
                }

                @Override
                public void onBannerAdCollapsed(SASBannerView sasBannerView) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server banner ad collapsed");
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdCollapsed();
                    }
                }

                @Override
                public void onBannerAdResized(SASBannerView sasBannerView) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server banner ad resized");
                    // no equivalent
                }

                @Override
                public void onBannerAdClosed(SASBannerView sasBannerView) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server banner ad closed");
                    if (mInteractionListener != null) {
                        mInteractionListener.onAdDismissed();
                    }
                }

                @Override
                public void onBannerAdVideoEvent(SASBannerView sasBannerView, int i) {
                    MoPubLog.log(getAdNetworkId(), CUSTOM, ADAPTER_NAME, "Smart Ad Server banner video event: " + i);
                    // no equivalent
                }
            });
        }

        // Now request ad for this SASBannerView
        MoPubLog.log(getAdNetworkId(), LOAD_ATTEMPTED, ADAPTER_NAME);

        String biddingAdId =  adData.getExtras().get("SAS_BIDDING_AD_ID");
        SASBiddingAdResponse biddingAdResponse = SASBiddingAdStorage.getSharedInstance().getBiddingAd(biddingAdId);
        if (biddingAdResponse != null) {
            sasBannerView.loadAd(biddingAdResponse);
        } else {
            sasBannerView.loadAd(adPlacement);
        }


    }

    @Nullable
    @Override
    protected View getAdView() {
        return sasBannerView;
    }

    @Override
    protected void onInvalidate() {
        if (sasBannerView != null) {
            sasBannerView.onDestroy();
            sasBannerView = null;
        }
    }
}
