package com.orgista.openstream.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class TrailerJavascriptBridge(
    private val onPlaying: () -> Unit,
    private val onUnavailable: () -> Unit,
) {
    @JavascriptInterface
    fun playing() = onPlaying()

    @JavascriptInterface
    fun unavailable() = onUnavailable()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeTrailerPreview(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    onPlaying: () -> Unit = {},
    onUnavailable: () -> Unit = {},
) {
    YoutubeEmbedPlayer(
        youtubeKey = youtubeKey,
        modifier = modifier,
        muted = true,
        fullscreen = false,
        onPlaying = onPlaying,
        onUnavailable = onUnavailable,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeEmbedPlayer(
    youtubeKey: String,
    modifier: Modifier = Modifier,
    muted: Boolean,
    fullscreen: Boolean,
    onPlaying: () -> Unit = {},
    onUnavailable: () -> Unit = {},
) {
    if (!TrailerPreviewPolicy.validYoutubeKey(youtubeKey)) {
        LaunchedEffect(youtubeKey) { onUnavailable() }
        return
    }
    key(youtubeKey) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(
                        TrailerJavascriptBridge(
                            onPlaying = { post(onPlaying) },
                            onUnavailable = { post(onUnavailable) },
                        ),
                        "OpenStreamTrailer",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (request.isForMainFrame) return true
                            val host = request.url.host.orEmpty().lowercase()
                            return host != "youtube.com" && !host.endsWith(".youtube.com")
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) onUnavailable()
                        }
                    }
                    val appOrigin = "https://${context.packageName.lowercase()}/"
                    val html = youtubeEmbedHtml(
                        youtubeKey = youtubeKey,
                        muted = muted,
                        fullscreen = fullscreen,
                        origin = appOrigin.removeSuffix("/"),
                    )
                    loadDataWithBaseURL(appOrigin, html, "text/html", "UTF-8", null)
                }
            },
            update = {},
            onRelease = { webView ->
                webView.stopLoading()
                webView.removeJavascriptInterface("OpenStreamTrailer")
                webView.loadUrl("about:blank")
                webView.destroy()
            },
        )
    }
}

fun youtubeEmbedHtml(
    youtubeKey: String,
    muted: Boolean = true,
    fullscreen: Boolean = false,
    origin: String = "https://com.orgista.openstream",
): String {
    require(TrailerPreviewPolicy.validYoutubeKey(youtubeKey)) { "Invalid YouTube video ID" }
    val readyAction = if (muted) {
        "event.target.mute(); event.target.playVideo();"
    } else {
        "event.target.unMute(); event.target.setVolume(100); event.target.playVideo();"
    }
    return """
    <!doctype html>
    <html>
      <head>
        <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <style>
          html, body, #player { width:100%; height:100%; margin:0; background:#000; overflow:hidden; }
        </style>
      </head>
      <body>
        <div id="player"></div>
        <script src="https://www.youtube.com/iframe_api"></script>
        <script>
          var player;
          function onYouTubeIframeAPIReady() {
            player = new YT.Player('player', {
              videoId: '${youtubeKey}',
              width: '100%',
              height: '100%',
              playerVars: {
                autoplay: 1,
                playsinline: 1,
                controls: 1,
                fs: ${if (fullscreen) 1 else 0},
                rel: 0,
                origin: '$origin'
              },
              events: {
                onReady: function(event) { $readyAction },
                onStateChange: function(event) {
                  if (event.data === YT.PlayerState.PLAYING) OpenStreamTrailer.playing();
                  if (event.data === YT.PlayerState.ENDED) OpenStreamTrailer.unavailable();
                },
                onError: function() { OpenStreamTrailer.unavailable(); }
              }
            });
          }
        </script>
      </body>
    </html>
    """.trimIndent()
}
