/**
 * ResQTap Web Central Configuration
 * Pusat konfigurasi tunggal bagi semua kunci API & perkhidmatan luaran (Supabase, Firebase)
 * untuk Portal Web ResQTap.
 */

// Supabase configuration
export const SUPABASE_URL = "https://umcxapvojtoxqlpnrwdw.supabase.co";
export const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVtY3hhcHZvanRveHFscG5yd2R3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTEzMTg2OTksImV4cCI6MjEwNjg5NDY5OX0.8qDJYad_rTwlzAqmF0Z1OiRgQ9ocxaluN7MIjz0mL0M";

// Firebase client configuration
export const firebaseConfig = {
  apiKey: "AIzaSyDZ8X0sDpjbaMLt20DVA4ocNOzw9rqy-Xw",
  authDomain: "resqtap-b9ff5.firebaseapp.com",
  databaseURL: "https://resqtap-b9ff5-default-rtdb.firebaseio.com",
  projectId: "resqtap-b9ff5",
  storageBucket: "resqtap-b9ff5.firebasestorage.app"
};
