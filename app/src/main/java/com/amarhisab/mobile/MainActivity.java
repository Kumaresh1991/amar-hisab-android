package com.amarhisab.mobile;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.OutputStream;
import android.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQUEST = 1001;
    private static final String HOME =
            "https://amarhisab-aa196.web.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AppBridge(), "AmarHisabAndroid");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;

                Intent intent = params.createIntent();
                try {
                    startActivityForResult(intent, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {

                Uri uri = request.getUrl();
                String host = uri.getHost();

                if ("amarhisab-aa196.web.app".equals(host)) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installPrintSupport();
                installBlobDownloadSupport();
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url, String userAgent, String contentDisposition,
                    String mimeType, long contentLength) {
                downloadFile(url, userAgent, contentDisposition, mimeType);
            }
        });

        webView.loadUrl(HOME);
    }

    private void downloadFile(
            String url, String userAgent,
            String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));

            String fileName = android.webkit.URLUtil.guessFileName(
                    url, contentDisposition, mimeType);

            request.setTitle(fileName);
            request.setDescription("আমার হিসাব থেকে ডাউনলোড হচ্ছে");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, fileName);

            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            request.addRequestHeader("User-Agent", userAgent);

            DownloadManager manager =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            manager.enqueue(request);

            Toast.makeText(this, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "ডাউনলোড করা যায়নি", Toast.LENGTH_LONG).show();
        }
    }

    private void installPrintSupport() {
        String script =
                "(function(){"
                + "if(window.__amarPrintReady)return;"
                + "window.__amarPrintReady=true;"
                + "window.print=function(){AmarHisabAndroid.printPage();};"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void installBlobDownloadSupport() {
        String script =
                "(function(){"
                + "if(window.__amarBlobReady)return;"
                + "window.__amarBlobReady=true;"
                + "document.addEventListener('click',function(e){"
                + "var a=e.target.closest('a');"
                + "if(!a||!a.href||!a.href.startsWith('blob:'))return;"
                + "e.preventDefault();"
                + "fetch(a.href).then(r=>r.blob()).then(b=>{"
                + "var reader=new FileReader();"
                + "reader.onloadend=function(){"
                + "var s=reader.result.split(',')[1];"
                + "AmarHisabAndroid.saveBlob(s,a.download||'amar-hisab-file.xls',"
                + "b.type||'application/octet-stream');"
                + "};reader.readAsDataURL(b);"
                + "}).catch(()=>alert('ফাইল তৈরি করা যায়নি'));"
                + "},true);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    public class AppBridge {
        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager manager =
                            (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    android.print.PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter("AmarHisab");
                    manager.print("আমার হিসাব", adapter,
                            new PrintAttributes.Builder().build());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "PDF তৈরি করা যায়নি", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void saveBlob(String base64, String fileName, String mimeType) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                android.content.ContentValues values =
                        new android.content.ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS);

                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

                if (uri == null) throw new Exception("File create failed");

                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("Output failed");
                    out.write(bytes);
                }

                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "ফাইল Downloads-এ সেভ হয়েছে",
                                Toast.LENGTH_LONG).show());

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "ফাইল সেভ করা যায়নি",
                                Toast.LENGTH_LONG).show());
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri[] results = WebChromeClient.FileChooserParams
                    .parseResult(resultCode, data);
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
