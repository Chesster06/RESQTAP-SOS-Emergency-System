/**
 * ResQTap Web Central Configuration
 * Pusat konfigurasi tunggal bagi semua kunci API & perkhidmatan luaran (Supabase, Firebase)
 * untuk Portal Web ResQTap.
 */

// Supabase configuration
export const SUPABASE_URL = "https://your-project.supabase.co";
export const SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY";

// Firebase client configuration
export const firebaseConfig = {
  apiKey: "YOUR_FIREBASE_API_KEY",
  authDomain: "your-project-id.firebaseapp.com",
  databaseURL: "https://your-project-id-default-rtdb.firebaseio.com",
  projectId: "your-project-id",
  storageBucket: "your-project-id.firebasestorage.app"
};
