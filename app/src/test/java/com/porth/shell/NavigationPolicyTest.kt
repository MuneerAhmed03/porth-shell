package com.porth.shell

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the shell's navigation rules: the origin matrix and
 * the SPA fallback (including dotted client-side routes).
 */
class NavigationPolicyTest {

    private val dev = NavigationPolicy("http://192.168.1.2:5173")

    // --- asset origin ---

    @Test
    fun assetOriginOverHttpsIsAllowed() {
        assertTrue(
            dev.isAllowedNavigation(URI("https://appassets.androidplatform.net/index.html")),
        )
    }

    @Test
    fun assetOriginOverHttpIsDenied() {
        assertFalse(
            dev.isAllowedNavigation(URI("http://appassets.androidplatform.net/index.html")),
        )
    }

    @Test
    fun unrelatedHostIsDenied() {
        assertFalse(dev.isAllowedNavigation(URI("https://example.com/")))
    }

    // --- dev origin matrix ---

    @Test
    fun devOriginExactMatchIsAllowed() {
        assertTrue(dev.isAllowedNavigation(URI("http://192.168.1.2:5173/settings")))
    }

    @Test
    fun devOriginWithDifferentPortIsDenied() {
        assertFalse(dev.isAllowedNavigation(URI("http://192.168.1.2:8080/")))
    }

    @Test
    fun devOriginWithDifferentSchemeIsDenied() {
        assertFalse(dev.isAllowedNavigation(URI("https://192.168.1.2:5173/")))
    }

    @Test
    fun devOriginWithDifferentHostIsDenied() {
        assertFalse(dev.isAllowedNavigation(URI("http://192.168.1.3:5173/")))
    }

    @Test
    fun noDevConfiguredOnlyAllowsAssetOrigin() {
        val policy = NavigationPolicy(null)
        assertFalse(policy.isAllowedNavigation(URI("http://192.168.1.2:5173/")))
        assertTrue(policy.isAllowedNavigation(URI("https://appassets.androidplatform.net/")))
    }

    // --- SPA fallback ---

    @Test
    fun mainFrameDottedRouteServesSpaFallback() {
        assertTrue(
            dev.shouldServeSpaFallback(
                URI("https://appassets.androidplatform.net/releases/v1.2"),
                isForMainFrame = true,
            ),
        )
        assertTrue(
            dev.shouldServeSpaFallback(
                URI("https://appassets.androidplatform.net/users/jane.doe"),
                isForMainFrame = true,
            ),
        )
    }

    @Test
    fun mainFrameExtensionlessRouteServesSpaFallback() {
        assertTrue(
            dev.shouldServeSpaFallback(
                URI("https://appassets.androidplatform.net/settings"),
                isForMainFrame = true,
            ),
        )
    }

    @Test
    fun subresourceNeverServesSpaFallback() {
        assertFalse(
            dev.shouldServeSpaFallback(
                URI("https://appassets.androidplatform.net/assets/app.js"),
                isForMainFrame = false,
            ),
        )
        assertFalse(
            dev.shouldServeSpaFallback(
                URI("https://appassets.androidplatform.net/logo.png"),
                isForMainFrame = false,
            ),
        )
    }

    @Test
    fun devOriginMainFrameDoesNotServeSpaFallback() {
        // The Vite dev server performs its own SPA fallback.
        assertFalse(
            dev.shouldServeSpaFallback(
                URI("http://192.168.1.2:5173/settings"),
                isForMainFrame = true,
            ),
        )
    }

    @Test
    fun nonHttpsAssetOriginDoesNotServeSpaFallback() {
        assertFalse(
            dev.shouldServeSpaFallback(
                URI("http://appassets.androidplatform.net/settings"),
                isForMainFrame = true,
            ),
        )
    }
}
