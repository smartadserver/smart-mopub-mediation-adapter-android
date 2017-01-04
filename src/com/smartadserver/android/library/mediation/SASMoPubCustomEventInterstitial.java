package com.smartadserver.android.library.mediation;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.location.Location;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.mopub.common.MoPub;
import com.mopub.mobileads.CustomEventInterstitial;
import com.mopub.mobileads.MoPubErrorCode;
import com.smartadserver.android.library.SASBannerView;
import com.smartadserver.android.library.SASInterstitialView;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdElement;
import com.smartadserver.android.library.model.SASNativeVideoAdElement;
import com.smartadserver.android.library.ui.SASAdView;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Mopub adapter class for Smart AdServer SDK 6.6 interstitial format
 */
public class SASMoPubCustomEventInterstitial extends CustomEventInterstitial {


    // Smart interstitial view that will handle the mediation ad call
    SASInterstitialView sasInterstitialView;

    // Mopub interstitial listener
    CustomEventInterstitialListener interstitialListener;

    // Container view for offscreen interstitial loading (as SASInterstitialView is displayed immediately after successful loading)
    FrameLayout interstitialContainer;

    /**
     * utility method for rewarded video implementation
     * @return
     */
    public boolean hasVideo() {
        return sasInterstitialView != null && sasInterstitialView.getCurrentAdElement() instanceof SASNativeVideoAdElement;
    }

    @Override
    protected void loadInterstitial(Context context, CustomEventInterstitialListener customEventInterstitialListener, Map<String, Object> localExtras, Map<String, String> serverExtras) {

        SASUtil.debugModeEnabled = true;

        interstitialListener = customEventInterstitialListener;

        // get smart placement object
        SASMoPubCustomEventUtil.SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getPlacementFromMap(serverExtras);

        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            interstitialListener.onInterstitialFailed(MoPubErrorCode.ADAPTER_CONFIGURATION_ERROR);
        } else {
            if (sasInterstitialView == null) {
                // instantiate the AdResponseHandler to handle Smart ad call outcome
                SASAdView.AdResponseHandler adResponseHandler = new SASAdView.AdResponseHandler() {
                    @Override
                    public void adLoadingCompleted(SASAdElement sasAdElement) {
                        //  notify AdMob of AdLoaded
                        synchronized(SASMoPubCustomEventInterstitial.this) {
                            if (sasInterstitialView != null) {
                                sasInterstitialView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        interstitialListener.onInterstitialLoaded();
                                    }
                                });
                            }
                        }
                    }

                    @Override
                    public void adLoadingFailed(final Exception e) {
                        // notify admob that ad call has failed with appropriate eror code
                        synchronized(SASMoPubCustomEventInterstitial.this) {
                            if (sasInterstitialView != null) {
                                sasInterstitialView.post(new Runnable() {
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
                                        interstitialListener.onInterstitialFailed(errorCode);
                                    }
                                });
                            }
                        }
                    }
                };

                // instantiate SASInterstitialView that will perform the Smart ad call
                sasInterstitialView = new SASInterstitialView(context) {

                    /**
                     * Overriden to notify ad mob that the ad was opened
                     */
                    @Override
                    public void open(String url) {
                        super.open(url);
                        if (isAdWasOpened()) {
                            SASAdElement adElement = sasInterstitialView.getCurrentAdElement();
                            final boolean openInApp = adElement.isOpenClickInApplication();
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!openInApp) {
                                        interstitialListener.onLeaveApplication();
                                    } else {
                                        interstitialListener.onInterstitialClicked();
                                    }
                                }
                            });
                        }
                    }
                };

                // add state change listener to detect when ad is closed or loaded and expanded (=ready to be displayed)
                sasInterstitialView.addStateChangeListener(new SASAdView.OnStateChangeListener() {
                    public void onStateChanged(
                            SASAdView.StateChangeEvent stateChangeEvent) {
                        switch (stateChangeEvent.getType()) {
                            case SASAdView.StateChangeEvent.VIEW_HIDDEN:
                                // ad was closed
                                ViewParent parent = interstitialContainer.getParent();
                                if (parent instanceof ViewGroup) {
                                    ((ViewGroup)parent).removeView(interstitialContainer);
                                }

                                // defer onInterstitialDismissed() call not to conflict with onInterstitialClicked/onLeaveApplication
                                // that occurred just before when clicking on the interstitial (otherwise click pixel not called)
                                sasInterstitialView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        interstitialListener.onInterstitialDismissed();
                                    }
                                });
                                break;
                        }
                    }
                });

                // useful for rewarded video adapter
                if (interstitialListener instanceof SASAdView.OnRewardListener) {
                    sasInterstitialView.addRewardListener((SASAdView.OnRewardListener)interstitialListener);
                }

                // create the (offscreen) FrameLayout that the SASInterstitialView will expand into
                if (interstitialContainer == null) {
                    interstitialContainer = new FrameLayout(context);
                    interstitialContainer.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                }

                sasInterstitialView.setExpandParentContainer(interstitialContainer);

