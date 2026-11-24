package com.zadalqolob.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;
import androidx.core.view.WindowCompat;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 7401;
    private WebView webView;
    private WebViewAssetLoader assetLoader;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(Color.rgb(6, 43, 57));
        getWindow().setNavigationBarColor(Color.rgb(3, 28, 41));
        getWindow().getDecorView().setSystemUiVisibility(0);

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(6, 43, 57));
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setTextZoom(100);
        webView.addJavascriptInterface(new AndroidBridge(this), "ZadAndroid");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://appassets.androidplatform.net/assets/")) return false;
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                        startActivity(intent);
                    } catch (Exception ignored) { }
                    return true;
                }
                return true;
            }
        });

        if (savedInstanceState != null) {
            if (webView.restoreState(savedInstanceState) == null) {
                webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
            }
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
        }
    }

    private class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface
        public void requestNotificationPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                } else {
                    Toast.makeText(context, "الإشعارات مفعّلة أو لا تحتاج إلى إذن إضافي", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void scheduleDailyReminder(String id, String title, String message, int hour, int minute) {
            if (id == null || id.trim().isEmpty() || hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
            try {
                JSONObject data = new JSONObject();
                data.put("title", title == null ? "تذكير من زاد القلوب" : title);
                data.put("message", message == null ? "حان وقت وردك اليومي" : message);
                data.put("hour", hour);
                data.put("minute", minute);
                context.getSharedPreferences("zad_reminders", Context.MODE_PRIVATE)
                        .edit().putString("reminder_" + id, data.toString()).apply();
                ReminderReceiver.schedule(context, id, title, message, hour, minute);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void cancelDailyReminder(String id) {
            if (id == null) return;
            context.getSharedPreferences("zad_reminders", Context.MODE_PRIVATE)
                    .edit().remove("reminder_" + id).apply();
            ReminderReceiver.cancel(context, id);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView == null) {
            super.onBackPressed();
            return;
        }
        webView.evaluateJavascript(
                "(function(){try{return window.zadHandleAndroidBack?String(window.zadHandleAndroidBack()):'false'}catch(e){return 'false'}})()",
                value -> {
                    if (!"true".equals(value)) {
                        if (webView != null && webView.canGoBack()) webView.goBack();
                        else finish();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("ZadAndroid");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
