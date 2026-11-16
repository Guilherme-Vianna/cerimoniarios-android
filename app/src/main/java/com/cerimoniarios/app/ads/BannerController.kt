package com.cerimoniarios.app.ads

import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cerimoniarios.app.BuildConfig
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * One adaptive banner at the top of the screen, shown only while [bind]'s flow says so
 * (ads enabled in settings and the user is not a supporter). Asks for consent lazily, so
 * supporters and people who turned ads off never see the consent form.
 */
class BannerController(
    private val activity: AppCompatActivity,
    private val container: ViewGroup,
) : DefaultLifecycleObserver {

    private var adView: AdView? = null
    private var wanted = false
    private var consentRequested = false

    fun bind(shouldShow: Flow<Boolean>) {
        activity.lifecycle.addObserver(this)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                shouldShow.collect {
                    wanted = it
                    render()
                }
            }
        }
    }

    private fun render() {
        if (!wanted) {
            removeBanner()
            return
        }
        when {
            sdkReady -> showBanner()
            !consentRequested -> {
                consentRequested = true
                AdConsent.gather(activity) { initializeSdk() }
            }
        }
    }

    private fun initializeSdk() {
        if (activity.isDestroyed || !AdConsent.canRequestAds(activity)) return
        if (sdkReady || sdkStarting) {
            render()
            return
        }
        sdkStarting = true
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                .build(),
        )
        MobileAds.initialize(activity.applicationContext) {
            sdkReady = true
            if (!activity.isDestroyed) render()
        }
    }

    private fun showBanner() {
        if (adView != null || BuildConfig.ADMOB_BANNER_ID.isBlank()) return
        val metrics = activity.resources.displayMetrics
        val widthDp = (metrics.widthPixels / metrics.density).toInt()
        val banner = AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    container.isVisible = wanted
                }
            }
        }
        adView = banner
        container.addView(banner)
        banner.loadAd(AdRequest.Builder().build())
    }

    private fun removeBanner() {
        adView?.destroy()
        adView = null
        container.removeAllViews()
        container.isVisible = false
    }

    override fun onResume(owner: LifecycleOwner) {
        adView?.resume()
    }

    override fun onPause(owner: LifecycleOwner) {
        adView?.pause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        removeBanner()
    }

    private companion object {
        var sdkStarting = false
        var sdkReady = false
    }
}
