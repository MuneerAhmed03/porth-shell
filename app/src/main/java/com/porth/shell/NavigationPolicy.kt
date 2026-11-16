package com.porth.shell

import java.net.URI

/**
 * Pure navigation rules for the shell: which origins may load inside the
 * WebView (and therefore hold the AndroidBridge), and when an SPA fallback
 * should serve index.html.
 *
 * Uses java.net.URI so the rules are unit-testable on the JVM without
 * Android dependencies.
 */
internal class NavigationPolicy(devUrl: String?) {
    private val devUri: URI? = devUrl?.let { runCatching { URI(it) }.getOrNull() }

    /**
     * True when [uri] may load inside the WebView: the asset origin over
     * HTTPS, or the exact configured dev origin (scheme + host + effective
     * port). Everything else is routed to the system browser.
     */
    fun isAllowedNavigation(uri: URI): Boolean {
        if (uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(ASSET_HOST, ignoreCase = true)
        ) {
            return true
        }
        val dev = devUri ?: return false
        return uri.scheme.equals(dev.scheme, ignoreCase = true) &&
            uri.host.equals(dev.host, ignoreCase = true) &&
            effectivePort(uri) == effectivePort(dev)
    }

    /**
     * True when a request should be answered with index.html: any main-frame
     * navigation on the HTTPS asset origin. Subresources and dev-origin
     * requests are excluded (the dev server does its own SPA fallback).
     */
    fun shouldServeSpaFallback(uri: URI, isForMainFrame: Boolean): Boolean {
        if (!isForMainFrame) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(ASSET_HOST, ignoreCase = true)
    }

    private fun effectivePort(uri: URI): Int {
        val port = uri.port
        if (port != -1) {
            return port
        }
        return when (uri.scheme?.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
    }

    companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
    }
}
