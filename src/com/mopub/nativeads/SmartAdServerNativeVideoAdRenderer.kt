package com.mopub.nativeads

import android.content.Context
import com.mopub.nativeads.SmartAdServerNative.SASVideoNativeAd
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import com.smartadserver.android.library.ui.SASNativeAdMediaView
import android.widget.TextView

/**
 * MoPub renderer class for SASVideoNativeAd video native ads
 */
class SmartAdServerNativeVideoAdRenderer(private val mViewBinder: ViewBinder) : MoPubAdRenderer<SASVideoNativeAd> {

    override fun createAdView(context: Context, parent: ViewGroup?): View {
        val adView = LayoutInflater.from(context).inflate(mViewBinder.layoutId, parent, false)
        val mainImageView = adView.findViewById<View>(mViewBinder.mainImageId)
            ?: return adView
        val mainImageViewLayoutParams = mainImageView.layoutParams
        val mediaViewLayoutParams = RelativeLayout.LayoutParams(
            mainImageViewLayoutParams.width,
            mainImageViewLayoutParams.height
        )

        if (mainImageViewLayoutParams is MarginLayoutParams) {
            mediaViewLayoutParams.setMargins(
                mainImageViewLayoutParams.leftMargin,
                mainImageViewLayoutParams.topMargin,
                mainImageViewLayoutParams.rightMargin,
                mainImageViewLayoutParams.bottomMargin
            )
        }

        if (mainImageViewLayoutParams is RelativeLayout.LayoutParams) {
            val rules = mainImageViewLayoutParams.rules
            for (i in rules.indices) {
                mediaViewLayoutParams.addRule(i, rules[i])
            }
            mainImageView.visibility = View.INVISIBLE
        } else {
            mainImageView.visibility = View.GONE
        }

        // Create SASNativeAdMediaView that will render native video
        val mediaView = SASNativeAdMediaView(context)
        val mainImageParent = mainImageView.parent as ViewGroup
        val mainImageIndex = mainImageParent.indexOfChild(mainImageView)

        // Encapsulate SASNativeAdMediaView into a relative layout for centering purposes (as its enforced ratio may differ from the main image)
        val mediaViewCenteringContainer = RelativeLayout(context)
        val layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
        mediaViewCenteringContainer.tag = mediaView
        mediaViewCenteringContainer.addView(mediaView, layoutParams)
        mainImageParent.addView(
            mediaViewCenteringContainer,
            mainImageIndex + 1,
            mediaViewLayoutParams
        )
        return adView
    }

    override fun renderAdView(view: View, sasVideoNativeAd: SASVideoNativeAd) {
        var sasNativeViewHolder = view.tag as SASNativeViewHolder?

        if (sasNativeViewHolder == null) {
            sasNativeViewHolder = SASNativeViewHolder.fromViewBinder(view, mViewBinder)
            view.tag = sasNativeViewHolder
        }

        val mainImageView = sasNativeViewHolder.mainImageView
        NativeRendererHelper.addTextView(sasNativeViewHolder.titleView, sasVideoNativeAd.title)
        NativeRendererHelper.addTextView(sasNativeViewHolder.textView, sasVideoNativeAd.text)
        NativeRendererHelper.addTextView(
            sasNativeViewHolder.callToActionView,
            sasVideoNativeAd.callToAction
        )
        NativeImageHelper.loadImageView(sasVideoNativeAd.mainImageUrl, mainImageView)
        NativeImageHelper.loadImageView(
            sasVideoNativeAd.iconImageUrl,
            sasNativeViewHolder.iconImageView
        )
        NativeRendererHelper.addPrivacyInformationIcon(
            sasNativeViewHolder.privacyInformationIconImageView,
            sasVideoNativeAd.privacyInformationIconImageUrl,
            sasVideoNativeAd.privacyInformationIconClickThroughUrl
        )
        val mediaView = sasNativeViewHolder.mediaView
        if (mediaView != null && mainImageView != null) {
            mediaView.nativeAdElement = sasVideoNativeAd.sASNativeAd
            mediaView.visibility = View.VISIBLE
            if (sasNativeViewHolder.isMainImageViewInRelativeView) {
                mainImageView.visibility = View.INVISIBLE
            } else {
                mainImageView.visibility = View.GONE
            }
        }
    }

    override fun supports(nativeAd: BaseNativeAd): Boolean {
        return nativeAd is SASVideoNativeAd
    }

    /**
     * View holder class for views rendering SASVideoNativeAd instances
     */
    internal class SASNativeViewHolder private constructor(
        private val mStaticNativeViewHolder: StaticNativeViewHolder,
        val mediaView: SASNativeAdMediaView?,
        val isMainImageViewInRelativeView: Boolean
    ) {
        val mainView: View?
            get() = mStaticNativeViewHolder.mainView
        val titleView: TextView?
            get() = mStaticNativeViewHolder.titleView
        val textView: TextView?
            get() = mStaticNativeViewHolder.textView
        val callToActionView: TextView?
            get() = mStaticNativeViewHolder.callToActionView
        val mainImageView: ImageView?
            get() = mStaticNativeViewHolder.mainImageView
        val iconImageView: ImageView?
            get() = mStaticNativeViewHolder.iconImageView
        val privacyInformationIconImageView: ImageView?
            get() = mStaticNativeViewHolder.privacyInformationIconImageView

        companion object {
            fun fromViewBinder(view: View?, viewBinder: ViewBinder?): SASNativeViewHolder {
                val staticNativeViewHolder = StaticNativeViewHolder.fromViewBinder(
                    view!!, viewBinder!!
                )
                val mainImageView: View? = staticNativeViewHolder.mainImageView
                var mainImageViewInRelativeView = false
                var mediaView: SASNativeAdMediaView? = null
                if (mainImageView != null) {
                    val mainImageParent = mainImageView.parent as ViewGroup
                    if (mainImageParent is RelativeLayout) {
                        mainImageViewInRelativeView = true
                    }
                    val mainImageIndex = mainImageParent.indexOfChild(mainImageView)
                    val viewAfterImageView = mainImageParent.getChildAt(mainImageIndex + 1)
                    if (viewAfterImageView.tag is SASNativeAdMediaView) {
                        mediaView = viewAfterImageView.tag as SASNativeAdMediaView
                    }
                }
                return SASNativeViewHolder(
                    staticNativeViewHolder,
                    mediaView,
                    mainImageViewInRelativeView
                )
            }
        }
    }
}