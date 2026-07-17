package com.example.resqtap.utils;
import com.example.resqtap.R;

import com.example.resqtap.contacts.EmergencyContact;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;


/**
 * UserPrefs
 * Helper SharedPreferences untuk simpan data session user.
 */
public final class UserPrefs {
    private static final String PREFS = "ResQTap_prefs";
    private static final String KEY_UID = "uid";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_FIRST_NAME = "first_name";
    private static final String KEY_LAST_NAME = "last_name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PUBLIC_ID = "public_id";
    private static final String KEY_PHOTO_URI = "photo_uri";
    private static final String KEY_PHOTO_URL = "photo_url";
    private static final String KEY_PHOTO_B64 = "photo_b64";
    private static final String KEY_NIGHT_MODE = "night_mode";
    private static final String KEY_BLOOD_TYPE = "blood_type";
    private static final String KEY_ALLERGIES = "allergies";
    private static final String KEY_EXISTING_CONDITIONS = "existing_conditions";
    private static final String KEY_HIDE_MEDICAL_INFO = "hide_medical_info";
    private static final String KEY_VIBRATION_STRENGTH = "vibration_strength";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_FULLSCREEN_QUICK_MESSAGE = "fullscreen_quick_message";
    private static final String KEY_APP_LOCK_ENABLED = "app_lock_enabled";
    private static final String KEY_APP_LOCK_PIN = "app_lock_pin";
    private static final String KEY_FINGERPRINT_ENABLED = "fingerprint_enabled";
    private static final String KEY_BIOMETRIC_PIN = "biometric_pin";
    private static final String KEY_DURESS_SAFEGUARD_ENABLED = "duress_safeguard_enabled";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_RELIGION = "religion";
    private static final String KEY_PHONE = "phone_number";
    private static final String KEY_DOB = "date_of_birth";
    private static final String KEY_ETHNICITY = "ethnicity";
    private static final String KEY_IC_NUMBER = "ic_number";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_WEIGHT = "weight";
    private static final String KEY_HEIGHT = "height";
    private static final String KEY_MEDICATIONS = "medications";
    private static final String KEY_ORGAN_DONOR = "organ_donor";
    private static final String KEY_EMERGENCY_CONTACTS_JSON = "emergency_contacts_json";
    private static final String KEY_LOW_BATTERY_ALERT = "low_battery_alert";
    private static final String KEY_BATTERY_SAVER_MODE = "battery_saver_mode";
    private static final String KEY_NOTIF_UNREAD = "notif_unread";
    private static final String KEY_LAST_ADMIN_NOTIFICATION_ID = "last_admin_notification_id";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_PENDING_PHOTO_URI = "pending_photo_uri";
    private static final String KEY_ACTIVE_ROOM_CODE = "active_room_code";
    private static final String KEY_TOS_ACCEPTED = "tos_accepted";
    private static final String KEY_SOS_LAST_SEEN_AT_PREFIX = "sos_last_seen_at_";
    private static final String KEY_SOS_LAST_HANDLED_ID_PREFIX = "sos_last_handled_id_";
    private static final String KEY_SOS_LISTENER_BASELINE_PREFIX = "sos_listener_baseline_";
    private static final String KEY_ROOM_MAP_VISIBLE = "room_map_visible";
    private static final String KEY_SOS_FOCUS_ROOM = "sos_focus_room";
    private static final String KEY_SOS_FOCUS_SENDER_UID = "sos_focus_sender_uid";
    private static final String KEY_SOS_FOCUS_ALERT_ID = "sos_focus_alert_id";
    private static final String KEY_SOS_FOCUS_CREATED_AT = "sos_focus_created_at";
    private static final String KEY_SOS_FOCUS_CONSUMED = "sos_focus_consumed";

    private static final String KEY_SOS_UI_ROOM = "sos_ui_room";
    private static final String KEY_SOS_UI_SENDER_UID = "sos_ui_sender_uid";
    private static final String KEY_SOS_UI_ALERT_ID = "sos_ui_alert_id";
    private static final String KEY_SOS_UI_UNTIL_MS = "sos_ui_until_ms";
    private static final String KEY_BATTERY_OPT_PROMPTED = "battery_opt_prompted";
    private static final String KEY_MAP_TYPE_SATELLITE = "map_type_satellite";
    private static final String KEY_MAP_TRAFFIC_ENABLED = "map_traffic_enabled";
    private static final String KEY_LAST_INCIDENT_REPORT_ID = "last_incident_report_id";
    private static final String KEY_LAST_INCIDENT_REPORT_STATUS = "last_incident_report_status";
    private static final String KEY_ACTIVE_SOS_PROGRESS_ROOM = "active_sos_progress_room";
    private static final String KEY_ACTIVE_SOS_PROGRESS_ALERT = "active_sos_progress_alert";
    private static final String KEY_ACTIVE_SOS_PROGRESS_ACTIVE = "active_sos_progress_active";

    private UserPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getOrCreateUid(Context context) {
        SharedPreferences p = prefs(context);
        String uid = p.getString(KEY_UID, null);
        uid = uid == null ? "" : uid.trim().toUpperCase();
        if (!uid.matches("^ONE-[A-Z0-9]{4}$")) {
            uid = generateShortUid();
            p.edit().putString(KEY_UID, uid).apply();
        }
        return uid;
    }

