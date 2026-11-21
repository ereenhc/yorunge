package com.oyun.yorunge;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    // Güvenli alan boşlukları (CSS px) — çentik / yuvarlak köşe için
    private float insetTop, insetBottom, insetLeft, insetRight;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Çentikli telefonlarda ekranın üst kısmında siyah bant kalmasın, tüm ekran kullanılsın
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#05060f"));
        // Kaydırırken sağda kaydırma çubuğu ve kenar parlaması görünmesin
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // rekor, ✦ ve mağaza kayıtları (localStorage)
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        // Telefonun sistem yazı boyutu büyükse arayüz taşmasın
        s.setTextZoom(100);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

        // Oyun içindeki "Skor kartı" butonu bu köprüyü çağırır
        webView.addJavascriptInterface(new ShareBridge(), "YorungeAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pushInsetsToPage();
            }
        });
        webView.setOnApplyWindowInsetsListener((v, insets) -> {
            readInsets(insets);
            pushInsetsToPage();
            return v.onApplyWindowInsets(insets);
        });
        setContentView(webView);
        hideSystemUi();

        webView.loadUrl("file:///android_asset/index.html");
    }

    /** Oyundan gelen PNG'yi (base64) önbelleğe yazıp Android paylaşım menüsünü açar. */
    private class ShareBridge {
        @JavascriptInterface
        public void shareImage(final String base64Png, final String text) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64Png, Base64.DEFAULT);
                    File dir = new File(getCacheDir(), "share");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, "yorunge-skor.png");
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(bytes);
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".fileprovider", file);

                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/png");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_TEXT, text);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Skor kartını paylaş"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Paylaşılamadı: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void readInsets(WindowInsets insets) {
        float d = getResources().getDisplayMetrics().density;
        int t = 0, b = 0, l = 0, r = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            DisplayCutout c = insets.getDisplayCutout();
            if (c != null) {
                t = c.getSafeInsetTop(); b = c.getSafeInsetBottom();
                l = c.getSafeInsetLeft(); r = c.getSafeInsetRight();
            }
        }
        insetTop = t / d; insetBottom = b / d; insetLeft = l / d; insetRight = r / d;
    }

    /** Boşlukları sayfaya CSS değişkeni (--sat/--sab/--sal/--sar) olarak iletir. */
    private void pushInsetsToPage() {
        if (webView == null) return;
        String js = "(function(s){s.setProperty('--sat','" + insetTop + "px');"
                + "s.setProperty('--sab','" + insetBottom + "px');"
                + "s.setProperty('--sal','" + insetLeft + "px');"
                + "s.setProperty('--sar','" + insetRight + "px');"
                + "window.dispatchEvent(new Event('resize'));})(document.documentElement ? document.documentElement.style : document.createElement('i').style)";
        webView.evaluateJavascript(js, null);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    /** Geri tuşu: oyun içindeyse duraklatır / bir önceki ekrana döner; ana menüde iki kez basılınca çıkar. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView == null) { super.onBackPressed(); return; }
        webView.evaluateJavascript("(window.yorungeBack ? yorungeBack() : 'exit')", result -> {
            if (result != null && result.contains("exit")) finish();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            // Telefon çalınca / başka uygulamaya geçince oyunu duraklat
            webView.evaluateJavascript("window.yorungeHidden && yorungeHidden()", null);
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
