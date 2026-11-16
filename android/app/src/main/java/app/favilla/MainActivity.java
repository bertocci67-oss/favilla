package app.favilla;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Favilla runs as a web app: the model is downloaded and executed inside the page by
 * WebAssembly, so the APK is a shell that keeps the site in a full-screen WebView.
 * Bundling the assets was not an option: the worker needs COOP/COEP headers for the
 * multi-thread path, and only the hosted origin sends them.
 */
public class MainActivity extends Activity {

    private static final String START_URL =
            "https://site-3c4b410fa82a4937a3a270ac92de0e41.freebuff.page/";
    private static final String RETRY_URL = "favilla://retry";
    private static final int BG = Color.rgb(20, 20, 19);
    private static final long PAGE_TIMEOUT_MS = 25000;

    private FrameLayout root;
    private WebView web;
    private ProgressBar bar;
    private boolean offline = false;
    private boolean pageReady = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pageTimeout = new Runnable() {
        @Override
        public void run() {
            if (!pageReady) showOffline();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6));

        setContentView(root);
        installWebView();
        loadStart();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void installWebView() {
        web = new WebView(this);
        web.setBackgroundColor(BG);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();

                if (RETRY_URL.equals(url.toString())) {
                    loadStart();
                    return true;
                }

                String host = url.getHost();
                // Everything the app itself needs (the page, the model files, the runtime)
                // stays inside; anything else goes to the system browser.
                if (host != null && (host.endsWith("freebuff.page")
                        || host.endsWith("huggingface.co")
                        || host.endsWith("hf.co")
                        || host.endsWith("jsdelivr.net"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageReady = false;
                bar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                offline = false;
                handler.removeCallbacks(pageTimeout);
                bar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) showOffline();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                           WebResourceResponse response) {
                if (request.isForMainFrame() && response != null
                        && response.getStatusCode() >= 500) {
                    showOffline();
                }
            }
        });

        CookieManager.getInstance().setAcceptCookie(true);
        root.addView(web, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void loadStart() {
        offline = false;
        pageReady = false;
        bar.setVisibility(View.VISIBLE);
        handler.removeCallbacks(pageTimeout);
        handler.postDelayed(pageTimeout, PAGE_TIMEOUT_MS);
        if (web != null) web.loadUrl(START_URL);
    }

    /** First run needs the network for the model download; say so instead of a blank page. */
    private void showOffline() {
        if (offline || web == null) return;
        offline = true;
        bar.setVisibility(View.GONE);
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#141413;color:#ecebe4;font-family:system-ui,sans-serif;"
                + "padding:56px 24px;text-align:center;line-height:1.6}"
                + "h1{font-family:Georgia,serif;font-weight:400;font-size:32px;margin:0 0 12px}"
                + "p{color:#a29d92;font-size:15px;margin:0 auto;max-width:34ch}"
                + "a{display:inline-block;margin-top:28px;padding:12px 22px;border:1px solid #d97757;"
                + "border-radius:999px;color:#d97757;text-decoration:none;font-size:15px}</style></head>"
                + "<body><h1>Favilla</h1>"
                + "<p>Serve una connessione: al primo avvio il modello viene scaricato nel telefono.</p>"
                + "<a href='favilla://retry'>Riprova</a></body></html>";
        web.loadDataWithBaseURL(START_URL, html, "text/html", "utf-8", null);
    }

    /**
     * A big model on a small phone can kill the WebView renderer (out of memory). Without
     * this the whole app disappears; here the page is rebuilt and the chat comes back.
     */
    @TargetApi(Build.VERSION_CODES.O)
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        if (view == web) {
            root.removeView(view);
            view.destroy();
            web = null;
            installWebView();
            Toast.makeText(this, "Memoria esaurita: ricarico Favilla", Toast.LENGTH_LONG).show();
            loadStart();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    // The worker keeps generating even when the app is in the background: pausing timers
    // is what stops the CPU (and the battery) from being spent on a page nobody sees.
    @Override
    protected void onPause() {
        if (web != null) {
            web.onPause();
            web.pauseTimers();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.resumeTimers();
            web.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(pageTimeout);
        if (web != null) {
            root.removeView(web);
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
