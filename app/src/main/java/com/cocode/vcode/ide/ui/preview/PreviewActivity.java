package com.cocode.vcode.ide.ui.preview;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;

import com.cocode.vcode.ide.databinding.ActivityPreviewBinding;
import com.cocode.vcode.ide.ui.base.BaseActivity;
import com.cocode.vcode.ide.utils.UiUtils;

/**
 * PreviewActivity provides an in-app web environment for viewing HTML and web projects.
 * It utilizes a WebView with specialized configurations to support local file access,
 * allowing HTML files to correctly load associated CSS and JavaScript from the project directory.
 */
public class PreviewActivity extends BaseActivity {

    /**
     * Intent extra key for passing the URL to be previewed.
     */
    public static final String EXTRA_URL = "extra_preview_url";

    private ActivityPreviewBinding binding;

    private String currentUrl;
    private boolean isDesktopMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Retrieve the target URL from the intent; default to an error page if missing
        currentUrl = getIntent().getStringExtra(EXTRA_URL);
        if (currentUrl == null) {
            currentUrl = "file:///android_asset/sample_error.html"; // fallback
        }

        // Apply system bar insets to handle edge-to-edge display correctly
        UiUtils.applySystemBarInsets(binding.getRoot());

        binding.tvTitle.setText(currentUrl);

        setupWebView();
        setupFloatingPreviewStyles();
        setupListeners();

        loadUrl(currentUrl);

        // Handle the hardware back button to navigate WebView history if possible
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    /**
     * Configures the WebView with appropriate settings for a development environment.
     * This includes enabling JavaScript and setting up local file cross-access policies.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        android.webkit.WebSettings settings = binding.webView.getSettings();

        // Standard web features required for modern web apps
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);

        // --- SECURITY & ACCESS CONFIGURATION ---
        // We explicitly enable local file access and cross-access between file URLs.
        // This is necessary for a local web IDE preview, as it allows an HTML file
        // to load its relative assets (CSS, JS, Images) from the device filesystem.
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Configure zoom and viewport for a better mobile experience
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false); // Hides the default zoom buttons for a cleaner UI
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // Advanced Browser Capabilities
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Track and display page loading progress
        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    binding.progressLoading.setProgress(newProgress);
                    binding.progressLoading.setVisibility(View.VISIBLE);
                } else {
                    binding.progressLoading.setVisibility(View.GONE);
                }
            }
        });

        // Manage page navigation events and error handling
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                binding.layoutError.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                enforceViewport(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                enforceViewport(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                // Only show our custom error layout if the error happened on the main frame
                if (request.isForMainFrame()) {
                    showError(error.getDescription().toString());
                }
            }
        });
    }

    /**
     * Initializes click listeners for UI components like navigation and browser export.
     */
    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> binding.webView.reload());

        binding.btnTryAgain.setOnClickListener(v -> loadUrl(currentUrl));

        binding.btnToggleDesktop.setOnClickListener(v -> toggleDesktopMode());

        // Attempt to open the current preview URL in an external system browser
        binding.btnOpenBrowser.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);

                // Handle local file URLs by converting them to content URIs via FileProvider
                if (currentUrl != null && currentUrl.startsWith("file://")) {
                    String filePath = currentUrl.replace("file://", "");
                    java.io.File localFile = new java.io.File(filePath);

                    // Generate a secure content URI to share with the external app
                    android.net.Uri contentUri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            localFile
                    );

                    // Grant temporary read permissions to ensure the browser can access the file
                    browserIntent.setDataAndType(contentUri, "text/html");
                    browserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    // Standard internet URLs can be opened directly
                    browserIntent.setData(android.net.Uri.parse(currentUrl));
                }

                startActivity(browserIntent);
            } catch (Exception e) {
                // Inform the user if the browser handover fails
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Loads a specific URL into the WebView.
     *
     * @param url The URL or file path to load.
     */
    private void loadUrl(String url) {
        binding.layoutError.setVisibility(View.GONE);
        binding.webView.setVisibility(View.VISIBLE);
        binding.webView.loadUrl(url);
    }

    /**
     * Displays a custom error layout when a page fails to load.
     *
     * @param msg The error description to display.
     */
    private void showError(String msg) {
        binding.webView.setVisibility(View.GONE);
        binding.layoutError.setVisibility(View.VISIBLE);
        binding.tvErrorMsg.setText(msg);
    }

    private void enforceViewport(WebView view) {
        if (isDesktopMode) {
            view.evaluateJavascript(
                    "var meta = document.querySelector('meta[name=\"viewport\"]');" +
                            "if (meta) { meta.setAttribute('content', 'width=1024'); }" +
                            "else { " +
                            "  meta = document.createElement('meta');" +
                            "  meta.name = 'viewport';" +
                            "  meta.content = 'width=1024';" +
                            "  document.getElementsByTagName('head')[0].appendChild(meta);" +
                            "}", null);
        } else {
            view.evaluateJavascript(
                    "var meta = document.querySelector('meta[name=\"viewport\"]');" +
                            "if (meta) { meta.setAttribute('content', 'width=device-width, initial-scale=1.0'); }", null);
        }
    }

    private void setupFloatingPreviewStyles() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true);
        int baseColor = value.data;
        int glassAccentColor = (baseColor & 0x00FFFFFF) | 0xD9000000;

        android.graphics.drawable.GradientDrawable ovalDrawable = new android.graphics.drawable.GradientDrawable();
        ovalDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        ovalDrawable.setColor(glassAccentColor);
        binding.btnToggleDesktop.setBackground(ovalDrawable);
    }

    private void toggleDesktopMode() {
        isDesktopMode = !isDesktopMode;
        android.webkit.WebSettings settings = binding.webView.getSettings();

        if (isDesktopMode) {
            binding.btnToggleDesktop.setImageResource(com.cocode.vcode.ide.R.drawable.ic_mobile);
            String desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";
            settings.setUserAgentString(desktopUserAgent);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
        } else {
            binding.btnToggleDesktop.setImageResource(com.cocode.vcode.ide.R.drawable.ic_monitor);
            settings.setUserAgentString(null); // restores default user agent
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
        }

        binding.webView.reload();
        Toast.makeText(this, isDesktopMode ? "Desktop Mode" : "Mobile Mode", Toast.LENGTH_SHORT).show();
    }
}
