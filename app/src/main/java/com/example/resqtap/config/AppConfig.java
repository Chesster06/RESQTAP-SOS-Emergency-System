package com.example.resqtap.config;

/**
 * AppConfig
 * Pusat konfigurasi tunggal bagi semua kunci API, kredensial, dan URL perkhidmatan
 * untuk aplikasi ResQTap (Supabase, Firebase RTDB, Google Maps API).
 *
 * SEC-06 SECURITY HARDENING REQUIREMENTS:
 * =========================================
 * These keys are embedded in the APK as per Android mobile app conventions.
 * They MUST be hardened at the cloud console level as follows:
 *
 * 1. GOOGLE_MAPS_API_KEY:
 *    - Console: https://console.cloud.google.com → APIs & Services → Credentials
 *    - Set "Application restrictions" to "Android apps"
 *    - Add package name: com.example.resqtap
 *    - Add SHA-1 fingerprint for BOTH debug and release keystores
 *    - Restrict API to "Maps SDK for Android" only
 *
 * 2. SUPABASE_ANON_KEY:
 *    - This is a public JWT signed for the anon role; exposure is acceptable ONLY if
 *      Row Level Security (RLS) is enabled and correct on ALL tables.
 *    - SEC-04: Authorization header now uses Firebase ID Token (not this key as Bearer).
 *    - Verify RLS policies at: Supabase Dashboard → Table Editor → RLS policies
 *
 * 3. FIREBASE keys (project_id, storage_bucket, etc.):
 *    - Secured via Firebase Security Rules (database.rules.json / storage.rules)
 *    - Verify rules are deployed and not in test/open mode.
 *    - The Firebase Admin SDK service account JSON must NEVER be embedded in the APK.
 */
public final class AppConfig {

    private AppConfig() {
        // Prevent instantiation
    }

    // Supabase configuration: anon key is for the gateway; auth uses Firebase ID Token.
    public static final String SUPABASE_URL = "https://your-project.supabase.co";
    public static final String SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY";

    // Firebase Realtime Database configuration
    public static final String FIREBASE_DATABASE_URL = "https://your-project-default-rtdb.firebaseio.com";
    public static final String FIREBASE_PROJECT_ID = "your-project-id";
    public static final String FIREBASE_STORAGE_BUCKET = "your-project-id.firebasestorage.app";

    // Google Cloud & Maps API: restricted to package com.example.resqtap with SHA-1
    public static final String GOOGLE_MAPS_API_KEY = "YOUR_GOOGLE_MAPS_API_KEY";
}
