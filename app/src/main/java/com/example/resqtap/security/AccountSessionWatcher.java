package com.example.resqtap.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.resqtap.R;
import com.example.resqtap.app.SplashActivity;
import com.example.resqtap.auth.ForgotPasswordActivity;
import com.example.resqtap.auth.GetStartedActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.auth.RegisterActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.sos.SosServiceStarter;
import com.example.resqtap.utils.UserPrefs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AccountSessionWatcher
 * Memantau status akaun pengguna dalam masa nyata (real-time).
 * Sekiranya akaun pengguna dipadam oleh pentadbir atau pangkalan data dibersihkan ("Clear DB")
 * di laman web admin semasa pengguna sedang menggunakan aplikasi di peranti, pengurus ini akan:
 * 1. Log keluar Firebase Auth secara serta-merta.
 * 2. Membersihkan cache sesi tempatan (UserPrefs).
 * 3. Menghentikan servis latar belakang (SOS).
 * 4. Menutup aktiviti semasa dan mengembalikan pengguna terus ke LoginActivity.
 */
public class AccountSessionWatcher implements Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AccountSessionWatcher";
    private static AccountSessionWatcher instance;

    private final Application application;
    private WeakReference<Activity> currentActivityRef = new WeakReference<>(null);

    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private String currentObservedUid = null;

    private final AtomicBoolean isKicking = new AtomicBoolean(false);

    private AccountSessionWatcher(Application app) {
        this.application = app;
    }

    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new AccountSessionWatcher(app);
            app.registerActivityLifecycleCallbacks(instance);
            instance.setupAuthListener();
        }
    }

    private void setupAuthListener() {
        FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                isKicking.set(false);
                startWatchingUser(user.getUid());
            } else {
                stopWatchingUser();
            }
        });
    }

    private synchronized void startWatchingUser(String uid) {
        if (uid == null || uid.isEmpty()) return;
        if (uid.equals(currentObservedUid) && userListener != null) {
            return;
        }

        stopWatchingUser();
        currentObservedUid = uid;

        userRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("users")
                .child(uid);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    return;
                }

                // snapshot.exists() == false!
                // Semak sama ada pengguna sedang di skrin pendaftaran / auth permulaan
                Activity currentActivity = getCurrentActivity();
                if (isAuthScreen(currentActivity)) {
                    // Jangan kacau pengguna jika sedang berada dalam skrin proses pendaftaran
                    return;
                }

                // Elakkan salah faham semasa app baru dibina semula / cold start / reconnect:
                // Jangan tendang pengguna keluar secara terburu-buru melainkan akaun Firebase Auth
                // benar-benar tidak sah (invalid user / dipadam daripada Authentication oleh admin).
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) {
                    return;
                }

                user.reload().addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        Log.w(TAG, "Akaun " + uid + " telah dipadam di Firebase Auth. Mengembalikan pengguna ke LoginActivity...");
                        handleAccountPurged(currentActivity);
                    }
                }).addOnSuccessListener(aVoid -> {
                    // Jika Auth masih sah tetapi node users/{uid} kosong (contohnya baru login atau reset profil),
                    // periksa sama ada pengguna masih mempunyai sesi tempatan sebelum menendang.
                    // Jika profil tempatan masih wujud, kembalikan ke pangkalan data atau abaikan pengusiran palsu.
                    Log.i(TAG, "Firebase Auth pengguna masih sah. Mengabaikan penyingkiran palsu.");
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "AccountSessionWatcher error: " + error.getMessage());
            }
        };

        userRef.addValueEventListener(userListener);
    }

    private synchronized void stopWatchingUser() {
        if (userRef != null && userListener != null) {
            try {
                userRef.removeEventListener(userListener);
            } catch (Exception ignored) {}
        }
        userRef = null;
        userListener = null;
        currentObservedUid = null;
    }

    /**
     * Kendalikan pengusiran pengguna ke halaman LoginActivity serta-merta.
     */
    public void handleAccountPurged(@Nullable Activity activity) {
        Activity current = (activity != null) ? activity : getCurrentActivity();
        if (isAuthScreen(current)) {
            // Jika pengguna sudah berada di skrin login/register/auth, abaikan terus.
            return;
        }

        if (isKicking.getAndSet(true)) {
            return; // Elakkan panggilan bertindih
        }

        stopWatchingUser();

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Activity act = getCurrentActivity();
                if (isAuthScreen(act)) {
                    isKicking.set(false);
                    return;
                }

                Context context = (act != null && !act.isFinishing()) ? act : application;

                // 1. Hentikan sebarang background service
                try {
                    SosServiceStarter.stop(context);
                } catch (Exception ignored) {}

                // 2. Sign out dari Firebase Auth
                try {
                    FirebaseAuth.getInstance().signOut();
                } catch (Exception ignored) {}

                // 3. Padam data akaun tempatan
                UserPrefs.clearAccountData(context);

                // 4. Paparkan notis hanya jika pengguna ditendang dari skrin utama (bukan skrin log masuk)
                try {
                    Toast.makeText(application, R.string.account_deleted_notice, Toast.LENGTH_LONG).show();
                } catch (Exception ignored) {}

                // 5. Buka LoginActivity dan kosongkan backstack aktiviti
                Intent loginIntent = new Intent(context, LoginActivity.class);
                loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(loginIntent);

                // 6. Tutup aktiviti semasa jika masih hidup
                if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                    act.finish();
                }
            } catch (Exception e) {
                Log.e(TAG, "Ralat semasa mengembalikan pengguna ke Login: ", e);
            }
        });
    }

    private boolean isAuthScreen(Activity activity) {
        if (activity == null) return true;
        String name = activity.getClass().getName();
        return activity instanceof LoginActivity ||
                activity instanceof RegisterActivity ||
                activity instanceof SplashActivity ||
                activity instanceof GetStartedActivity ||
                activity instanceof ForgotPasswordActivity ||
                name.contains("google") ||
                name.contains("SignInHubActivity") ||
                name.contains("auth") ||
                name.contains("Credential");
    }

    @Nullable
    public Activity getCurrentActivity() {
        return currentActivityRef.get();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);

        // Jika aktiviti bukan auth screen, pastikan pengguna masih sah
        if (!isAuthScreen(activity)) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null) {
                // Jangan panggil handleAccountPurged terus jika tiada pengguna log masuk,
                // elakkan toast sesi tamat palsu semasa pengguna belum log masuk
                return;
            }

            // Sahkan dengan Firebase Auth (jika token dibatalkan atau user dipadam di Auth)
            currentUser.reload().addOnFailureListener(e -> {
                if (e instanceof FirebaseAuthInvalidUserException) {
                    Log.w(TAG, "currentUser.reload() melaporkan FirebaseAuthInvalidUserException! Akaun telah dipadam.");
                    handleAccountPurged(activity);
                }
            });
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivityRef.get() == activity) {
            currentActivityRef.clear();
        }
    }
}
