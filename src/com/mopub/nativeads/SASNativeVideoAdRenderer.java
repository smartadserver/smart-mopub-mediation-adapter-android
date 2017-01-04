package com.mopub.nativeads;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.smartadserver.android.library.mediation.SASMoPubCustomEventNative;
import com.smartadserver.android.library.ui.SASNativeAdMediaView;


/**
 * MoPub renderer class for {@link com.smartadserver.android.library.mediation.SASMoPubCustomEventNative.SASVideoNativeAd} video native ads
 */
public class SASNativeVideoAdRenderer implements MoPubAdRenderer<SASMoPubCustomEventNative.SASVideoNativeAd> {

    private final ViewBinder mViewBinder;

    /**
     * Constructs a renderer for smart ad server native ad with video.
     *
     * @param viewBinder The view binder to use when inflating and rendering an ad.
     */
    public SASNativeVideoAdRenderer(final ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    @NonNull
    @Override
    public View createAdView(@NonNull Context context, @Nullable ViewGroup parent) {
        final View adView = LayoutInflater
                .from(context)
                .inflate(mViewBinder.layoutId, parent, false);
        final View mainImageView = adView.findViewById(mViewBinder.mainImageId);
        if (mainImageView == null) {
            return adView;
        }

        final ViewGroup.LayoutParams mainImageViewLayoutParams = mainImageView.getLayoutParams();
        final RelativeLayout.LayoutParams mediaViewLayoutParams = new RelativeLayout.LayoutParams(
                mainImageViewLayoutParams.width, mainImageViewLayoutParams.height);

        if (mainImageViewLayoutParams instanceof ViewGroup.MarginLayoutParams) {
            final ViewGroup.MarginLayoutParams marginParams =
                    (ViewGroup.MarginLayoutParams) mainImageViewLayoutParams;
            mediaViewLayoutParams.setMargins(marginParams.leftMargin,
                    marginParams.topMargin,
                    marginParams.rightMargin,
                    marginParams.bottomMargin);
        }

        if (mainImageViewLayoutParams instanceof RelativeLayout.LayoutParams) {
            final RelativeLayout.LayoutParams mainImageViewRelativeLayoutParams =
                    (RelativeLayout.LayoutParams) mainImageViewLayoutParams;
            final int[] rules = mainImageViewRelativeLayoutParams.getRules();
            for (int i = 0; i < rules.length; i++) {
                mediaViewLayoutParams.addRule(i, rules[i]);
            }
            mainImageView.setVisibility(View.INVISIBLE);
        } else {
            mainImageView.setVisibility(View.GONE);
        }

        // create SASNativeAdMediaView that will render native video
        final SASNativeAdMediaView mediaView = new SASNativeAdMediaView(context);
        ViewGroup mainImageParent = (ViewGroup) mainImageView.getParent();
        int mainImageIndex = mainImageParent.indexOfChild(mainImageView);

        // encapsulate SASNativeAdMediaView into a relative layout for centering purposes (as its enforced ratio may differ from the main image)
        RelativeLayout mediaViewCenteringContainer = new RelativeLayout(context);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        mediaViewCenteringContainer.setTag(mediaView);
        mediaViewCenteringContainer.addView(mediaView, layoutParams);

        mainImageParent.addView(mediaViewCenteringContainer, mainImageIndex + 1, mediaViewLayoutParams);
        return adView;
    }

    @Override
    public void renderAdView(@NonNull View view, @NonNull SASMoPubCustomEventNative.SASVideoNativeAd sasVideoNativeAd) {
        SASNativeViewHolder sasNativeViewHolder = (SASNativeViewHolder)view.getTag();
        if (sasNativeViewHolder == null) {
            sasNativeViewHolder = SASNativeViewHolder.fromViewBinder(view, mViewBinder);
            view.setTag(sasNativeViewHolder);
        }

        final ImageView mainImageView = sasNativeViewHolder.getMainImageView();
        NativeRendererHelper.addTextView(sasNativeViewHolder.getTitleView(),
                sasVideoNativeAd.getTitle());
        NativeRendererHelper.addTextView(sasNativeViewHolder.getTextView(), sasVideoNativeAd.getText());
        NativeRendererHelper.addTextView(sasNativeViewHolder.getCallToActionView(),
                sasVideoNativeAd.getCallToAction());
        NativeImageHelper.loadImageView(sasVideoNativeAd.getMainImageUrl(), mainImageView);
        NativeImageHelper.loadImageView(sasVideoNativeAd.getIconImageUrl(),
                sasNativeViewHolder.getIconImageView());
        NativeRendererHelper.addPrivacyInformationIcon(
                sasNativeViewHolder.getPrivacyInformationIconImageView(),
                sasVideoNativeAd.getPrivacyInformationIconImageUrl(),
                sasVideoNativeAd.getPrivacyInformationIconClickThroughUrl());
        final SASNativeAdMediaView mediaView = sasNativeViewHolder.getMediaView();
        if (mediaView != null && mainImageView != null) {
            mediaView.setNativeAdElement(sasVideoNativeAd.getSASNativeAd());
            mediaView.setVisibility(View.VISIBLE);
            if (sasNativeViewHolder.isMainImageViewInRelativeView()) {
                mainImageView.setVisibility(View.INVISIBLE);
            } else {
                mainImageView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean supports(@NonNull BaseNativeAd nativeAd) {
        return nativeAd instanceof SASMoPubCustomEventNative.SASVideoNativeAd;
    }

    /**
     * View holder class for views rendering {@link com.smartadserver.android.library.mediation.SASMoPubCustomEventNative.SASVideoNativeAd} instances
     */
    static class SASNativeViewHolder {
        private final StaticNativeViewHolder mStaticNativeViewHolder;
        private final SASNativeAdMediaView mMediaView;
        private final boolean isMainImageViewInRelativeView;

        private SASNativeViewHolder(final StaticNativeViewHolder staticNativeViewHolder,
                                         final SASNativeAdMediaView mediaView, final boolean mainImageViewInRelativeView) {
            mStaticNativeViewHolder = staticNativeViewHolder;
            mMediaView = mediaView;
            isMainImageViewInRelativeView = mainImageViewInRelativeView;
        }

        static SASNativeViewHolder fromViewBinder(final View view,
                                                       final ViewBinder viewBinder) {
            StaticNativeViewHolder staticNativeViewHolder = StaticNativeViewHolder.fromViewBinder(view, viewBinder);
            final View mainImageView = staticNativeViewHolder.mainImageView;
            boolean mainImageViewInRelativeView = false;
            SASNativeAdMediaView mediaView = null;
            if (mainImageView != null) {
                final ViewGroup mainImageParent = (ViewGroup) mainImageView.getParent();
                if (mainImageParent instanceof RelativeLayout) {
                    mainImageViewInRelativeView = true;
                }
                final int mainImageIndex = mainImageParent.indexOfChild(mainImageView);
                final View viewAfterImageView = mainImageParent.getChildAt(mainImageIndex + 1);
                if (viewAfterImageView.getTag() instanceof SASNativeAdMediaView) {
                    mediaView = (SASNativeAdMediaView) viewAfterImageView.getTag();
                }
            }
            return new SASNativeViewHolder(staticNativeViewHolder, mediaView, mainImageViewInRelativeView);
        }

        public View getMainView() {
            return mStaticNativeViewHolder.mainView;
        }

        public TextView getTitleView() {
            return mStaticNativeViewHolder.titleView;
        }

        public TextView getTextView() {
            return mStaticNativeViewHolder.textView;
        }

        public TextView getCallToActionView() {
            return mStaticNativeViewHolder.callToActionView;
        }

        public ImageView getMainImageView() {
            return mStaticNativeViewHolder.mainImageView;
        }

        public ImageView getIconImageView() {
            return mStaticNativeViewHolder.iconImageView;
        }

        public ImageView getPrivacyInformationIconImageView() {
            return mStaticNativeViewHolder.privacyInformationIconImageView;
        }

        public SASNativeAdMediaView getMediaView() {
            return mMediaView;
        }

        public boolean isMainImageViewInRelativeView() {
            return isMainImageViewInRelativeView;
        }
    }
}
