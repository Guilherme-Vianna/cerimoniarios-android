package com.cerimoniarios.app

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.cerimoniarios.app.ads.BannerController
import com.cerimoniarios.app.auth.NativeBridge
import com.cerimoniarios.app.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val appUri: Uri by lazy { BuildConfig.APP_URL.toUri() }
    private var mainFrameFailed = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = binding.webView.goBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpSystemBars(binding.root, binding.appBar)

        binding.toolbar.setOnMenuItemClickListener(::onMenuItemSelected)
        binding.retryButton.setOnClickListener { binding.webView.reload() }
        onBackPressedDispatcher.addCallback(this, backCallback)

        setUpWebView()
        NativeBridge(this).attach(binding.webView, origin(appUri))
        BannerController(this, binding.adContainer).bind(shouldShowAds())

        if (savedInstanceState == null || binding.webView.restoreState(savedInstanceState) == null) {
            binding.webView.loadUrl(BuildConfig.APP_URL)
        }
    }

    override fun onResume() {
        super.onResume()
        app.support.refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun shouldShowAds() =
        combine(app.preferences.adsEnabled, app.support.state.map { it.isSupporter }) { enabled, supporter ->
            enabled && !supporter
        }.distinctUntilChanged()

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        val target = when (item.itemId) {
            R.id.action_support -> SupportActivity::class.java
            R.id.action_about -> AboutActivity::class.java
            else -> return false
        }
        startActivity(Intent(this, target))
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        with(binding.webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(false)
        }
        binding.webView.webViewClient = AppWebViewClient()
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.setProgressCompat(newProgress, true)
                binding.progress.isVisible = newProgress < 100
            }
        }
    }

    private fun isAppUrl(uri: Uri) = uri.scheme == appUri.scheme && uri.authority == appUri.authority

    private fun openExternally(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (isAppUrl(request.url)) return false
            if (request.url.scheme in setOf("http", "https", "mailto", "tel")) openExternally(request.url)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            mainFrameFailed = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            binding.errorView.isVisible = mainFrameFailed
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            backCallback.isEnabled = view.canGoBack()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            mainFrameFailed = true
            binding.errorView.isVisible = true
        }
    }

    private companion object {
        fun origin(uri: Uri) = "${uri.scheme}://${uri.authority}"
    }
}
