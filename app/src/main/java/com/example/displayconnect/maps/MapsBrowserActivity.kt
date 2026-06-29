package com.example.displayconnect.maps

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.displayconnect.R
import com.example.displayconnect.navigation.NavigationForegroundService
import com.example.displayconnect.ui.theme.DisplayConnectTheme

/**
 * Shows Google Maps in a WebView and periodically extracts navigation HTML for the CYD.
 * Keep this screen open while riding — extraction runs while the activity is visible.
 */
class MapsBrowserActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val htmlHandler = Handler(Looper.getMainLooper())
    private val htmlRunnable = object : Runnable {
        override fun run() {
            extractHtmlFromWebView()
            htmlHandler.postDelayed(this, HTML_POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
        val destLon = intent.getDoubleExtra(EXTRA_DEST_LON, Double.NaN)
        if (destLat.isNaN() || destLon.isNaN()) {
            finish()
            return
        }

        val mapsUrl = MapsHtmlExtractor.buildMapsDirectionsUrl(destLat, destLon)

        setContent {
            DisplayConnectTheme {
                MapsBrowserScreen(
                    mapsUrl = mapsUrl,
                    onWebViewCreated = { webView = it },
                    onStop = { stopAndFinish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        htmlHandler.postDelayed(htmlRunnable, HTML_POLL_MS)
    }

    override fun onPause() {
        htmlHandler.removeCallbacks(htmlRunnable)
        super.onPause()
    }

    override fun onDestroy() {
        htmlHandler.removeCallbacks(htmlRunnable)
        MapsHtmlHolder.clear()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun extractHtmlFromWebView() {
        val view = webView ?: return
        view.evaluateJavascript(MapsHtmlExtractor.EXTRACT_SCRIPT) { result ->
            val html = result
                ?.trim('"')
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
            if (!html.isNullOrBlank() && html != "null" && html != "") {
                MapsHtmlHolder.update(html)
            }
        }
    }

    private fun stopAndFinish() {
        NavigationForegroundService.stop(this)
        MapsHtmlHolder.clear()
        finish()
    }

    companion object {
        const val EXTRA_DEST_LAT = "extra_dest_lat"
        const val EXTRA_DEST_LON = "extra_dest_lon"
        private const val HTML_POLL_MS = 2000L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapsBrowserScreen(
    mapsUrl: String,
    onWebViewCreated: (WebView) -> Unit,
    onStop: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.maps_browser_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = stringResource(R.string.maps_browser_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl(mapsUrl)
                        onWebViewCreated(this)
                    }
                }
            )
            OutlinedButton(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.stop_navigation))
            }
        }
    }
}
