package com.smsindia.app.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.smsindia.app.R;

public class WebTaskActivity extends AppCompatActivity {

    private static final String TAG = "WebTaskActivity";
    private WebView webView;
    private ProgressBar progressBar;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_task);

        webView = findViewById(R.id.webview);
        progressBar = findViewById(R.id.progressBar);
        
        // Get URL from Intent
        String url = getIntent().getStringExtra("TARGET_URL");
        if (url == null || url.isEmpty()) {
            url = getString(R.string.default_url);
            Log.w(TAG, "No URL provided, using default");
        }

        // Configure WebView
        try {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.getSettings().setSupportZoom(true);
            webView.getSettings().setBuiltInZoomControls(true);
            webView.getSettings().setDisplayZoomControls(false);
            
            // Ensure links open INSIDE the app, not Chrome
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    super.onReceivedError(view, request, error);
                    Log.e(TAG, "WebView error: " + error.getDescription());
                    showErrorDialog();
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });
            
            // Show progress bar loading
            webView.setWebChromeClient(new WebChromeClient() {
                public void onProgressChanged(WebView view, int progress) {
                    if (progressBar == null) {
                        return;
                    }
                    if (progress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(progress);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            });

            webView.loadUrl(url);
            Log.d(TAG, "Loading URL: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Error configuring WebView", e);
            Toast.makeText(this, R.string.webview_error_message, Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private void showErrorDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle(R.string.webview_error_title)
            .setMessage(R.string.webview_error_message)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> finish())
            .setCancelable(false)
            .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Proper WebView cleanup to prevent memory leaks
        if (webView != null) {
            try {
                webView.clearHistory();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.onPause();
                webView.removeAllViews();
                webView.destroyDrawingCache();
                webView.destroy();
                webView = null;
                Log.d(TAG, "WebView cleaned up");
            } catch (Exception e) {
                Log.e(TAG, "Error destroying WebView", e);
            }
        }
    }
}
