package com.example.resqtap.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AvatarUtils
 * Utility load avatar user dari Base64, URL (http/https), Storage gs://, atau Uri lokal.
 */
public final class AvatarUtils {
    private static final String TAG = "AvatarUtils";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final LruCache<String, Bitmap> bitmapCache = new LruCache<>(80);

    private AvatarUtils() {}

    /** Fungsi utama untuk applyAvatar dengan sokongan Base64, Remote URL, dan Uri. */
    public static void applyAvatar(ImageView view, String photoB64, String photoUrl, String photoUri, int fallbackRes) {
        if (view == null) return;
        view.setImageTintList(null);

        String b64 = String.valueOf(photoB64 == null ? "" : photoB64).trim();
        if (!b64.isEmpty()) {
            Bitmap cached = bitmapCache.get(b64);
            if (cached != null) {
                view.setTag(null);
                view.setImageBitmap(cached);
                return;
            }
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    bitmapCache.put(b64, bmp);
                    view.setTag(null);
                    view.setImageBitmap(bmp);
                    return;
                }
            } catch (Exception ignored) {}
        }

        String uri = String.valueOf(photoUri == null ? "" : photoUri).trim();
        if (!uri.isEmpty()) {
            try {
                view.setTag(null);
                view.setImageURI(Uri.parse(uri));
                return;
            } catch (Exception ignored) {}
        }

        String rawUrl = String.valueOf(photoUrl == null ? "" : photoUrl).trim();
        String url = getHighResUrl(rawUrl);
        if (!url.isEmpty() && (url.startsWith("http") || url.startsWith("gs://"))) {
            Bitmap cachedUrl = bitmapCache.get(url);
            if (cachedUrl != null) {
                view.setTag(null);
                view.setImageBitmap(cachedUrl);
                return;
            }

            view.setImageResource(fallbackRes);
            view.setTag(url);
            executor.execute(() -> {
                Bitmap downloaded = loadRemoteBitmap(url);
                if (downloaded != null) {
                    bitmapCache.put(url, downloaded);
                    mainHandler.post(() -> {
                        Object currentTag = view.getTag();
                        if (currentTag != null && url.equals(currentTag.toString())) {
                            view.setImageTintList(null);
                            view.setImageBitmap(downloaded);
                        }
                    });
                }
            });
            return;
        }

        view.setTag(null);
        view.setImageResource(fallbackRes);
    }

    /** Overload: Base64 + PhotoUrl */
    public static void applyAvatar(ImageView view, String photoB64, String photoUrl, int fallbackRes) {
        applyAvatar(view, photoB64, photoUrl, "", fallbackRes);
    }

    /** Overload: Base64 + PhotoUri (mengekalkan keserasian lama) */
    public static void applyAvatar(ImageView view, String photoB64, int fallbackRes) {
        applyAvatar(view, photoB64, "", "", fallbackRes);
    }

    /** Muat turun Bitmap daripada URL atau Firebase Storage */
    private static Bitmap loadRemoteBitmap(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.trim().isEmpty()) return null;
        String clean = sourceUrl.trim();

        if (clean.startsWith("gs://")) {
            try {
                com.google.firebase.storage.StorageReference ref =
                        com.google.firebase.storage.FirebaseStorage.getInstance().getReferenceFromUrl(clean);
                com.google.android.gms.tasks.Task<byte[]> task = ref.getBytes(4L * 1024L * 1024L);
                byte[] bytes = com.google.android.gms.tasks.Tasks.await(task, 12, java.util.concurrent.TimeUnit.SECONDS);
                if (bytes != null && bytes.length > 0) {
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                }
            } catch (Exception e) {
                Log.d(TAG, "Failed to load gs:// image: " + e.getMessage());
            }
            return null;
        }

        if (clean.startsWith("http")) {
            Bitmap bmp = downloadHttpStream(clean);
            if (bmp == null && !clean.equals(sourceUrl.trim())) {
                bmp = downloadHttpStream(sourceUrl.trim());
            }
            return bmp;
        }

        return null;
    }

    private static Bitmap downloadHttpStream(String endpoint) {
        InputStream in = null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                in = conn.getInputStream();
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to load http image from " + endpoint + ": " + e.getMessage());
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Tukar URL foto profil Google bersaiz kecil (=s96-c) kepada resolusi tinggi (=s1000-c)
     * supaya tidak kabur / berpiksel apabila dipaparkan pada skrin besar.
     */
    public static String getHighResUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String clean = url.trim();
        if (clean.contains("googleusercontent.com")) {
            if (clean.matches(".*=s\\d+.*")) {
                return clean.replaceAll("=s\\d+[^&?]*", "=s1000-c");
            } else if (clean.matches(".*/s\\d+.*")) {
                return clean.replaceAll("/s\\d+[^/]*", "/s1000-c");
            } else if (!clean.contains("=")) {
                return clean + "=s1000-c";
            }
        }
        return clean;
    }

    /**
     * Potong Bitmap menjadi bentuk bulatan sempurna (circular bitmap)
     * supaya rendering peranti tidak menunjukkan bucu petak walaupun di-draw pada canvas biasa.
     */
    public static Bitmap getCircularBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);

        final android.graphics.Paint paint = new android.graphics.Paint();
        final android.graphics.Rect rect = new android.graphics.Rect(
                (bitmap.getWidth() - size) / 2,
                (bitmap.getHeight() - size) / 2,
                (bitmap.getWidth() + size) / 2,
                (bitmap.getHeight() + size) / 2
        );
        final android.graphics.Rect destRect = new android.graphics.Rect(0, 0, size, size);

        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, destRect, paint);
        return output;
    }
}
