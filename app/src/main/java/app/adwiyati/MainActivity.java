package app.adwiyati;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_RING = 2;
    private String ringKind = "med";
    private android.media.MediaPlayer preview;
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

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        }

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
        if (requestCode == REQ_RING) {
            if (resultCode == RESULT_OK && data != null) {
                Uri u = data.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (u != null) {
                    AlarmHelper.setSoundUri(this, ringKind, u);
                    final String js = "window.onDevSound&&window.onDevSound("
                            + org.json.JSONObject.quote(ringKind) + ")";
                    web.post(new Runnable() {
                        @Override
                        public void run() {
                            web.evaluateJavascript(js, null);
                        }
                    });
                }
            }
            return;
        }
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

    String ringtoneTitle(String uri) {
        try {
            if (uri == null || uri.length() == 0) return "";
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this, Uri.parse(uri));
            return r.getTitle(this);
        } catch (Exception e) {
            return "";
        }
    }

    /** Functions the web page can call as window.Android.* */
    /** Keeps Arabic and other letters in file names; only removes characters Android forbids. */
    static String cleanFileName(String name) {
        String n = name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = (dot > 0 && n.length() - dot <= 8) ? n.substring(dot) : "";
            n = n.substring(0, 110) + ext;
        }
        return n.isEmpty() ? "file" : n;
    }

    private class Bridge {

        @JavascriptInterface
        public boolean has() {
            return true;
        }

        /**
         * Sets an alarm inside this app (works offline, with the app closed).
         * days non-empty = repeats every day.
         */
        @JavascriptInterface
        public void setAlarm(final int hour, final int minute, final String label,
                             final String days, final boolean silent) {
            final boolean daily = days != null && days.trim().length() > 0;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AlarmHelper.schedule(MainActivity.this, hour, minute, label, daily);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر ضبط التنبيه", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void setAlarmAt(final String id, final double at, final String label) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        AlarmHelper.scheduleAt(MainActivity.this, id, (long) at, label);
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public int cancelAppt(final String prefix) {
            return AlarmHelper.cancelAtPrefix(MainActivity.this, prefix == null ? "" : prefix);
        }

        @JavascriptInterface
        public int cancelAlarms(final String name) {
            return AlarmHelper.cancelByName(MainActivity.this, name == null ? "" : name);
        }

        @JavascriptInterface
        public String getSound(String kind) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("src", AlarmHelper.soundSrc(MainActivity.this, kind));
                o.put("tone", AlarmHelper.soundTone(MainActivity.this, kind));
                o.put("name", ringtoneTitle(AlarmHelper.soundUri(MainActivity.this, kind)));
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void setSound(String kind, String src, int tone) {
            AlarmHelper.setSound(MainActivity.this, kind, src, tone);
        }

        @JavascriptInterface
        public void playTone(final int n) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (preview != null) {
                            preview.release();
                            preview = null;
                        }
                        preview = android.media.MediaPlayer.create(MainActivity.this, AlarmHelper.toneRes(n));
                        if (preview != null) preview.start();
                    } catch (Exception ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public void pickRingtone(final String kind) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ringKind = kind;
                        Intent i = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
                        i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                android.media.RingtoneManager.TYPE_ALL);
                        i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة");
                        i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                        i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
                        String cur = AlarmHelper.soundUri(MainActivity.this, kind);
                        if (cur.length() > 0) {
                            i.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur));
                        }
                        startActivityForResult(i, REQ_RING);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "تعذر فتح قائمة النغمات", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void testRingtone(final String kind, final String say) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent s = new Intent(MainActivity.this, AlarmService.class);
                    boolean appt = "appt".equals(kind);
                    s.putExtra("label", (appt ? "تجربة نغمة الموعد" : "تجربة نغمة الدواء") + "\u0001"
                            + ((say != null && say.length() > 0) ? say : (appt ? "الموعد القادم بعد 7 أيام" : "حان موعد الدواء")) + "\u0001"
                            + (appt ? "appt" : "med"));
                    androidx.core.content.ContextCompat.startForegroundService(MainActivity.this, s);
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
                        String safe = cleanFileName(name);
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

        /** Returns the text currently on the clipboard ("" if none). */
        @JavascriptInterface
        public String getClipboard() {
            final String[] out = {""};
            final CountDownLatch done = new CountDownLatch(1);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                                && cm.getPrimaryClip().getItemCount() > 0) {
                            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                            if (t != null) out[0] = t.toString();
                        }
                    } catch (Exception ignored) {
                    }
                    done.countDown();
                }
            });
            try {
                done.await(1500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            return out[0];
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
                        File f = new File(dir, cleanFileName(name));
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
