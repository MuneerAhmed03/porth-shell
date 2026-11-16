package com.porth.shell

import java.net.URI

internal class NavigationPolicy(devUrl: String?) {
    private val devUri: URI? = devUrl?.let { runCatching { URI(it) }.getOrNull() }

    fun isAllowedNavigation(uri: URI): Boolean =
        uri.isAssetOrigin() || devUri?.let(uri::hasSameOrigin) == true

    fun shouldServeSpaFallback(uri: URI, isForMainFrame: Boolean): Boolean =
        isForMainFrame && uri.isAssetOrigin()
}

private fun URI.isAssetOrigin(): Boolean =
    scheme.equals("https", ignoreCase = true) && host.equals(ShellUrls.ASSET_HOST, ignoreCase = true)

private fun URI.hasSameOrigin(other: URI): Boolean =
    scheme.equals(other.scheme, ignoreCase = true) &&
        host.equals(other.host, ignoreCase = true) &&
        effectivePort(this) == effectivePort(other)

private fun effectivePort(uri: URI): Int =
    uri.port.takeUnless { it == -1 } ?: when (uri.scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
