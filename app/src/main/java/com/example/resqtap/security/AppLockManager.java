package com.example.resqtap.security;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.example.resqtap.app.SplashActivity;
import com.example.resqtap.auth.ForgotPasswordActivity;
import com.example.resqtap.auth.GetStartedActivity;
import com.example.resqtap.auth.LoginActivity;
import com.example.resqtap.auth.RegisterActivity;
import com.example.resqtap.utils.UserPrefs;

/**
 * AppLockManager
 * Menguruskan pengesahan PIN / Kunci Aplikasi setiap kali pengguna masuk semula ke app.
 */
public class AppLockManager implements Application.ActivityLifecycleCallbacks {

    private static AppLockManager instance;
    private int runningActivities = 0;
    private boolean isChangingConfigurations = false;
    private static boolean isUnlockedForSession = false;

    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new AppLockManager();
            app.registerActivityLifecycleCallbacks(instance);
        }
    }

    public static void setUnlocked(boolean unlocked) {
        isUnlockedForSession = unlocked;
    }

    public static boolean isUnlocked() {
        return isUnlockedForSession;
    }

    public static void checkLock(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity instanceof AppLockActivity ||
            activity instanceof SetPinActivity ||
            activity instanceof SplashActivity ||
            activity instanceof LoginActivity ||
            activity instanceof RegisterActivity ||
            activity instanceof ForgotPasswordActivity ||
            activity instanceof GetStartedActivity ||
            activity instanceof com.example.resqtap.call.IncomingCallActivity ||
            activity instanceof com.example.resqtap.call.VoiceCallActivity ||
            activity instanceof com.example.resqtap.call.VideoCallActivity ||
            activity instanceof com.example.resqtap.sos.SosAlarmActivity ||
            activity instanceof com.example.resqtap.sos.SosProgressActivity ||
            activity instanceof com.example.resqtap.sos.SosLivechatActivity) {
            return;
        }

        Context ctx = activity.getApplicationContext();
        boolean appLockOn = UserPrefs.isAppLockEnabled(ctx);
        boolean biometricOn = UserPrefs.isFingerprintEnabled(ctx);

        boolean hasAppLockPin = appLockOn && UserPrefs.hasAppLockPin(ctx);
        boolean needsLock = (hasAppLockPin || biometricOn);

        if (needsLock && !isUnlockedForSession) {
            Intent intent = new Intent(activity, AppLockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (++runningActivities == 1 && !isChangingConfigurations) {
            // App came from background into foreground!
            isUnlockedForSession = false;
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        checkLock(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        isChangingConfigurations = activity.isChangingConfigurations();
        --runningActivities;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
