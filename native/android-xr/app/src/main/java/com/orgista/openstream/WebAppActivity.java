package com.orgista.openstream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * The OpenStream product UI: the bundled web application rendered in a
 * WebView. Kids Mode, onboarding, the catalog, and playback all live in the
 * web app; its storage is app data, so uninstalling OpenStream is the
 * documented irreversible-Kids-Mode reset.
 *
 * Two modes:
 * - Standalone (default): serves the build bundled under assets/ from the
 *   app-assets origin. Gateway-backed features (NAS browsing, add-on streams,
 *   local transcode) degrade gracefully until a gateway is reachable.
 * - Tethered development: launched with a loopback `launchUrl` extra (the
 *   one-time secure launch URL, reachable through `adb reverse tcp:5173
 *   tcp:5173`), it loads the dev server directly for the full experience
 *   including NAS media and gateway transcoding.
 */
public final class WebAppActivity extends Activity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 8, 11));
        setContentView(webView);
        hideSystemUi();

        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (debuggable) WebView.setWebContentsDebuggingEnabled(true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("appassets.androidplatform.net")
                .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        String launchUrl = getIntent().getStringExtra("launchUrl");
        if (debuggable && isLoopbackHttp(launchUrl)) {
            webView.loadUrl(launchUrl);
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/index.html");
        }
    }

    private static boolean isLoopbackHttp(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && ("127.0.0.1".equals(host) || "localhost".equals(host));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void hideSystemUi() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }
}
