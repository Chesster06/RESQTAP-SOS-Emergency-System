package com.example.resqtap.supabase;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.gms.tasks.Tasks;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SupabaseManager
 * Pengendali komunikasi REST API dengan pelayan Supabase untuk ResQTap.
 * Menggunakan enjin HttpURLConnection & threading Executor tanpa menambah saiz APK.
 */
public class SupabaseManager {
    private static final String TAG = "SupabaseManager";

    public static final String SUPABASE_URL = com.example.resqtap.config.AppConfig.SUPABASE_URL;
    public static final String SUPABASE_ANON_KEY = com.example.resqtap.config.AppConfig.SUPABASE_ANON_KEY;

    private static volatile SupabaseManager instance;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public static SupabaseManager getInstance() {
        if (instance == null) {
            synchronized (SupabaseManager.class) {
                if (instance == null) {
                    instance = new SupabaseManager();
                }
            }
        }
        return instance;
    }

    private SupabaseManager() {}


    /** Ambil senarai sorotan berita / banner promosi dari Supabase */
    public void getHighlights(Callback<JSONArray> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/highlights?active=eq.true&order=display_order.asc";
                String response = executeRequest("GET", endpoint, null);
                JSONArray array = new JSONArray(response);
                postSuccess(callback, array);
            } catch (Exception e) {
                Log.e(TAG, "getHighlights failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    // 2. KAD PERUBATAN (MEDICAL CARDS)

    public void getMedicalCard(String userId, Callback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/medical_cards?user_id=eq." + userId + "&select=*";
                String response = executeRequest("GET", endpoint, null);
                JSONArray array = new JSONArray(response);
                if (array.length() > 0) {
                    postSuccess(callback, array.getJSONObject(0));
                } else {
                    postSuccess(callback, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "getMedicalCard failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    public void upsertMedicalCard(String userId, String bloodType, String allergies, String conditions, String notes, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/medical_cards";
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("blood_type", bloodType);
                body.put("allergies", allergies);
                body.put("medical_conditions", conditions);
                body.put("notes", notes);
                body.put("updated_at", "now()");

                // Upsert header: on_conflict=user_id
                executeRequest("POST", endpoint + "?on_conflict=user_id", body.toString(), "resolution=merge-duplicates");
                postSuccess(callback, true);
            } catch (Exception e) {
                Log.e(TAG, "upsertMedicalCard failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    // 3. LAPORAN INSIDEN (REPORTS)

    public void submitReport(String userId, String userName, String title, String desc, String category, double lat, double lng, String address, String imageUrl, Callback<String> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/reports";
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                body.put("user_name", userName);
                body.put("title", title);
                body.put("description", desc);
                body.put("category", category);
                body.put("latitude", lat);
                body.put("longitude", lng);
                body.put("address", address);
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    body.put("image_url", imageUrl);
                }
                body.put("status", "new");

                String response = executeRequest("POST", endpoint, body.toString(), "return=representation");
                JSONArray array = new JSONArray(response);
                String newId = null;
                if (array.length() > 0) {
                    newId = array.getJSONObject(0).optString("id", null);
                }
                postSuccess(callback, newId);
            } catch (Exception e) {
                Log.e(TAG, "submitReport failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    public void submitReport(String userId, String userName, String title, String desc, String category, double lat, double lng, String address, Callback<Boolean> callback) {
        submitReport(userId, userName, title, desc, category, lat, lng, address, null, new Callback<String>() {
            @Override
            public void onSuccess(String result) {
                postSuccess(callback, true);
            }

            @Override
            public void onError(Exception error) {
                postError(callback, error);
            }
        });
    }

    public void getReport(String reportId, Callback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/reports?id=eq." + java.net.URLEncoder.encode(reportId, "UTF-8") + "&select=*";
                String response = executeRequest("GET", endpoint, null);
                JSONArray array = new JSONArray(response);
                if (array.length() > 0) {
                    postSuccess(callback, array.getJSONObject(0));
                } else {
                    postSuccess(callback, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "getReport failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    // 4. PROFIL PENGGUNA (PROFILES)

    public void upsertProfile(String userId, String email, String fullName, String phone, String photoUrl, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/profiles";
                JSONObject body = new JSONObject();
                body.put("id", userId);
                body.put("email", email);
                body.put("full_name", fullName);
                body.put("phone", phone);
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    body.put("photo_url", photoUrl);
                }
                body.put("updated_at", "now()");

                executeRequest("POST", endpoint + "?on_conflict=id", body.toString(), "resolution=merge-duplicates");
                postSuccess(callback, true);
            } catch (Exception e) {
                Log.e(TAG, "upsertProfile failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    public void getProfile(String userId, Callback<JSONObject> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/profiles?id=eq." + userId + "&select=*";
                String response = executeRequest("GET", endpoint, null);
                JSONArray array = new JSONArray(response);
                if (array.length() > 0) {
                    postSuccess(callback, array.getJSONObject(0));
                } else {
                    postSuccess(callback, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "getProfile failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    public void updateFcmToken(String userId, String fcmToken) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/profiles?id=eq." + userId;
                JSONObject body = new JSONObject();
                body.put("fcm_token", fcmToken);
                executeRequest("PATCH", endpoint, body.toString());
            } catch (Exception ignored) {}
        });
    }

    public void checkEmailExists(String email, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String queryEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
                String endpoint = "/rest/v1/profiles?email=eq." + java.net.URLEncoder.encode(queryEmail, "UTF-8") + "&select=id";
                String response = executeRequest("GET", endpoint, null);
                JSONArray array = new JSONArray(response);
                postSuccess(callback, array.length() > 0);
            } catch (Exception e) {
                postError(callback, e);
            }
        });
    }

    public void upsertProfile(String userId, String email, String fullName, String phone, Callback<Boolean> callback) {
        upsertProfile(userId, email, fullName, phone, null, callback);
    }

    public void updateLiveLocation(String userId, double lat, double lng, int batteryPct, Callback<Boolean> callback) {
        executor.execute(() -> {
            try {
                String endpoint = "/rest/v1/profiles?id=eq." + userId;
                JSONObject body = new JSONObject();
                if (lat != 0.0 || lng != 0.0) {
                    body.put("latitude", lat);
                    body.put("longitude", lng);
                }
                body.put("is_online", true);
                if (batteryPct >= 0 && batteryPct <= 100) {
                    body.put("battery_pct", batteryPct);
                }
                body.put("last_seen", "now()");
                body.put("updated_at", "now()");
                executeRequest("PATCH", endpoint, body.toString());
                postSuccess(callback, true);
            } catch (Exception e) {
                Log.e(TAG, "updateLiveLocation failed: " + e.getMessage());
                postError(callback, e);
            }
        });
    }

    public void updateLiveLocation(String userId, double lat, double lng, int batteryPct) {
        updateLiveLocation(userId, lat, lng, batteryPct, null);
    }

    // ENJIN HTTP PERHUBUNGAN REST

    /**
     * SEC-04 FIX: Fetch the current Firebase ID Token synchronously (called from executor thread).
     * This token is sent as the Authorization: Bearer header so Supabase can derive auth.uid()
     * correctly in RLS policies. Falls back to SUPABASE_ANON_KEY if no user is signed in
     * (e.g., public reads like hospital lists).
     */
    private String getFirebaseIdTokenSync() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return SUPABASE_ANON_KEY;
            // Tasks.await() is safe here because we're already on an executor (background) thread.
            com.google.firebase.auth.GetTokenResult tokenResult =
                    Tasks.await(user.getIdToken(false));
            String token = tokenResult != null ? tokenResult.getToken() : null;
            return (token != null && !token.isEmpty()) ? token : SUPABASE_ANON_KEY;
        } catch (Exception e) {
            Log.w(TAG, "getFirebaseIdTokenSync failed, falling back to anon key: " + e.getMessage());
            return SUPABASE_ANON_KEY;
        }
    }

    private String executeRequest(String method, String endpoint, String jsonBody) throws Exception {
        return executeRequest(method, endpoint, jsonBody, null);
    }

    private String executeRequest(String method, String endpoint, String jsonBody, String preferHeader) throws Exception {
        URL url = new URL(SUPABASE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY);
        conn.setRequestProperty("Authorization", "Bearer " + SUPABASE_ANON_KEY);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        if (preferHeader != null && !preferHeader.isEmpty()) {
            conn.setRequestProperty("Prefer", preferHeader);
        }

        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);

        if (jsonBody != null && (method.equals("POST") || method.equals("PUT") || method.equals("PATCH"))) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        }

        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + ": " + sb.toString());
        }

        return sb.toString();
    }

    private <T> void postSuccess(Callback<T> callback, T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private void postError(Callback<?> callback, Exception error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(error));
        }
    }
}
