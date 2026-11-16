package com.porth.shell

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity

internal class ShellWebChromeClient(
    private val activity: AppCompatActivity,
    private val fileChooserLauncher: ActivityResultLauncher<Intent>,
    private val debugLogging: Boolean,
) : WebChromeClient() {

    var filePathCallback: ValueCallback<Array<Uri>>? = null
        private set

    fun finishFileChooser(uris: Array<Uri>?) {
        val callback = filePathCallback
        filePathCallback = null
        callback?.onReceiveValue(uris)
    }

    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?,
    ): Boolean {
        AlertDialog.Builder(activity)
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
        result: JsResult?,
    ): Boolean {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
            .setOnCancelListener { result?.cancel() }
            .show()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (debugLogging) {
            Log.d("PorthWeb", consoleMessage?.message() ?: "")
        }
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        finishFileChooser(null)
        this.filePathCallback = filePathCallback
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
