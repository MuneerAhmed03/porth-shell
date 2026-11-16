package com.porth.shell

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private var devUri: Uri? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        finishFileChooser(
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
                ViewGroup.LayoutParams.MATCH_PARENT
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
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.splash_background)
        }

        val storageManager = StorageManager(applicationContext)
        val databaseManager = DatabaseManager(applicationContext)

        webView.addJavascriptInterface(
            AndroidBridge(storageManager, databaseManager),
            "AndroidBridge"
        )

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (request.url.host != ASSET_HOST) {
                    // Dev server or external origin: let the network stack handle it.
                    return null
                }
                assetLoader.shouldInterceptRequest(request.url)?.let { return it }

                // SPA fallback: serve index.html for any main-frame navigation
                // that isn't an existing asset (e.g. /settings or /users/jane.doe
                // on reload or deep link). Subresources are never main-frame, so
                // they still 404 normally.
                if (request.isForMainFrame) {
                    return assetLoader.shouldInterceptRequest(Uri.parse(ASSET_LOADER_INDEX_URL))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (isAllowedNavigation(url)) {
                    return false
                }
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (_: Exception) {
                    // No handler on device; ignore.
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                splashView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                splashView.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    showInlineError(error.description?.toString() ?: "Unknown loading error")
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                showInlineError(description ?: "Unknown loading error")
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (BuildConfig.DEBUG || BuildConfig.PORTH_WEBVIEW_DEBUG) {
                    Log.d("PorthWeb", consoleMessage?.message() ?: "")
                }
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                finishFileChooser(null) // cancel any pending picker
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                if (intent == null) {
                    finishFileChooser(null)
                    return false
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    finishFileChooser(null)
                    false
                }
            }
        }

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
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

        val startUrl = resolveStartUrl()
        webView.loadUrl(startUrl)
    }

    override fun onDestroy() {
        finishFileChooser(null)
        webView.destroy()
        super.onDestroy()
    }

    private fun finishFileChooser(uris: Array<Uri>?) {
        val callback = filePathCallback
        filePathCallback = null
        callback?.onReceiveValue(uris)
    }

    private fun isAllowedNavigation(url: Uri): Boolean {
        if (url.scheme == "https" && url.host == ASSET_HOST) {
            return true
        }
        val dev = devUri ?: return false
        return url.scheme == dev.scheme &&
            url.host == dev.host &&
            effectivePort(url) == effectivePort(dev)
    }

    private fun effectivePort(url: Uri): Int {
        val port = url.port
        if (port != -1) return port
        return when (url.scheme) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
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
                devUri = Uri.parse(configuredDevUrl)
                configuredDevUrl
            } else {
                ASSET_LOADER_INDEX_URL
            }
        } catch (_: Exception) {
            ASSET_LOADER_INDEX_URL
        }
    }

    private companion object {
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val ASSET_LOADER_INDEX_URL = "https://appassets.androidplatform.net/index.html"
    }
}
