package com.smartadserver.android.library.mediation;

import android.content.Context;
import android.support.annotation.NonNull;
import android.view.View;

import com.mopub.common.MoPub;
import com.mopub.nativeads.BaseNativeAd;
import com.mopub.nativeads.CustomEventNative;
import com.mopub.nativeads.MoPubCustomEventVideoNative;
import com.mopub.nativeads.NativeErrorCode;
import com.mopub.nativeads.StaticNativeAd;
import com.mopub.nativeads.VideoNativeAd;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASNativeAdElement;
import com.smartadserver.android.library.model.SASNativeAdManager;
import com.smartadserver.android.library.model.SASNativeAdPlacement;
import com.smartadserver.android.library.model.SASNativeVideoAdElement;
import com.smartadserver.android.library.ui.SASAdChoicesView;
import com.smartadserver.android.library.util.SASConstants;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Mopub adapter class for Smart AdServer SDK 6.6 native ad format
 */
public class SASMoPubCustomEventNative extends CustomEventNative {

    CustomEventNativeListener nativeListener;

    SASNativeAdManager nativeAdManager;

    /**
     * Class representing a native ad with static content (no video)
     */
    public static class SASStaticNativeAd extends StaticNativeAd {

        SASNativeAdElement sasNativeAdElement;

        public SASStaticNativeAd(SASNativeAdElement sasNativeAdElement) {

            this.sasNativeAdElement = sasNativeAdElement;

            this.sasNativeAdElement.setClickHandler(new SASNativeAdElement.ClickHandler() {
                @Override
                public boolean handleClick(String s, SASNativeAdElement sasNativeAdElement) {
                    notifyAdClicked();
                    return false;
                }
            });

            setCallToAction(sasNativeAdElement.getCalltoAction());
//            setClickDestinationUrl(sasNativeAdElement.getClickUrl());
            if (sasNativeAdElement.getIcon() != null) {
                setIconImageUrl(sasNativeAdElement.getIcon().getUrl());
            }
            if (sasNativeAdElement.getCoverImage() != null) {
                setMainImageUrl(sasNativeAdElement.getCoverImage().getUrl());
            }
            setTitle(sasNativeAdElement.getTitle());
            setText(sasNativeAdElement.getSubtitle());
            setStarRating((double)sasNativeAdElement.getRating());

        }

        @Override
        public void prepare(@NonNull View view) {
            notifyAdImpressed();
            this.sasNativeAdElement.registerView(view);
        }

        @Override
        public void clear(@NonNull View view) {
            this.sasNativeAdElement.unregisterView(view);
        }
    }

    /**
     * Class representing a native ad with video content
     */
    public static class SASVideoNativeAd extends BaseNativeAd {

        SASNativeAdElement sasNativeAdElement;

        public SASVideoNativeAd(SASNativeAdElement sasNativeAdElement) {

            this.sasNativeAdElement = sasNativeAdElement;

            this.sasNativeAdElement.setClickHandler(new SASNativeAdElement.ClickHandler() {
                @Override
                public boolean handleClick(String s, SASNativeAdElement sasNativeAdElement) {
                    notifyAdClicked();
                    return false;
                }
            });
        }

        @Override
        public void prepare(@NonNull View view) {
            notifyAdImpressed();
            this.sasNativeAdElement.registerView(view);
        }

        @Override
        public void clear(@NonNull View view) {
            this.sasNativeAdElement.unregisterView(view);
        }

        @Override
        public void destroy() {
            // nothing to do
        }

        public String getTitle() {
            return sasNativeAdElement.getTitle();
        }

        public String getText() {
            return sasNativeAdElement.getSubtitle();
        }

        public String getCallToAction() {
            return sasNativeAdElement.getCalltoAction();
        }

        public String getMainImageUrl() {
            String mainImageUrl = null;
            if (sasNativeAdElement.getCoverImage() != null) {
                mainImageUrl = sasNativeAdElement.getCoverImage().getUrl();
            }
            return  mainImageUrl;
        }

