package com.porth.shell

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var splashView: View
    private lateinit var webChromeClient: ShellWebChromeClient
    private var devUrl: String? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        webChromeClient.finishFileChooser(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || BuildConfig.PORTH_WEBVIEW_DEBUG)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                @Suppress("DEPRECATION")
                databaseEnabled = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
            }
        }

        splashView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.splash_background)
        }

        val storageManager = StorageManager(applicationContext)
        val databaseManager = DatabaseManager(applicationContext)
        webView.addJavascriptInterface(
            AndroidBridge(storageManager, databaseManager),
            "AndroidBridge",
        )

        val startUrl = resolveStartUrl()
        val policy = NavigationPolicy(devUrl)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = ShellWebViewClient(
            assetLoader = assetLoader,
            policy = policy,
            openExternal = { url ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (_: Exception) {
                }
            },
            onPageStarted = { splashView.visibility = View.VISIBLE },
            onPageFinished = { splashView.visibility = View.GONE },
            onMainFrameError = { message -> showInlineError(message) },
        )

        webChromeClient = ShellWebChromeClient(
            activity = this,
            fileChooserLauncher = fileChooserLauncher,
            debugLogging = BuildConfig.DEBUG || BuildConfig.PORTH_WEBVIEW_DEBUG,
        )
        webView.webChromeClient = webChromeClient

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(webView)
            addView(splashView)
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        webView.loadUrl(startUrl)
    }

    override fun onDestroy() {
        if (::webChromeClient.isInitialized) {
            webChromeClient.finishFileChooser(null)
        }
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun showInlineError(message: String) {
        splashView.visibility = View.GONE
        val escaped = message
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

        val html = """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>Load Error</title></head>
            <body style="font-family: sans-serif; padding: 24px;">
              <h2>Failed to load app</h2>
              <p>$escaped</p>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun resolveStartUrl(): String {
        return try {
            val configJson = assets.open("porth-config.json")
                .bufferedReader()
                .use { it.readText() }
            val json = JSONObject(configJson)
            val configuredDevUrl = json.optString("devUrl", "")
            if (configuredDevUrl.isNotBlank()) {
                devUrl = configuredDevUrl
                configuredDevUrl
            } else {
                ASSET_LOADER_INDEX_URL
            }
        } catch (_: Exception) {
            ASSET_LOADER_INDEX_URL
        }
    }

    private companion object {
        const val ASSET_LOADER_INDEX_URL = "https://appassets.androidplatform.net/index.html"
    }
}
