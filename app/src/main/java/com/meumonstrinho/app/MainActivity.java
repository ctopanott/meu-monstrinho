package com.meumonstrinho.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_SAVE = 2;
    private static final int REQ_NOTIF = 10;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingSaveText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        applyBars(isNight());
        web.loadUrl("file:///android_asset/www/index.html");

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
        Reminders.scheduleNext(this);
    }

    private boolean isNight() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressWarnings("deprecation")
    private void applyBars(boolean dark) {
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor(dark ? "#1E1720" : "#FBF6F8"));
        w.setNavigationBarColor(Color.parseColor(dark ? "#2A2130" : "#FFFFFF"));
        View d = w.getDecorView();
        int flags = d.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        d.setSystemUiVisibility(flags);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
        } else if (requestCode == REQ_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveText != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData())) {
                    if (os != null) os.write(pendingSaveText.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup salvo!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Não consegui salvar o backup.", Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveText = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("(window.appBack && window.appBack()) ? 'yes' : 'no'", value -> {
            if (value == null || !value.contains("yes")) exitApp();
        });
    }

    @SuppressWarnings("deprecation")
    private void exitApp() {
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Reminders.scheduleNext(this);
    }

    private class Bridge {
        private SharedPreferences prefs() {
            return getSharedPreferences(Reminders.PREFS, MODE_PRIVATE);
        }

        @JavascriptInterface
        public void setStatus(String json) {
            prefs().edit().putString("status", json).apply();
        }

        @JavascriptInterface
        public void setReminders(String json) {
            prefs().edit().putString("rem", json).apply();
            Reminders.scheduleNext(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isDark() {
            return isNight();
        }

        @JavascriptInterface
        public void setBars(boolean dark) {
            runOnUiThread(() -> applyBars(dark));
        }

        @JavascriptInterface
        public void saveFile(String name, String text) {
            pendingSaveText = text;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, name);
            runOnUiThread(() -> {
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(MainActivity.this, "Não encontrei onde salvar.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareText(String text) {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            runOnUiThread(() -> startActivity(Intent.createChooser(send, "Compartilhar")));
        }

        @JavascriptInterface
        public void printPage(String name) {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                if (pm != null) {
                    pm.print(name, web.createPrintDocumentAdapter(name), new PrintAttributes.Builder().build());
                }
            });
        }

        @JavascriptInterface
        public void openBatterySettings() {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }
}
