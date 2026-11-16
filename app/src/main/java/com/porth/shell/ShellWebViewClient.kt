package com.porth.shell

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import java.net.URI

internal class ShellWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val policy: NavigationPolicy,
    private val openExternal: (Uri) -> Unit,
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
    private val onMainFrameError: (String) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val uri = request.url.toJavaUri() ?: return null
        assetLoader.shouldInterceptRequest(request.url)?.let { return it }
        if (policy.shouldServeSpaFallback(uri, request.isForMainFrame)) {
            return assetLoader.shouldInterceptRequest(INDEX_URI)
        }
        return null
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val uri = request.url.toJavaUri()
        if (uri != null && policy.isAllowedNavigation(uri)) {
            return false
        }
        openExternal(request.url)
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = onPageStarted()

    override fun onPageFinished(view: WebView?, url: String?) = onPageFinished()

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onMainFrameError(error.description?.toString() ?: "Unknown loading error")
        }
    }

    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        onMainFrameError(description ?: "Unknown loading error")
    }

    private fun Uri.toJavaUri(): URI? = runCatching { URI(toString()) }.getOrNull()

    private companion object {
        val INDEX_URI: Uri = Uri.parse(ShellUrls.INDEX_URL)
    }
}
