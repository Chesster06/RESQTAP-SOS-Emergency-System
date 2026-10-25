package com.example.resqtap.call;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * IncomingCallManager
 * Global manager that listens for incoming calls across the entire application lifecycle.
 * Ensures that whenever a user is logged in, regardless of whether they are in a room,
 * making a report, in settings, or on lockscreen, incoming calls from web/mobile arrive immediately.
 */
public class IncomingCallManager {

    private static final String TAG = "IncomingCallManager";
    private static IncomingCallManager instance;
    private final Context context;
    private FirebaseAuth.AuthStateListener authStateListener;
    private String currentListeningUid = null;

    private IncomingCallManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized void init(Application app) {
        if (instance == null) {
            instance = new IncomingCallManager(app);
            instance.setup();
        }
    }

    public static synchronized IncomingCallManager getInstance() {
        return instance;
    }

    private void setup() {
        CallNotificationHelper.ensureIncomingCallChannel(context);

        authStateListener = auth -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                startListening(user.getUid());
            } else {
                stopListening();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current != null) {
            startListening(current.getUid());
        }
    }

    public synchronized void startListening(String uid) {
        if (uid == null || uid.trim().isEmpty()) return;
        if (uid.equals(currentListeningUid)) {
            // Already listening for this user
            return;
        }

        currentListeningUid = uid;
        Log.d(TAG, "Starting global incoming call listener for uid: " + uid);

        CallSignalingClient.getInstance().listenForIncomingCalls(uid, new CallSignalingClient.IncomingCallHandler() {
            @Override
            public void onIncomingCall(String callId, String callerName, String callerPhotoUrl, String callType) {
                Log.d(TAG, "Incoming call received! callId=" + callId + " caller=" + callerName + " type=" + callType);
                handleIncomingCall(callId, callerName, callerPhotoUrl, callType);
            }

            @Override
            public void onCallCancelled(String callId) {
                Log.d(TAG, "Incoming call cancelled/ended: " + callId);
                handleCallCancelled(callId);
            }
        });
    }

    public synchronized void stopListening() {
        Log.d(TAG, "Stopping global incoming call listener");
        currentListeningUid = null;
        CallSignalingClient.getInstance().stopListeningForIncomingCalls();
        CallNotificationHelper.dismissIncomingCallNotification(context);
    }

    private void handleIncomingCall(String callId, String callerName, String callerPhotoUrl, String callType) {
        if (CallSignalingClient.isAwaitingSosResponse()) {
            Log.d(TAG, "User is awaiting outgoing SOS call response. Skipping IncomingCallActivity popup for callId=" + callId);
            return;
        }

        // 1. Post high priority full-screen notification (vital for Android 10+ / 12 / 13 / 14 background & lockscreen)
        CallNotificationHelper.showIncomingCallNotification(context, callId, callerName, callerPhotoUrl, callType);

        // 2. Also attempt direct startActivity if possible (e.g. app in foreground or system allows)
        try {
            Intent callIntent = new Intent(context, IncomingCallActivity.class);
            callIntent.putExtra("callId", callId);
            callIntent.putExtra("callerName", callerName);
            callIntent.putExtra("callerPhotoUrl", callerPhotoUrl);
            callIntent.putExtra("callType", callType);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(callIntent);
        } catch (Exception e) {
            Log.w(TAG, "Direct startActivity deferred to full-screen notification: " + e.getMessage());
        }
    }

    private void handleCallCancelled(String callId) {
        // Dismiss notification
        CallNotificationHelper.dismissIncomingCallNotification(context);

        // Broadcast cancellation to dismiss IncomingCallActivity if open
        try {
            Intent intent = new Intent("com.example.resqtap.CALL_CANCELLED");
            intent.putExtra("callId", callId);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception ignored) {}
    }
}
