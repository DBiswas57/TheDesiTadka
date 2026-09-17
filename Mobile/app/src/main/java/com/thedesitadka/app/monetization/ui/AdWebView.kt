package com.thedesitadka.app.monetization.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdWebView(
    htmlContent: String,
    onAdRendered: () -> Unit,
    onAdClicked: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // 1. Strict Security Hardening
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString = NetworkClient.DEFAULT_USER_AGENT

                    // Explicitly disable all local file and content access
                    allowFileAccess = false
                    allowContentAccess = false
                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }

                setBackgroundColor(0x00000000) // Transparent background

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        StreamHubLogger.d("AdWebView", "Ad webview finished loading: $url")
                        onAdRendered()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        val errMsg = error?.description?.toString() ?: "Unknown error"
                        StreamHubLogger.w("AdWebView", "Ad webview error: $errMsg")
                        onError(errMsg)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // Ad link clicked: open safely in external system browser
                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                            onAdClicked()
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                                true
                            } catch (e: Exception) {
                                StreamHubLogger.w("AdWebView", "Failed to launch ad intent: ${e.message}")
                                false
                            }
                        } else {
                            // Block arbitrary custom schemes (e.g. intent:, market:, file:)
                            false
                        }
                    }
                }

                loadDataWithBaseURL("https://a.exoclick.com/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Keep content updated if needed
        }
    )
}
