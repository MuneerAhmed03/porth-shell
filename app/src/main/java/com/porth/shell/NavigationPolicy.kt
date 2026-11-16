package com.porth.shell

import java.net.URI

internal class NavigationPolicy(devUrl: String?) {
    private val devUri: URI? = devUrl?.let { runCatching { URI(it) }.getOrNull() }

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
