package com.thedesitadka.app.ui.challenge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.thedesitadka.app.ui.theme.TheDesiTadkaTheme
import com.thedesitadka.core.network.CloudflareHtmlCache
import com.thedesitadka.core.network.NetworkClient
import com.thedesitadka.core.security.StreamHubLogger

class CloudflareChallengeActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_target_url"
        const val EXTRA_PROVIDER_NAME = "extra_provider_name"

        fun createIntent(context: Context, url: String, providerName: String = "Site"): Intent {
            return Intent(context, CloudflareChallengeActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_PROVIDER_NAME, providerName)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetUrl = intent.getStringExtra(EXTRA_URL) ?: "https://fry99.cc/"
        val providerName = intent.getStringExtra(EXTRA_PROVIDER_NAME) ?: "Site"

        setContent {
            TheDesiTadkaTheme {
                ChallengeScreen(
                    targetUrl = targetUrl,
                    providerName = providerName,
                    onSuccess = {
                        CookieManager.getInstance().flush()
                        Toast.makeText(this@CloudflareChallengeActivity, "Security clearance verified! Reloading...", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    },
                    onClose = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChallengeScreen(
    targetUrl: String,
    providerName: String,
    onSuccess: () -> Unit,
    onClose: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf("Verifying...") }
    var isVerifying by remember { mutableStateOf(false) }
    var isSuccessHandled by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun checkClearance(url: String, title: String?): Boolean {
        return try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.flush()
            val cookies = cookieManager.getCookie(url) ?: ""
            val hasClearance = cookies.contains("cf_clearance")

            val isChallengeTitle = title?.contains("Just a moment", ignoreCase = true) == true ||
                    title?.contains("Attention Required", ignoreCase = true) == true ||
                    title?.contains("Cloudflare", ignoreCase = true) == true ||
                    title?.contains("Security Challenge", ignoreCase = true) == true ||
                    title?.contains("Verifying", ignoreCase = true) == true

            if (hasClearance && !isChallengeTitle && !url.contains("/cdn-cgi/challenge-platform")) {
                StreamHubLogger.i("CloudflareChallenge", "cf_clearance cookie verified for $url! Title: $title")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun handleSuccess() {
        if (isSuccessHandled) return
        isSuccessHandled = true

        val currentView = webViewRef
        val finalUrl = currentView?.url ?: targetUrl
        CookieManager.getInstance().flush()

        if (currentView != null) {
            currentView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { htmlJson ->
                if (!htmlJson.isNullOrBlank() && htmlJson != "null") {
                    val unquoted = try {
                        org.json.JSONTokener(htmlJson).nextValue().toString()
                    } catch (e: Exception) {
                        htmlJson.trim('\"').replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "").replace("\\t", "\t")
                    }
                    if (unquoted.isNotBlank() && unquoted.contains("<")) {
                        StreamHubLogger.i("CloudflareChallenge", "Captured decrypted HTML from WebView for $finalUrl (${unquoted.length} bytes)")
                        CloudflareHtmlCache.put(finalUrl, unquoted)
                        CloudflareHtmlCache.put(targetUrl, unquoted)
                    }
                }
                onSuccess()
            }
        } else {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp),
                title = {
                    Column {
                        Text(
                            text = "Security Verification: $providerName",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = pageTitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Complete Human Verification",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap the Cloudflare Turnstile checkbox above if shown.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val currentUrl = webViewRef?.url ?: targetUrl
                            val title = webViewRef?.title
                            if (checkClearance(currentUrl, title)) {
                                handleSuccess()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Verification incomplete. Please solve the security checkbox or tap Reload.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (pageProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            userAgentString = NetworkClient.DEFAULT_USER_AGENT
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                pageProgress = newProgress / 100f
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isVerifying = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isVerifying = false
                                if (url != null) {
                                    cookieManager.flush()
                                    if (checkClearance(url, view?.title)) {
                                        handleSuccess()
                                    }
                                }
                            }
                        }

                        webViewRef = this
                        loadUrl(targetUrl)
                    }
                }
            )
        }
    }
}
