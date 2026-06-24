package com.example.resqtap.notification;

import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.UserPrefs;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;


/**
 * ResQTapMessagingService
 * FCM Service: handle push notification background untuk SOS alert, call, dan admin notice.
 */
public class ResQTapMessagingService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        persistToken(this, token);
    }

    @Override
}
