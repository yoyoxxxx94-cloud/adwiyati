package app.adwiyati;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                try {
                    Intent intent;
                    if (params.isCaptureEnabled()) {
                        File dir = new File(getCacheDir(), "photos");
                        dir.mkdirs();
                        File f = File.createTempFile("med_", ".jpg", dir);
                        cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                        intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("", cameraUri));
                    } else {
                        cameraUri = null;
                        intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    }
                    startActivityForResult(intent, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(HOME);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) {
            web.saveState(outState);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE || filePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (cameraUri != null) {
                results = new Uri[]{cameraUri};
            } else if (data != null) {
                ClipData cd = data.getClipData();
                if (cd != null && cd.getItemCount() > 0) {
                    results = new Uri[cd.getItemCount()];
                    for (int i = 0; i < cd.getItemCount(); i++) {
                        results[i] = cd.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraUri = null;
    }

    @Override
    public void onBackPressed() {
        // The web page is a single screen; close the app on back.
        super.onBackPressed();
    }

    /** Functions the web page can call as window.Android.* */
    private class Bridge {

        @JavascriptInterface
        public boolean has() {
            return true;
        }

        /**
         * Open (or silently create) an alarm in the phone's Clock app.
         * days: comma separated, 1=Sunday ... 7=Saturday. Empty = one time alarm.
         */
        @JavascriptInterface
        public void setAlarm(final int hour, final int minute, final String label,
                             final String days, final boolean silent) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                        i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                        i.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                        i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
                        i.putExtra(AlarmClock.EXTRA_VIBRATE, true);
                        if (days != null && days.trim().length() > 0) {
                            ArrayList<Integer> d = new ArrayList<Integer>();
                            for (String x : days.split(",")) {
                                d.add(Integer.parseInt(x.trim()));
                            }
                            i.putExtra(AlarmClock.EXTRA_DAYS, d);
                        }
                        i.putExtra(AlarmClock.EXTRA_SKIP_UI, silent);
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر فتح تطبيق الساعة", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        /** Save a PNG into the phone gallery (Pictures/Adwiyati). */
        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String base64) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] data = Base64.decode(base64, Base64.DEFAULT);
                        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                        if (Build.VERSION.SDK_INT >= 29) {
                            ContentValues v = new ContentValues();
                            v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, safe);
                            v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                            v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                    Environment.DIRECTORY_PICTURES + "/Adwiyati");
                            Uri uri = getContentResolver().insert(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                            OutputStream o = getContentResolver().openOutputStream(uri);
                            o.write(data);
                            o.close();
                        } else {
                            File dir = new File(Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES), "Adwiyati");
                            dir.mkdirs();
                            File f = new File(dir, safe);
                            FileOutputStream o = new FileOutputStream(f);
                            o.write(data);
                            o.close();
                            MediaScannerConnection.scanFile(MainActivity.this,
                                    new String[]{f.getAbsolutePath()}, new String[]{mime}, null);
                        }
                        Toast.makeText(MainActivity.this, "تم حفظ الصورة في المعرض (Adwiyati)", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر حفظ الصورة", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        /** Share a file (PNG list, calendar file ...) with any app, e.g. WhatsApp. */
        @JavascriptInterface
        public void shareFile(final String name, final String mime, final String base64) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        byte[] data = Base64.decode(base64, Base64.DEFAULT);
                        File dir = new File(getCacheDir(), "share");
                        dir.mkdirs();
                        File f = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
                        FileOutputStream o = new FileOutputStream(f);
                        o.write(data);
                        o.close();
                        Uri uri = FileProvider.getUriForFile(MainActivity.this,
                                getPackageName() + ".fileprovider", f);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType(mime);
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "إرسال"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر إرسال الملف", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }
}