        public String getIconImageUrl() {
            String iconUrl = null;
            if (sasNativeAdElement.getIcon() != null) {
                iconUrl = sasNativeAdElement.getIcon().getUrl();
            }
            return  iconUrl;
        }

        public String getPrivacyInformationIconImageUrl() {
            return null;
        }

        public String getPrivacyInformationIconClickThroughUrl() {
            return "http://smartadserver.fr/societe/politique-de-confidentialite/";
        }

        public SASNativeAdElement getSASNativeAd() {
            return sasNativeAdElement;
        }
    }

    @Override
    /**
     *
     */
    protected void loadNativeAd(@NonNull Context context, @NonNull CustomEventNativeListener customEventNativeListener,
                                @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) {

        nativeListener = customEventNativeListener;

        // get smart placement object
        SASMoPubCustomEventUtil.SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getPlacementFromMap(serverExtras);

        // no placement -> exit on error
        if (adPlacement == null) {
            // incorrect smart placement : exit in error
            nativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR);
        } else {
            // create native ad manager
            if (nativeAdManager == null) {
                nativeAdManager = new SASNativeAdManager(context,new SASNativeAdPlacement(SASConstants.DEFAULT_BASE_URL,
                                                adPlacement.siteId,adPlacement.pageId,adPlacement.formatId,adPlacement.targeting));

                // create NativeAdResponseHandler to handle native ad call response
                SASNativeAdManager.NativeAdResponseHandler nativeAdResponseHandler = new SASNativeAdManager.NativeAdResponseHandler() {
                    @Override
                    /**
                     * Native ad succeeded
                     */
                    public void nativeAdLoadingCompleted(final SASNativeAdElement sasNativeAdElement) {

                        BaseNativeAd baseNativeAd = null;

                        SASNativeVideoAdElement videoAdElement = sasNativeAdElement.getMediaElement();

                        boolean videoRendererAvailable = false;
                        try {
                            Class.forName("com.mopub.nativeads.SASNativeVideoAdRenderer");
                            videoRendererAvailable = true;
                        } catch (ClassNotFoundException e) { }

                        // create a native video ad only if renderer is available
                        if (videoAdElement != null && videoRendererAvailable) {
                            baseNativeAd = new SASVideoNativeAd(sasNativeAdElement);
                        } else {
                            baseNativeAd = new SASStaticNativeAd(sasNativeAdElement);
                        }

                        final BaseNativeAd finalNativeAd = baseNativeAd;
                        // must be executed in Main thread
                        SASUtil.getMainLooperHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                nativeListener.onNativeAdLoaded(finalNativeAd);
                            }
                        });
                    }

                    @Override
                    /**
                     * Native ad failed
                     */
                    public void nativeAdLoadingFailed(Exception e) {
                        nativeListener.onNativeAdFailed(NativeErrorCode.NETWORK_NO_FILL);

                        NativeErrorCode errorCode = NativeErrorCode.UNSPECIFIED;
                        if (e instanceof SASNoAdToDeliverException) {
                            // no ad to deliver
                            errorCode = NativeErrorCode.NETWORK_NO_FILL;
                        } else if (e instanceof SASAdTimeoutException) {
                            // ad request timeout translates to admob network error
                            errorCode = NativeErrorCode.NETWORK_TIMEOUT;
                        }

                        // must be executed in Main thread
                        final NativeErrorCode finalCode = errorCode;
                        SASUtil.getMainLooperHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                nativeListener.onNativeAdFailed(finalCode);
                            }
                        });
                    }
                };

                // pass received location on to SASBannerView
                boolean locationEnabled = !(MoPub.getLocationAwareness() == MoPub.LocationAwareness.DISABLED);
                SASUtil.setAllowAutomaticLocationDetection(locationEnabled);

                nativeAdManager.requestNativeAd(nativeAdResponseHandler,10000);
            }

        }

    }
}
