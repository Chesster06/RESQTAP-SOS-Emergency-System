package com.example.resqtap.friend;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONObject;


/**
 * QrCodeUtils
 * Utility untuk generate QR code bitmap guna library ZXing.
 */
public class QrCodeUtils {
    private static final String TAG = "QrCodeUtils";

    public static class QrPayload {
        public String uid = "";
        public String publicId = "";
        public String name = "";

        /** Semak dan sahkan Valid. */
        public boolean isValid() {
            return isValidFirebaseKey(uid);
        }
    }

    /** Semak sama ada string merupakan kunci Firebase yang sah (tanpa ., #, $, [, ], /). */
    public static boolean isValidFirebaseKey(String key) {
        if (key == null) return false;
        String trimmed = key.trim();
        if (trimmed.length() < 3 || trimmed.length() > 128) return false;
        return !trimmed.matches(".*[.#$\\[\\]/].*");
    }

    public static String createQrPayload(String uid, String publicId, String name) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "resqtap_friend");
            obj.put("uid", String.valueOf(uid == null ? "" : uid).trim());
            obj.put("publicId", String.valueOf(publicId == null ? "" : publicId).trim());
            obj.put("name", String.valueOf(name == null ? "" : name).trim());
            return obj.toString();
        } catch (Exception e) {
            return "https://resqtap.web.app/connect?uid=" + uid + "&id=" + publicId;
        }
    }

    public static QrPayload parseQrPayload(String scannedData) {
        QrPayload payload = new QrPayload();
        if (scannedData == null || scannedData.trim().isEmpty()) return payload;
        String raw = scannedData.trim();

        try {
            if (raw.startsWith("{")) {
                JSONObject obj = new JSONObject(raw);
                payload.uid = obj.optString("uid", "").trim();
                payload.publicId = obj.optString("publicId", "").trim();
                payload.name = obj.optString("name", "").trim();
                if (payload.isValid()) return payload;
            }
        } catch (Exception ignored) {}

        if (raw.startsWith("resqtap://") || raw.startsWith("http://") || raw.startsWith("https://")) {
            try {
                android.net.Uri uri = android.net.Uri.parse(raw);
                String u = uri.getQueryParameter("uid");
                if (u == null || u.trim().isEmpty()) {
                    u = uri.getQueryParameter("targetUid");
                }
                String pub = uri.getQueryParameter("publicId");
                if (pub == null || pub.trim().isEmpty()) {
                    pub = uri.getQueryParameter("id");
                }
                String n = uri.getQueryParameter("name");

                payload.uid = String.valueOf(u == null ? "" : u).trim();
                payload.publicId = String.valueOf(pub == null ? "" : pub).trim();
                payload.name = String.valueOf(n == null ? "" : n).trim();
                if (payload.isValid()) return payload;
            } catch (Exception ignored) {}
        }

        if (raw.length() >= 10 && raw.length() <= 128 && !raw.contains(" ") && isValidFirebaseKey(raw)) {
            payload.uid = raw;
        }
        return payload;
    }

    public static Bitmap generateQrCodeBitmap(String content, int width, int height) {
        if (content == null || content.trim().isEmpty()) return null;
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            return encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, width, height);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate QR Code bitmap", e);
            try {
                BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height);
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                    }
                }
                return bitmap;
            } catch (Exception ex) {
                Log.e(TAG, "Fallback QR generation failed", ex);
                return null;
            }
        }
    }

    /**
     * Decode teks QR Code daripada Bitmap imej menggunakan ZXing.
     */
    public static String decodeQrFromBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            com.google.zxing.RGBLuminanceSource source = new com.google.zxing.RGBLuminanceSource(width, height, pixels);
            com.google.zxing.BinaryBitmap binaryBitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(source));
            com.google.zxing.MultiFormatReader reader = new com.google.zxing.MultiFormatReader();
            try {
                com.google.zxing.Result result = reader.decode(binaryBitmap);
                return result.getText();
            } catch (Exception e) {
                com.google.zxing.BinaryBitmap globalBitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.GlobalHistogramBinarizer(source));
                com.google.zxing.Result result = reader.decode(globalBitmap);
                return result.getText();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode QR code from bitmap", e);
            return null;
        }
    }
}