    public static String getOrCreateDeviceId(Context context) {
        SharedPreferences p = prefs(context);
        String id = p.getString(KEY_DEVICE_ID, null);
        id = id == null ? "" : id.trim();
        if (id.length() < 8) {
            id = generateDeviceId();
            p.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static void setUid(Context context, String uid) {
        prefs(context).edit().putString(KEY_UID, uid == null ? "" : uid.trim()).apply();
    }

    public static String getUid(Context context) {
        return prefs(context).getString(KEY_UID, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    public static void setEmail(Context context, String email) {
        prefs(context).edit().putString(KEY_EMAIL, email == null ? "" : email.trim()).apply();
    }

    public static String getPublicId(Context context) {
        return prefs(context).getString(KEY_PUBLIC_ID, "");
    }

    public static void setPublicId(Context context, String publicId) {
        prefs(context).edit().putString(KEY_PUBLIC_ID, publicId == null ? "" : publicId.trim().toUpperCase()).apply();
    }

    public static String getPhotoUri(Context context) {
        return prefs(context).getString(KEY_PHOTO_URI, "");
    }

    public static void setPhotoUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_PHOTO_URI, uri == null ? "" : uri.trim()).apply();
    }

    public static String getPhotoUrl(Context context) {
        return prefs(context).getString(KEY_PHOTO_URL, "");
    }

    public static void setPhotoUrl(Context context, String url) {
        prefs(context).edit().putString(KEY_PHOTO_URL, url == null ? "" : url.trim()).apply();
    }

    public static String getPhotoB64(Context context) {
        return prefs(context).getString(KEY_PHOTO_B64, "");
    }

    public static void setPhotoB64(Context context, String b64) {
        prefs(context).edit().putString(KEY_PHOTO_B64, b64 == null ? "" : b64.trim()).apply();
    }

    public static void setUidFromPhone(Context context, String phone) {
        String uid = deriveUidFromPhone(phone);
        prefs(context).edit().putString(KEY_UID, uid).apply();
    }

    private static String deriveUidFromPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized.isEmpty()) return generateShortUid();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder("ONE-");

            for (int i = 0; i < 4; i++) {
                int v = hash[i] & 0xFF;
                sb.append(alphabet.charAt(v % alphabet.length()));
            }
            return sb.toString();
        } catch (Exception e) {
            return generateShortUid();
        }
    }

    private static String normalizePhone(String phone) {
        String p = String.valueOf(phone == null ? "" : phone).trim();
        if (p.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    private static String generateShortUid() {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder("ONE-");
        for (int i = 0; i < 4; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String generateDeviceId() {
        final String hex = "0123456789abcdef";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 16; i++) sb.append(hex.charAt(r.nextInt(hex.length())));
        return sb.toString();
    }

    public static String getName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static void setName(Context context, String name) {
        prefs(context).edit().putString(KEY_NAME, name == null ? "" : name.trim()).apply();
    }

    /** Ambil data FirstName. */
    public static String getFirstName(Context context) {
        return prefs(context).getString(KEY_FIRST_NAME, "");
    }

    public static void setFirstName(Context context, String firstName) {
        prefs(context).edit().putString(KEY_FIRST_NAME, firstName == null ? "" : firstName.trim()).apply();
    }

    /** Ambil data LastName. */
    public static String getLastName(Context context) {
        return prefs(context).getString(KEY_LAST_NAME, "");
    }

    public static void setLastName(Context context, String lastName) {
        prefs(context).edit().putString(KEY_LAST_NAME, lastName == null ? "" : lastName.trim()).apply();
    }

    public static String getLastAdminNotificationId(Context context) {
        return prefs(context).getString(KEY_LAST_ADMIN_NOTIFICATION_ID, "");
    }

    public static void setLastAdminNotificationId(Context context, String notificationId) {
        prefs(context).edit().putString(KEY_LAST_ADMIN_NOTIFICATION_ID, notificationId == null ? "" : notificationId.trim()).apply();
    }

    /** Padam atau bersihkan AccountData. */
    public static void clearAccountData(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .remove(KEY_UID)
                .remove(KEY_NAME)
                .remove(KEY_FIRST_NAME)
                .remove(KEY_LAST_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PUBLIC_ID)
                .remove(KEY_PHOTO_URI)
                .remove(KEY_PHOTO_URL)
                .remove(KEY_PHOTO_B64)
                .remove(KEY_PENDING_PHOTO_URI)
                .remove(KEY_BLOOD_TYPE)
                .remove(KEY_ALLERGIES)
                .remove(KEY_EXISTING_CONDITIONS)
                .remove(KEY_ADDRESS)
                .remove(KEY_RELIGION)
                .remove(KEY_PHONE)
                .remove(KEY_DOB)
                .remove(KEY_ETHNICITY)
                .remove(KEY_GENDER)
                .remove(KEY_WEIGHT)
                .remove(KEY_HEIGHT)
                .remove(KEY_MEDICATIONS)
                .remove(KEY_ORGAN_DONOR)
                .remove(KEY_HIDE_MEDICAL_INFO)
                .remove(KEY_EMERGENCY_CONTACTS_JSON)
                .remove(KEY_APP_LOCK_ENABLED)
                .remove(KEY_APP_LOCK_PIN)
                .remove(KEY_FINGERPRINT_ENABLED)
                .remove(KEY_BIOMETRIC_PIN)
                .remove(KEY_DURESS_SAFEGUARD_ENABLED)
                .remove(KEY_ACTIVE_ROOM_CODE)
                .remove(KEY_ROOM_MAP_VISIBLE)
                .remove(KEY_SOS_FOCUS_ROOM)
                .remove(KEY_SOS_FOCUS_SENDER_UID)
                .remove(KEY_SOS_FOCUS_ALERT_ID)
                .remove(KEY_SOS_FOCUS_CREATED_AT)
                .remove(KEY_SOS_FOCUS_CONSUMED)
                .remove(KEY_SOS_UI_ROOM)
                .remove(KEY_SOS_UI_SENDER_UID)
                .remove(KEY_SOS_UI_ALERT_ID)
                .remove(KEY_SOS_UI_UNTIL_MS)
                .remove(KEY_NOTIF_UNREAD)
                .remove(KEY_LAST_INCIDENT_REPORT_ID)
                .remove(KEY_LAST_INCIDENT_REPORT_STATUS)
                .remove(KEY_TOS_ACCEPTED)
                .apply();
    }

    public static String getPendingPhotoUri(Context context) {
        return prefs(context).getString(KEY_PENDING_PHOTO_URI, "");
    }

    public static void setPendingPhotoUri(Context context, String uri) {
        prefs(context).edit().putString(KEY_PENDING_PHOTO_URI, uri == null ? "" : uri.trim()).apply();
    }

    public static String getBloodType(Context context) {
        return prefs(context).getString(KEY_BLOOD_TYPE, "");
    }

    public static void setBloodType(Context context, String bloodType) {
        prefs(context).edit().putString(KEY_BLOOD_TYPE, bloodType == null ? "" : bloodType.trim()).apply();
    }

    public static String getAllergies(Context context) {
        return prefs(context).getString(KEY_ALLERGIES, "");
    }

    public static void setAllergies(Context context, String allergies) {
        prefs(context).edit().putString(KEY_ALLERGIES, allergies == null ? "" : allergies.trim()).apply();
    }

    public static String getExistingConditions(Context context) {
        return prefs(context).getString(KEY_EXISTING_CONDITIONS, "");
    }

    public static void setExistingConditions(Context context, String conditions) {
        prefs(context).edit().putString(KEY_EXISTING_CONDITIONS, conditions == null ? "" : conditions.trim()).apply();
    }

    /** Semak dan sahkan HideMedicalInfo. */
    public static boolean isHideMedicalInfo(Context context) {
        return prefs(context).getBoolean(KEY_HIDE_MEDICAL_INFO, false);
    }

    public static void setHideMedicalInfo(Context context, boolean hide) {
        prefs(context).edit().putBoolean(KEY_HIDE_MEDICAL_INFO, hide).apply();
    }

    public static String getVibrationStrength(Context context) {
        return prefs(context).getString(KEY_VIBRATION_STRENGTH, "medium");
    }

    public static void setVibrationStrength(Context context, String strength) {
        prefs(context).edit().putString(KEY_VIBRATION_STRENGTH, strength == null ? "medium" : strength).apply();
    }

    /** Semak dan sahkan TtsEnabled. */
    public static boolean isTtsEnabled(Context context) {
        return prefs(context).getBoolean(KEY_TTS_ENABLED, false);
    }

    public static void setTtsEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TTS_ENABLED, enabled).apply();
    }

    /** Semak dan sahkan FullscreenQuickMessage. */
    public static boolean isFullscreenQuickMessage(Context context) {
        return prefs(context).getBoolean(KEY_FULLSCREEN_QUICK_MESSAGE, false);
    }

    public static void setFullscreenQuickMessage(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FULLSCREEN_QUICK_MESSAGE, enabled).apply();
    }

    /** Semak dan sahkan AppLockEnabled. */
    public static boolean isAppLockEnabled(Context context) {
        return prefs(context).getBoolean(KEY_APP_LOCK_ENABLED, false);
    }

    public static void setAppLockEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply();
    }

    public static boolean hasAppLockPin(Context context) {
        String pin = prefs(context).getString(KEY_APP_LOCK_PIN, "");
        return pin != null && !pin.trim().isEmpty();
    }

    public static String getAppLockPin(Context context) {
        return prefs(context).getString(KEY_APP_LOCK_PIN, "");
    }

    private static final int PBKDF2_ITERATIONS = 12000;
    private static final int PBKDF2_KEY_LENGTH = 256;

    /** Helper hashing PIN menggunakan PBKDF2WithHmacSHA256 dan salt rawak unik. */
    public static String hashPin(Context context, String rawPin) {
        if (rawPin == null || rawPin.isEmpty()) return "";
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(rawPin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + bytesToHex(salt) + "$" + bytesToHex(hash);
        } catch (Exception e) {
            return legacySha256Pin(context, rawPin);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String legacySha256Pin(Context context, String rawPin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String salt = "ResQTap_Salt_" + context.getPackageName();
            byte[] hash = md.digest((salt + rawPin).getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            return rawPin;
        }
    }

    private static boolean verifyPinInternal(Context context, String stored, String enteredPin) {
        if (stored == null || stored.isEmpty() || enteredPin == null || enteredPin.isEmpty()) return false;
        if (stored.startsWith("pbkdf2$")) {
            String[] parts = stored.split("\\$");
            if (parts.length == 4) {
                try {
                    int iters = Integer.parseInt(parts[1]);
                    byte[] salt = hexToBytes(parts[2]);
                    byte[] expectedHash = hexToBytes(parts[3]);
                    javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(enteredPin.toCharArray(), salt, iters, expectedHash.length * 8);
                    javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                    byte[] actualHash = skf.generateSecret(spec).getEncoded();
                    return MessageDigest.isEqual(expectedHash, actualHash);
                } catch (Exception ignored) {}
            }
            return false;
        }
        // Legasi SHA-256 (64 aksara hex)
        if (stored.length() == 64) {
            return stored.equalsIgnoreCase(legacySha256Pin(context, enteredPin));
        }
        // Legasi teks biasa
        return stored.equals(enteredPin);
    }

    /** Pengesahan AppLockPin secara selamat (menyokong PBKDF2 dan migrasi automatik legasi). */
    public static boolean verifyAppLockPin(Context context, String enteredPin) {
        if (enteredPin == null) return false;
        String stored = getAppLockPin(context);
        if (stored.isEmpty()) return false;
        boolean valid = verifyPinInternal(context, stored, enteredPin);
        if (valid && !stored.startsWith("pbkdf2$")) {
            setAppLockPin(context, enteredPin);
        }
        return valid;
    }

    public static void setAppLockPin(Context context, String pin) {
        String toSave = (pin == null || pin.isEmpty()) ? "" : hashPin(context, pin);
        prefs(context).edit().putString(KEY_APP_LOCK_PIN, toSave).apply();
    }

    /** Semak dan sahkan FingerprintEnabled. */
    public static boolean isFingerprintEnabled(Context context) {
        return prefs(context).getBoolean(KEY_FINGERPRINT_ENABLED, false);
    }

    public static void setFingerprintEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply();
    }

    public static boolean hasBiometricPin(Context context) {
        String pin = prefs(context).getString(KEY_BIOMETRIC_PIN, "");
        return pin != null && !pin.trim().isEmpty();
    }

    public static String getBiometricPin(Context context) {
        return prefs(context).getString(KEY_BIOMETRIC_PIN, "");
    }

    /** Pengesahan BiometricPin secara selamat (menyokong PBKDF2 dan migrasi automatik legasi). */
    public static boolean verifyBiometricPin(Context context, String enteredPin) {
        if (enteredPin == null) return false;
        String stored = getBiometricPin(context);
        if (stored.isEmpty()) return false;
        boolean valid = verifyPinInternal(context, stored, enteredPin);
        if (valid && !stored.startsWith("pbkdf2$")) {
            setBiometricPin(context, enteredPin);
        }
        return valid;
    }

    public static void setBiometricPin(Context context, String pin) {
        String toSave = (pin == null || pin.isEmpty()) ? "" : hashPin(context, pin);
        prefs(context).edit().putString(KEY_BIOMETRIC_PIN, toSave).apply();
    }

    /** Semak dan sahkan DuressSafeguardEnabled. */
    public static boolean isDuressSafeguardEnabled(Context context) {
        return prefs(context).getBoolean(KEY_DURESS_SAFEGUARD_ENABLED, true);
    }

    public static void setDuressSafeguardEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_DURESS_SAFEGUARD_ENABLED, enabled).apply();
    }

    public static String getLanguage(Context context) {
        return prefs(context).getString(KEY_LANGUAGE, "en");
    }

    public static void setLanguage(Context context, String lang) {
        String normalized = String.valueOf(lang == null ? "" : lang).trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ms")) normalized = "ms";
        else if (normalized.startsWith("zh")) normalized = "zh";
        else if (normalized.startsWith("ta")) normalized = "ta";
        else if (normalized.startsWith("en")) normalized = "en";
        else normalized = "en";
        prefs(context).edit().putString(KEY_LANGUAGE, normalized).apply();
    }

    public static String getAddress(Context context) {
        return prefs(context).getString(KEY_ADDRESS, "");
    }

    public static void setAddress(Context context, String address) {
        prefs(context).edit().putString(KEY_ADDRESS, address == null ? "" : address).apply();
    }

    public static String getReligion(Context context) {
        return prefs(context).getString(KEY_RELIGION, "");
    }

    public static void setReligion(Context context, String religion) {
        prefs(context).edit().putString(KEY_RELIGION, religion == null ? "" : religion).apply();
    }

    public static String getPhoneNumber(Context context) {
        return prefs(context).getString(KEY_PHONE, "");
    }

    public static void setPhoneNumber(Context context, String phone) {
        prefs(context).edit().putString(KEY_PHONE, phone == null ? "" : phone).apply();
    }

    public static String getDateOfBirth(Context context) {
        return prefs(context).getString(KEY_DOB, "");
    }

    public static void setDateOfBirth(Context context, String dob) {
        prefs(context).edit().putString(KEY_DOB, dob == null ? "" : dob).apply();
    }

    public static String getEthnicity(Context context) {
        return prefs(context).getString(KEY_ETHNICITY, "");
    }

    public static void setEthnicity(Context context, String ethnicity) {
        prefs(context).edit().putString(KEY_ETHNICITY, ethnicity == null ? "" : ethnicity).apply();
    }

    public static String getIcNumber(Context context) {
        return prefs(context).getString(KEY_IC_NUMBER, "");
    }

    public static void setIcNumber(Context context, String ic) {
        prefs(context).edit().putString(KEY_IC_NUMBER, ic == null ? "" : ic).apply();
    }

    public static String getGender(Context context) {
        return prefs(context).getString(KEY_GENDER, "");
    }

    public static void setGender(Context context, String gender) {
        prefs(context).edit().putString(KEY_GENDER, gender == null ? "" : gender).apply();
    }

    public static String getWeight(Context context) {
        return prefs(context).getString(KEY_WEIGHT, "");
    }

    public static void setWeight(Context context, String weight) {
        prefs(context).edit().putString(KEY_WEIGHT, weight == null ? "" : weight.trim()).apply();
    }

    public static String getHeight(Context context) {
        return prefs(context).getString(KEY_HEIGHT, "");
    }

    public static void setHeight(Context context, String height) {
        prefs(context).edit().putString(KEY_HEIGHT, height == null ? "" : height.trim()).apply();
    }

    public static String getMedications(Context context) {
        return prefs(context).getString(KEY_MEDICATIONS, "");
    }

    public static void setMedications(Context context, String medications) {
        prefs(context).edit().putString(KEY_MEDICATIONS, medications == null ? "" : medications).apply();
    }

    public static String getOrganDonor(Context context) {
        return prefs(context).getString(KEY_ORGAN_DONOR, "");
    }

    public static void setOrganDonor(Context context, String donor) {
        prefs(context).edit().putString(KEY_ORGAN_DONOR, donor == null ? "" : donor).apply();
    }

    /** Semak dan sahkan PersonalInfoComplete. */
    public static boolean isPersonalInfoComplete(Context context) {
        if (isResqTapEmail(getEmail(context))) {
            return true;
        }
        // Jika pengguna sudah ada Public ID dan Nama (sudah pernah selesai pendaftaran), anggap profil lengkap
        if (!getPublicId(context).trim().isEmpty() && !getName(context).trim().isEmpty()) {
            return true;
        }
        return !getAddress(context).trim().isEmpty()
                && !getReligion(context).trim().isEmpty()
                && !getEthnicity(context).trim().isEmpty();
    }

    /** Muat dan simpan data DataSnapshot profil pengguna ke UserPrefs. */
    public static void applyUserSnapshot(Context context, com.google.firebase.database.DataSnapshot snapshot) {
        if (context == null || snapshot == null || !snapshot.exists()) return;
        Object nameObj = snapshot.child("name").getValue();
        if (nameObj != null) setName(context, String.valueOf(nameObj));
        Object bloodObj = snapshot.child("bloodType").getValue();
        if (bloodObj != null) setBloodType(context, String.valueOf(bloodObj));
        Object allergiesObj = snapshot.child("allergies").getValue();
        if (allergiesObj != null) setAllergies(context, String.valueOf(allergiesObj));
        Object weightObj = snapshot.child("weight").getValue();
        setWeight(context, weightObj == null ? "" : String.valueOf(weightObj));
        Object heightObj = snapshot.child("height").getValue();
        setHeight(context, heightObj == null ? "" : String.valueOf(heightObj));
        Object genderObj = snapshot.child("gender").getValue();
        if (genderObj != null) setGender(context, String.valueOf(genderObj));
        Object icObj = snapshot.child("icNumber").getValue();
        if (icObj != null) setIcNumber(context, String.valueOf(icObj));

        Object medObj = snapshot.child("medications").getValue();
        String medications = medObj == null ? "" : String.valueOf(medObj).trim();
        Object condObj = snapshot.child("existingConditions").getValue();
        String conditions = condObj == null ? "" : String.valueOf(condObj).trim();
        if (conditions.isEmpty() && !medications.isEmpty()) {
            conditions = medications;
        }
        setExistingConditions(context, conditions);
        setMedications(context, medications);

        Object organObj = snapshot.child("organDonor").getValue();
        if (organObj != null) setOrganDonor(context, String.valueOf(organObj));
        Object emailObj = snapshot.child("email").getValue();
        if (emailObj != null) setEmail(context, String.valueOf(emailObj));
        Object publicIdObj = snapshot.child("publicId").getValue();
        String pubId = publicIdObj == null ? "" : String.valueOf(publicIdObj).trim();
        if (pubId.isEmpty()) {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), "");
        } else {
            pubId = com.example.resqtap.friend.FirebaseFriendClient.format4DigitId(snapshot.getKey(), pubId);
        }
        setPublicId(context, pubId);

        Object photoUrlObj = snapshot.child("photoUrl").getValue();
        String photoUrl = photoUrlObj == null ? "" : String.valueOf(photoUrlObj);
        if (photoUrl == null) photoUrl = "";
        photoUrl = photoUrl.trim();
        if (photoUrl.isEmpty()) {
            Object photoUriObj = snapshot.child("photoUri").getValue();
            photoUrl = photoUriObj == null ? "" : String.valueOf(photoUriObj);
            if (photoUrl == null) photoUrl = "";
            photoUrl = photoUrl.trim();
        }
        if (!photoUrl.isEmpty()) setPhotoUrl(context, photoUrl);

        Object photoB64Obj = snapshot.child("photoB64").getValue();
        if (photoB64Obj != null) {
            String b64 = String.valueOf(photoB64Obj);
            if (b64 != null && !b64.trim().isEmpty()) setPhotoB64(context, b64.trim());
        }

        Object addrObj = snapshot.child("address").getValue();
        if (addrObj != null) setAddress(context, String.valueOf(addrObj));
        Object relObj = snapshot.child("religion").getValue();
        if (relObj != null) setReligion(context, String.valueOf(relObj));
        Object phoneObj = snapshot.child("phoneNumber").getValue();
        if (phoneObj != null) setPhoneNumber(context, String.valueOf(phoneObj));
        Object dobObj = snapshot.child("dateOfBirth").getValue();
        if (dobObj != null) setDateOfBirth(context, String.valueOf(dobObj));
        Object ethObj = snapshot.child("ethnicity").getValue();
        if (ethObj != null) setEthnicity(context, String.valueOf(ethObj));

        try {
            com.google.firebase.database.DataSnapshot contactsSnap = snapshot.child("emergencyContacts");
            if (contactsSnap != null && contactsSnap.exists()) {
                java.util.ArrayList<EmergencyContact> contacts = new java.util.ArrayList<>();
                for (com.google.firebase.database.DataSnapshot child : contactsSnap.getChildren()) {
                    if (child == null) continue;
                    String id = child.child("id").getValue() == null ? "" : String.valueOf(child.child("id").getValue());
                    if (id.trim().isEmpty()) id = child.getKey() == null ? "" : child.getKey();
                    String cName = child.child("name").getValue() == null ? "" : String.valueOf(child.child("name").getValue());
                    String rel = child.child("relationship").getValue() == null ? "" : String.valueOf(child.child("relationship").getValue());
                    String phone = child.child("phone").getValue() == null ? "" : String.valueOf(child.child("phone").getValue());
                    if (id == null) id = "";
                    if (cName == null) cName = "";
                    if (rel == null) rel = "";
                    if (phone == null) phone = "";
                    if (id.trim().isEmpty()) continue;
                    contacts.add(new EmergencyContact(id.trim(), cName, rel, phone));
                }
                setEmergencyContacts(context, contacts);
            }
        } catch (Exception ignored) {
        }
    }

    /** Semak dan sahkan ResqTapEmail. */
    private static boolean isResqTapEmail(String email) {
        if (email == null) return false;
        String value = email.trim().toLowerCase(java.util.Locale.ROOT);
        return value.endsWith("@resqtap.com");
    }

    public static java.util.ArrayList<EmergencyContact> getEmergencyContacts(Context context) {
        String raw = prefs(context).getString(KEY_EMERGENCY_CONTACTS_JSON, "");
        java.util.ArrayList<EmergencyContact> out = new java.util.ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        boolean dirty = false;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "").trim();
                String name = o.optString("name", "").trim();
                String rel = o.optString("relationship", "").trim();
                String phone = o.optString("phone", "").trim();
                String notes = o.optString("notes", "").trim();
                if (name.isEmpty() && phone.isEmpty()) continue;
                if (id.isEmpty()) {
                    id = java.util.UUID.randomUUID().toString();
                    dirty = true;
                }
                out.add(new EmergencyContact(id, name, rel, phone, notes));
            }
        } catch (Exception ignored) {
        }
        if (dirty) saveEmergencyContacts(context, out);
        return out;
    }

    public static void addEmergencyContact(Context context, EmergencyContact c) {
        if (c == null) return;
        String id = c.id == null ? "" : c.id.trim();
        String name = c.name == null ? "" : c.name.trim();
        String rel = c.relationship == null ? "" : c.relationship.trim();
        String phone = c.phone == null ? "" : c.phone.trim();
        String notes = c.notes == null ? "" : c.notes.trim();
        if (name.isEmpty() || phone.isEmpty()) return;

        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        if (hasEmergencyContactDuplicate(list, "", name, phone)) return;
        String finalId = id.isEmpty() ? java.util.UUID.randomUUID().toString() : id;
        list.add(new EmergencyContact(finalId, name, rel, phone, notes));
        saveEmergencyContacts(context, list);
    }

    public static void setEmergencyContacts(Context context, java.util.ArrayList<EmergencyContact> contacts) {
        java.util.ArrayList<EmergencyContact> list = new java.util.ArrayList<>();
        if (contacts != null) list.addAll(contacts);
        saveEmergencyContacts(context, list);
    }

    /** Simpan atau hantar data EmergencyContact. */
    public static void updateEmergencyContact(Context context, String id, String name, String relationship, String phone) {
        updateEmergencyContact(context, id, name, relationship, phone, "");
    }

    public static void updateEmergencyContact(Context context, String id, String name, String relationship, String phone, String notes) {
        String safeId = String.valueOf(id == null ? "" : id).trim();
        if (safeId.isEmpty()) return;
        String n = String.valueOf(name == null ? "" : name).trim();
        String r = String.valueOf(relationship == null ? "" : relationship).trim();
        String p = String.valueOf(phone == null ? "" : phone).trim();
        String not = String.valueOf(notes == null ? "" : notes).trim();
        if (n.isEmpty() || p.isEmpty()) return;

        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        if (hasEmergencyContactDuplicate(list, safeId, n, p)) return;
        for (int i = 0; i < list.size(); i++) {
            EmergencyContact c = list.get(i);
            if (c == null) continue;
            if (safeId.equals(c.id)) {
                list.set(i, new EmergencyContact(safeId, n, r, p, not));
                saveEmergencyContacts(context, list);
                return;
            }
        }
    }

    /** Semak dan sahkan EmergencyContactDuplicate. */
    public static boolean hasEmergencyContactDuplicate(Context context, String excludeId, String name, String phone) {
        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        return hasEmergencyContactDuplicate(list, excludeId, name, phone);
    }

    /** Semak dan sahkan EmergencyContactPhoneDuplicate. */
    public static boolean hasEmergencyContactPhoneDuplicate(Context context, String excludeId, String phone) {
        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        if (list == null) return false;
        String ex = String.valueOf(excludeId == null ? "" : excludeId).trim();
        String p = normalizePhoneNumber(phone);
        if (p.isEmpty()) return false;
        for (EmergencyContact c : list) {
            if (c == null) continue;
            if (!ex.isEmpty() && ex.equals(String.valueOf(c.id == null ? "" : c.id).trim())) continue;
            String cp = normalizePhoneNumber(c.phone);
            if (!cp.isEmpty() && cp.equals(p)) return true;
        }
        return false;
    }

    /** Semak dan sahkan EmergencyContactNameDuplicate. */
    public static boolean hasEmergencyContactNameDuplicate(Context context, String excludeId, String name) {
        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        if (list == null) return false;
        String ex = String.valueOf(excludeId == null ? "" : excludeId).trim();
        String n = normalizeName(name);
        if (n.isEmpty()) return false;
        for (EmergencyContact c : list) {
            if (c == null) continue;
            if (!ex.isEmpty() && ex.equals(String.valueOf(c.id == null ? "" : c.id).trim())) continue;
            String cn = normalizeName(c.name);
            if (!cn.isEmpty() && cn.equals(n)) return true;
        }
        return false;
    }

    /** Semak dan sahkan EmergencyContactDuplicate. */
    private static boolean hasEmergencyContactDuplicate(java.util.ArrayList<EmergencyContact> list, String excludeId, String name, String phone) {
        if (list == null) return false;
        String ex = String.valueOf(excludeId == null ? "" : excludeId).trim();
        String n = normalizeName(name);
        String p = normalizePhoneNumber(phone);
        if (n.isEmpty() || p.isEmpty()) return false;
        for (EmergencyContact c : list) {
            if (c == null) continue;
            if (!ex.isEmpty() && ex.equals(String.valueOf(c.id == null ? "" : c.id).trim())) continue;
            String cn = normalizeName(c.name);
            String cp = normalizePhoneNumber(c.phone);
            if (!cn.isEmpty() && !cp.isEmpty() && cn.equals(n) && cp.equals(p)) return true;
        }
        return false;
    }

    private static String normalizeName(String name) {
        return String.valueOf(name == null ? "" : name).trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String normalizePhoneNumber(String phone) {
        String p = String.valueOf(phone == null ? "" : phone).trim();
        if (p.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /** Padam atau bersihkan EmergencyContactById. */
    public static void deleteEmergencyContactById(Context context, String id) {
        String safeId = String.valueOf(id == null ? "" : id).trim();
        if (safeId.isEmpty()) return;
        java.util.ArrayList<EmergencyContact> list = getEmergencyContacts(context);
        for (int i = 0; i < list.size(); i++) {
            EmergencyContact c = list.get(i);
            if (c == null) continue;
            if (safeId.equals(c.id)) {
                list.remove(i);
                break;
            }
        }
        saveEmergencyContacts(context, list);
    }

    /** Simpan atau hantar data EmergencyContacts. */
    private static void saveEmergencyContacts(Context context, java.util.ArrayList<EmergencyContact> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        if (list != null) {
            for (EmergencyContact c : list) {
                if (c == null) continue;
                String id = c.id == null ? "" : c.id.trim();
                String name = c.name == null ? "" : c.name.trim();
                String rel = c.relationship == null ? "" : c.relationship.trim();
                String phone = c.phone == null ? "" : c.phone.trim();
                String notes = c.notes == null ? "" : c.notes.trim();
                if (name.isEmpty() || phone.isEmpty()) continue;
                org.json.JSONObject o = new org.json.JSONObject();
                try {
                    o.put("id", id.isEmpty() ? java.util.UUID.randomUUID().toString() : id);
                    o.put("name", name);
                    o.put("relationship", rel);
                    o.put("phone", phone);
                    o.put("notes", notes);
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        }
        prefs(context).edit().putString(KEY_EMERGENCY_CONTACTS_JSON, arr.toString()).apply();
    }

    /** Semak dan sahkan LowBatteryAlert. */
    public static boolean isLowBatteryAlert(Context context) {
        return prefs(context).getBoolean(KEY_LOW_BATTERY_ALERT, false);
    }

    public static void setLowBatteryAlert(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LOW_BATTERY_ALERT, enabled).apply();
    }

    /** Semak dan sahkan BatterySaverMode. */
    public static boolean isBatterySaverMode(Context context) {
        return prefs(context).getBoolean(KEY_BATTERY_SAVER_MODE, false);
    }

    public static void setBatterySaverMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_BATTERY_SAVER_MODE, enabled).apply();
    }

    public static int getUnreadNotifications(Context context) {
        return prefs(context).getInt(KEY_NOTIF_UNREAD, 0);
    }

    public static void setUnreadNotifications(Context context, int count) {
        int safe = Math.max(0, count);
        prefs(context).edit().putInt(KEY_NOTIF_UNREAD, safe).apply();
    }

    public static void incrementUnreadNotifications(Context context) {
        int next = getUnreadNotifications(context) + 1;
        setUnreadNotifications(context, next);
    }

    public static String getFcmToken(Context context) {
        return prefs(context).getString(KEY_FCM_TOKEN, "");
    }

    public static void setFcmToken(Context context, String token) {
        prefs(context).edit().putString(KEY_FCM_TOKEN, token == null ? "" : token.trim()).apply();
    }

    public static String getActiveRoomCode(Context context) {
        return prefs(context).getString(KEY_ACTIVE_ROOM_CODE, "");
    }

    public static void setActiveRoomCode(Context context, String roomCode) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        prefs(context).edit().putString(KEY_ACTIVE_ROOM_CODE, code).apply();
    }

    public static long getSosLastSeenAt(Context context, String roomCode) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return 0L;
        return prefs(context).getLong(KEY_SOS_LAST_SEEN_AT_PREFIX + code, 0L);
    }

    public static void setSosLastSeenAt(Context context, String roomCode, long createdAtMs) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return;
        prefs(context).edit().putLong(KEY_SOS_LAST_SEEN_AT_PREFIX + code, Math.max(0L, createdAtMs)).apply();
    }

    public static String getSosLastHandledId(Context context, String roomCode) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return "";
        return prefs(context).getString(KEY_SOS_LAST_HANDLED_ID_PREFIX + code, "");
    }

    public static void setSosLastHandledId(Context context, String roomCode, String alertId) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return;
        String id = String.valueOf(alertId == null ? "" : alertId).trim();
        prefs(context).edit().putString(KEY_SOS_LAST_HANDLED_ID_PREFIX + code, id).apply();
    }

    public static long getSosListenerBaselineAt(Context context, String roomCode) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return 0L;
        return prefs(context).getLong(KEY_SOS_LISTENER_BASELINE_PREFIX + code, 0L);
    }

    public static void setSosListenerBaselineAt(Context context, String roomCode, long baselineMs) {
        String code = String.valueOf(roomCode == null ? "" : roomCode).trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) return;
        prefs(context).edit().putLong(KEY_SOS_LISTENER_BASELINE_PREFIX + code, Math.max(0L, baselineMs)).apply();
    }

    /** Semak dan sahkan RoomMapVisible. */
    public static boolean isRoomMapVisible(Context context) {
        return prefs(context).getBoolean(KEY_ROOM_MAP_VISIBLE, false);
    }

    public static void setRoomMapVisible(Context context, boolean visible) {
        prefs(context).edit().putBoolean(KEY_ROOM_MAP_VISIBLE, visible).apply();
    }

    /** Semak dan sahkan BatteryOptPrompted. */
    public static boolean isBatteryOptPrompted(Context context) {
        return prefs(context).getBoolean(KEY_BATTERY_OPT_PROMPTED, false);
    }

    public static void setBatteryOptPrompted(Context context, boolean prompted) {
        prefs(context).edit().putBoolean(KEY_BATTERY_OPT_PROMPTED, prompted).apply();
    }

    public static void setPendingSosFocus(Context context, String roomId, String senderUid, String alertId, long createdAtMs) {
        if (context == null) return;
        String room = String.valueOf(roomId == null ? "" : roomId).trim().toUpperCase(java.util.Locale.ROOT);
        String sender = String.valueOf(senderUid == null ? "" : senderUid).trim();
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        long at = Math.max(0L, createdAtMs);
        prefs(context).edit()
                .putString(KEY_SOS_FOCUS_ROOM, room)
                .putString(KEY_SOS_FOCUS_SENDER_UID, sender)
                .putString(KEY_SOS_FOCUS_ALERT_ID, aId)
                .putLong(KEY_SOS_FOCUS_CREATED_AT, at)
                .putBoolean(KEY_SOS_FOCUS_CONSUMED, false)
                .apply();
    }

    public static String getPendingSosFocusRoom(Context context) {
        return prefs(context).getString(KEY_SOS_FOCUS_ROOM, "");
    }

    public static String getPendingSosFocusSenderUid(Context context) {
        return prefs(context).getString(KEY_SOS_FOCUS_SENDER_UID, "");
    }

    public static String getPendingSosFocusAlertId(Context context) {
        return prefs(context).getString(KEY_SOS_FOCUS_ALERT_ID, "");
    }

    public static long getPendingSosFocusCreatedAt(Context context) {
        return prefs(context).getLong(KEY_SOS_FOCUS_CREATED_AT, 0L);
    }

    /** Semak dan sahkan PendingSosFocusConsumed. */
    public static boolean isPendingSosFocusConsumed(Context context) {
        return prefs(context).getBoolean(KEY_SOS_FOCUS_CONSUMED, true);
    }

    public static void markPendingSosFocusConsumed(Context context) {
        prefs(context).edit().putBoolean(KEY_SOS_FOCUS_CONSUMED, true).apply();
    }

    public static void setSosUiBlink(Context context, String roomId, String senderUid, String alertId, long untilMs) {
        if (context == null) return;
        String room = String.valueOf(roomId == null ? "" : roomId).trim().toUpperCase(java.util.Locale.ROOT);
        String sender = String.valueOf(senderUid == null ? "" : senderUid).trim();
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        long until = Math.max(0L, untilMs);
        prefs(context).edit()
                .putString(KEY_SOS_UI_ROOM, room)
                .putString(KEY_SOS_UI_SENDER_UID, sender)
                .putString(KEY_SOS_UI_ALERT_ID, aId)
                .putLong(KEY_SOS_UI_UNTIL_MS, until)
                .apply();
    }

    public static String getSosUiRoom(Context context) {
        return prefs(context).getString(KEY_SOS_UI_ROOM, "");
    }

    public static String getSosUiSenderUid(Context context) {
        return prefs(context).getString(KEY_SOS_UI_SENDER_UID, "");
    }

    public static String getSosUiAlertId(Context context) {
        return prefs(context).getString(KEY_SOS_UI_ALERT_ID, "");
    }

    public static long getSosUiUntilMs(Context context) {
        return prefs(context).getLong(KEY_SOS_UI_UNTIL_MS, 0L);
    }

    /** Padam atau bersihkan SosUiBlink. */
    public static void clearSosUiBlink(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .remove(KEY_SOS_UI_ROOM)
                .remove(KEY_SOS_UI_SENDER_UID)
                .remove(KEY_SOS_UI_ALERT_ID)
                .remove(KEY_SOS_UI_UNTIL_MS)
                .apply();
    }

    /** Padam atau bersihkan SosUiBlinkIfAlert. */
    public static void clearSosUiBlinkIfAlert(Context context, String roomId, String alertId) {
        if (context == null) return;
        String room = String.valueOf(roomId == null ? "" : roomId).trim().toUpperCase(java.util.Locale.ROOT);
        String aId = String.valueOf(alertId == null ? "" : alertId).trim();
        if (room.isEmpty() || aId.isEmpty()) return;
        String storedRoom = String.valueOf(getSosUiRoom(context) == null ? "" : getSosUiRoom(context)).trim().toUpperCase(java.util.Locale.ROOT);
        String storedAlert = String.valueOf(getSosUiAlertId(context) == null ? "" : getSosUiAlertId(context)).trim();
        if (room.equals(storedRoom) && aId.equals(storedAlert)) clearSosUiBlink(context);
    }

    public static int getNightMode(Context context) {
        if (!prefs(context).getBoolean("theme_v2_light_default_applied", false)) {
            prefs(context).edit()
                    .putBoolean("theme_v2_light_default_applied", true)
                    .putInt(KEY_NIGHT_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                    .apply();
            return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        }
        int mode = prefs(context).getInt(KEY_NIGHT_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        if (mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            return androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        }
        return mode;
    }

    public static void setNightMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply();
    }

    /** Semak dan sahkan MapTypeSatellite. */
    public static boolean isMapTypeSatellite(Context context) {
        return prefs(context).getBoolean(KEY_MAP_TYPE_SATELLITE, false);
    }

    public static void setMapTypeSatellite(Context context, boolean satellite) {
        prefs(context).edit().putBoolean(KEY_MAP_TYPE_SATELLITE, satellite).apply();
    }

    /** Semak dan sahkan MapTrafficEnabled. */
    public static boolean isMapTrafficEnabled(Context context) {
        return prefs(context).getBoolean(KEY_MAP_TRAFFIC_ENABLED, false);
    }

    public static void setMapTrafficEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_MAP_TRAFFIC_ENABLED, enabled).apply();
    }

    /** Semak sama ada pengguna telah menerima Terms of Service. */
    public static boolean isTosAccepted(Context context) {
        return prefs(context).getBoolean(KEY_TOS_ACCEPTED, false);
    }

    /** Simpan status penerimaan Terms of Service. */
    public static void setTosAccepted(Context context, boolean accepted) {
        prefs(context).edit().putBoolean(KEY_TOS_ACCEPTED, accepted).apply();
    }

    /** Simpan ID laporan insiden terkini. */
    public static void setLastIncidentReportId(Context context, String reportId) {
        prefs(context).edit().putString(KEY_LAST_INCIDENT_REPORT_ID, reportId).apply();
    }

    /** Dapatkan ID laporan insiden terkini. */
    public static String getLastIncidentReportId(Context context) {
        return prefs(context).getString(KEY_LAST_INCIDENT_REPORT_ID, null);
    }

    /** Padam rekod ID laporan insiden terkini. */
    public static void clearLastIncidentReportId(Context context) {
        prefs(context).edit().remove(KEY_LAST_INCIDENT_REPORT_ID).remove(KEY_LAST_INCIDENT_REPORT_STATUS).apply();
    }

    /** Simpan status laporan insiden terkini. */
    public static void setLastIncidentReportStatus(Context context, String status) {
        prefs(context).edit().putString(KEY_LAST_INCIDENT_REPORT_STATUS, status).apply();
    }

    /** Dapatkan status laporan insiden terkini. */
    public static String getLastIncidentReportStatus(Context context) {
        return prefs(context).getString(KEY_LAST_INCIDENT_REPORT_STATUS, null);
    }

    /** Simpan sesi SOS Progress aktif apabila kes di-reserve oleh admin. */
    public static void setActiveSosProgress(Context context, String roomCode, String alertId) {
        if (context == null) return;
        prefs(context).edit()
                .putString(KEY_ACTIVE_SOS_PROGRESS_ROOM, roomCode == null ? "" : roomCode.trim())
                .putString(KEY_ACTIVE_SOS_PROGRESS_ALERT, alertId == null ? "" : alertId.trim())
                .putBoolean(KEY_ACTIVE_SOS_PROGRESS_ACTIVE, true)
                .apply();
    }

    /** Padam sesi SOS Progress aktif apabila kes dibatalkan atau selesai. */
    public static void clearActiveSosProgress(Context context) {
        if (context == null) return;
        prefs(context).edit()
                .putString(KEY_ACTIVE_SOS_PROGRESS_ROOM, "")
                .putString(KEY_ACTIVE_SOS_PROGRESS_ALERT, "")
                .putBoolean(KEY_ACTIVE_SOS_PROGRESS_ACTIVE, false)
                .apply();
    }

    /** Semak sama ada pengguna mempunyai sesi SOS Progress aktif yang belum resolved. */
    public static boolean isSosProgressActive(Context context) {
        if (context == null) return false;
        SharedPreferences p = prefs(context);
        boolean active = p.getBoolean(KEY_ACTIVE_SOS_PROGRESS_ACTIVE, false);
        String alertId = p.getString(KEY_ACTIVE_SOS_PROGRESS_ALERT, "");
        return active && !alertId.isEmpty();
    }

    /** Dapatkan kod bilik untuk sesi SOS Progress aktif. */
    public static String getActiveSosProgressRoom(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_ACTIVE_SOS_PROGRESS_ROOM, "");
    }

    /** Dapatkan ID amaran untuk sesi SOS Progress aktif. */
    public static String getActiveSosProgressAlert(Context context) {
        if (context == null) return "";
        return prefs(context).getString(KEY_ACTIVE_SOS_PROGRESS_ALERT, "");
    }
}