//                // detect layout changes to update padding
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    sasInterstitialView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                            // on layout change, add a globalLayoutListener to apply padding once layout is done (and not to early)
                            sasInterstitialView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    sasInterstitialView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    Rect r = new Rect();
                                    ViewParent parentView = interstitialContainer.getParent();
                                    if (parentView instanceof View) {
                                        ((View)parentView).getWindowVisibleDisplayFrame(r);
                                        int topPadding = r.top;
                                        // handle navigation bar overlay by adding padding
                                        int leftPadding = r.left;
                                        int bottomPadding = Math.max(0,((View)parentView).getHeight() - r.bottom);
                                        int rightPadding =  Math.max(0,((View)parentView).getWidth() - r.right);
                                        interstitialContainer.setPadding(leftPadding,topPadding,rightPadding,bottomPadding);
                                    }
                                }
                            });
                        }
                    });
                }

                // pass received location on to SASBannerView
                boolean locationEnabled = !(MoPub.getLocationAwareness() == MoPub.LocationAwareness.DISABLED);
                SASUtil.setAllowAutomaticLocationDetection(locationEnabled);

                // Now request ad for this SASBannerView
                sasInterstitialView.loadAd(adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,true,
                        adPlacement.targeting,adResponseHandler,10000);

            }
        }

    }

    /**
     * Implementation of CustomEventInterstitial interface method.
     * Displays the previously loaded SASInterstitialView, if any
     */
    @Override
    protected void showInterstitial() {

        // find the rootView where to add the interstitialContainer
        View rootContentView = null;
        Context context = sasInterstitialView.getContext();
        if (context instanceof Activity) {
            // try to find root view via Activity if available
            rootContentView = ((Activity)context).getWindow().getDecorView();
        }

        // now actually add the interstitialContainer including appropriate padding fir status/navigation bars
        if (rootContentView instanceof ViewGroup) {
            ((ViewGroup)rootContentView).addView(interstitialContainer);

            // notify Mopub listener that ad was presented full screen
            if (interstitialListener != null) {
                interstitialListener.onInterstitialShown();
            }
        }
    }

    @Override
    protected synchronized void onInvalidate() {
        if (sasInterstitialView != null) {
            if (interstitialListener instanceof SASAdView.OnVideoEventListener) {
                sasInterstitialView.removeVideoEventListener((SASAdView.OnVideoEventListener)interstitialListener);
            }
            if (interstitialListener instanceof SASAdView.OnRewardListener) {
                sasInterstitialView.removeRewardListener((SASAdView.OnRewardListener)interstitialListener);
            }
            sasInterstitialView.setExpandParentContainer(null);
            sasInterstitialView.onDestroy();
            sasInterstitialView = null;
        }
    }
}
