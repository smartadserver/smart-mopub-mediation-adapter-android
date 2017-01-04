package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.location.Location;

import com.mopub.common.MoPub;
import com.mopub.mobileads.CustomEventBanner;
import com.mopub.mobileads.MoPubErrorCode;
import com.smartadserver.android.library.SASBannerView;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.ui.SASAdView;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Mopub adapter class for Smart AdServer SDK 6.6 Banner format
 */
public class SASMoPubCustomEventBanner extends CustomEventBanner {

    // Smart banner view that will handle the mediation ad call
    SASBannerView sasBannerView;

    // moPub banner listener
    private CustomEventBannerListener bannerListener;

    @Override
    protected void loadBanner(Context context, CustomEventBannerListener customEventBannerListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {

        bannerListener = customEventBannerListener;

        // get smart placement object
        SASMoPubCustomEventUtil.SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getPlacementFromMap(serverExtras);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            bannerListener.onBannerFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        } else {
            if (sasBannerView == null) {
                // instantiate the AdResponseHandler to handle Smart ad call outcome
                SASAdView.AdResponseHandler adResponseHandler = new SASAdView.AdResponseHandler() {
                    @Override
                    public void adLoadingCompleted(SASAdElement sasAdElement) {
                        synchronized (SASMoPubCustomEventBanner.this) {
                            if (sasBannerView != null) {
                                sasBannerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // notify admob that ad call has succeeded
                                        bannerListener.onBannerLoaded(sasBannerView);
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void adLoadingFailed(final Exception e) {
                        // notify admob that ad call has failed with appropriate eror code
                        synchronized (SASMoPubCustomEventBanner.this) {
                            if (sasBannerView != null) {
                                sasBannerView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // default generic error code
                                        MoPubErrorCode errorCode = MoPubErrorCode.UNSPECIFIED;
                                        if (e instanceof SASNoAdToDeliverException) {
                                            // no ad to deliver
                                            errorCode = MoPubErrorCode.NO_FILL;
                                        } else if (e instanceof SASAdTimeoutException) {
                                            // ad request timeout translates to admob network error
                                            errorCode = MoPubErrorCode.NETWORK_TIMEOUT;
                                        }
                                        bannerListener.onBannerFailed(errorCode);
                                    }
                                });
                            }
                        }
                    }
                };

                // instantiate SASBannerView that will perform the Smart ad call
                sasBannerView = new SASBannerView(context) {

                    /**
                     * Overriden to notify ad mob that the ad was opened
                     */
                    @Override
                    public void open(String url) {
                        super.open(url);
                        if (isAdWasOpened()) {
                            SASAdElement adElement = sasBannerView.getCurrentAdElement();
                            final boolean openInApp = adElement.isOpenClickInApplication();
                            sasBannerView.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!openInApp) {
                                        bannerListener.onLeaveApplication();
                                    } else {
                                        bannerListener.onBannerClicked();
                                    }
                                }
                            });
                        }
                    }

                    /**
                     * Overriden to force banner size to received admob size if not expanded
                     * @param params
                     */
//                    @Override
//                    public void setLayoutParams(ViewGroup.LayoutParams params) {
//                        if (!sasBannerView.isExpanded()) {
//                            params.height = adSize.getHeightInPixels(context);
//                            params.width = adSize.getWidthInPixels(context);
//                        }
//                        super.setLayoutParams(params);
//                    }
                };

                // add state change listener to detect when ad is closed
                sasBannerView.addStateChangeListener(new SASAdView.OnStateChangeListener() {
                    boolean wasOpened = false;
                    public void onStateChanged(
                            SASAdView.StateChangeEvent stateChangeEvent) {
                        switch (stateChangeEvent.getType()) {
                            case SASAdView.StateChangeEvent.VIEW_EXPANDED:
                                // ad was expanded
                                bannerListener.onBannerExpanded();
                                wasOpened = true;
                                break;
                            case SASAdView.StateChangeEvent.VIEW_DEFAULT:
                                // ad was collapsed
                                if (wasOpened) {
                                    bannerListener.onBannerCollapsed();
                                    wasOpened = false;
                                }
                                break;
                        }
                    }
                });

                // pass received location on to SASBannerView
                boolean locationEnabled = !(MoPub.getLocationAwareness() == MoPub.LocationAwareness.DISABLED);
                SASUtil.setAllowAutomaticLocationDetection(locationEnabled);

                // Now request ad for this SASBannerView
                sasBannerView.loadAd(adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,true,
                        adPlacement.targeting, adResponseHandler,10000);

            }
        }
    }

    @Override
    protected synchronized void onInvalidate() {
        if (sasBannerView != null) {
            sasBannerView.onDestroy();
            sasBannerView = null;
        }
    }
}
