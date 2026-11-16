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

/**
 * Resource interception and navigation for the shell WebView. All policy
 * decisions delegate to [NavigationPolicy]; the Activity supplies only
 * callbacks for the pieces that touch views or the system.
 */
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

        // SPA fallback: serve index.html for any main-frame navigation that
        // isn't an existing asset (e.g. /settings or /users/jane.doe on
        // reload or deep link). Subresources are never main-frame, so they
        // still 404 normally.
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
        val INDEX_URI: Uri = Uri.parse("https://appassets.androidplatform.net/index.html")
    }
}
