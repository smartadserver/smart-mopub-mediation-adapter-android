package com.smartadserver.android.library.mediation;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.View;

import com.mopub.nativeads.BaseNativeAd;
import com.mopub.nativeads.CustomEventNative;
import com.mopub.nativeads.NativeErrorCode;
import com.mopub.nativeads.StaticNativeAd;
import com.smartadserver.android.library.exception.SASAdTimeoutException;
import com.smartadserver.android.library.exception.SASNoAdToDeliverException;
import com.smartadserver.android.library.model.SASAdPlacement;
import com.smartadserver.android.library.model.SASNativeAdElement;
import com.smartadserver.android.library.model.SASNativeAdManager;
import com.smartadserver.android.library.model.SASNativeVideoAdElement;
import com.smartadserver.android.library.util.SASUtil;

import java.util.Map;

/**
 * Class that handles a MoPub mediation native ad call to Smart Display SDK.
 */
public class SASMoPubCustomEventNative extends CustomEventNative {

    /**
     * Class representing a MoPub native ad with static content (no video).
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
            setClickDestinationUrl(sasNativeAdElement.getClickUrl());
            setTitle(sasNativeAdElement.getTitle());
            setText(sasNativeAdElement.getSubtitle());
            setStarRating((double) sasNativeAdElement.getRating());

            if (sasNativeAdElement.getIcon() != null) {
                setIconImageUrl(sasNativeAdElement.getIcon().getUrl());
            }

            if (sasNativeAdElement.getCoverImage() != null) {
                setMainImageUrl(sasNativeAdElement.getCoverImage().getUrl());
            }


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
     * Class representing a MoPub native ad with video content.
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
            return mainImageUrl;
        }

        public String getIconImageUrl() {
            String iconUrl = null;
            if (sasNativeAdElement.getIcon() != null) {
                iconUrl = sasNativeAdElement.getIcon().getUrl();
            }
            return iconUrl;
        }

        public String getPrivacyInformationIconImageUrl() {
            return null;
        }

        public String getPrivacyInformationIconClickThroughUrl() {
            return "https://smartadserver.fr/societe/politique-de-confidentialite/";
        }

        public SASNativeAdElement getSASNativeAd() {
            return sasNativeAdElement;
        }
    }

    @Override
    protected void loadNativeAd(@NonNull Context context, @NonNull final CustomEventNativeListener customEventNativeListener,
                                @NonNull Map<String, Object> localExtras, @NonNull Map<String, String> serverExtras) {

        // First, configure the Smart Display SDK
        if (!SASMoPubCustomEventUtil.configureSDKIfNeeded(context, serverExtras)) {
            // Error during configuration
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        // Get the Smart ad placement
        SASAdPlacement adPlacement = SASMoPubCustomEventUtil.getAdPlacementFromServerParams(serverExtras);

        if (adPlacement == null) {
            // Invalid Smart ad placement
            customEventNativeListener.onNativeAdFailed(NativeErrorCode.NATIVE_ADAPTER_CONFIGURATION_ERROR);
            return;
        }

        // Create native ad manager
        SASNativeAdManager nativeAdManager = new SASNativeAdManager(context, adPlacement);

        // Set the native ad manager listener
        nativeAdManager.setNativeAdListener(new SASNativeAdManager.NativeAdListener() {
            @Override
            public void onNativeAdLoaded(SASNativeAdElement sasNativeAdElement) {
                BaseNativeAd baseNativeAd;

                SASNativeVideoAdElement videoAdElement = sasNativeAdElement.getMediaElement();

                boolean videoRendererAvailable = false;
                try {
                    Class.forName("com.mopub.nativeads.SASNativeVideoAdRenderer");
                    videoRendererAvailable = true;
                } catch (ClassNotFoundException ignored) {
                }

                // Create a native video ad only if renderer is available
                if (videoAdElement != null && videoRendererAvailable) {
                    baseNativeAd = new SASVideoNativeAd(sasNativeAdElement);
                } else {
                    baseNativeAd = new SASStaticNativeAd(sasNativeAdElement);
                }

                final BaseNativeAd finalNativeAd = baseNativeAd;
                // Must be executed in Main thread
                SASUtil.getMainLooperHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        customEventNativeListener.onNativeAdLoaded(finalNativeAd);
                    }
                });
            }

            @Override
            public void onNativeAdFailedToLoad(Exception e) {
                customEventNativeListener.onNativeAdFailed(NativeErrorCode.NETWORK_NO_FILL);

                NativeErrorCode errorCode = NativeErrorCode.UNSPECIFIED;
                if (e instanceof SASNoAdToDeliverException) {
                    // No ad to deliver
                    errorCode = NativeErrorCode.NETWORK_NO_FILL;
                } else if (e instanceof SASAdTimeoutException) {
                    // Ad request timeout translates to admob network error
                    errorCode = NativeErrorCode.NETWORK_TIMEOUT;
                }

                // Must be executed in Main thread
                final NativeErrorCode finalCode = errorCode;
                SASUtil.getMainLooperHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        customEventNativeListener.onNativeAdFailed(finalCode);
                    }
                });
            }
        });

        nativeAdManager.loadNativeAd();
    }
}
