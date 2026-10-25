package com.example.resqtap.call;

import com.example.resqtap.room.FirebaseRoomClient;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;


/**
 * CallSignalingClient
 * Signaling Client (Firebase RTDB): tukar SDP Offer/Answer dan ICE Candidate antara caller & callee.
 */
public class CallSignalingClient {
    private static CallSignalingClient instance;
    private final DatabaseReference rootRef;

    private ValueEventListener incomingCallListener;
    private DatabaseReference incomingCallRef;

    public interface IncomingCallHandler {
        void onIncomingCall(String callId, String callerName, String callerPhotoUrl, String callType);
        void onCallCancelled(String callId);
    }

    private CallSignalingClient() {
        rootRef = FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference();
    }

    private static volatile boolean awaitingSosResponse = false;

    public static void setAwaitingSosResponse(boolean awaiting) {
        awaitingSosResponse = awaiting;
    }

    public static boolean isAwaitingSosResponse() {
        return awaitingSosResponse;
    }

    public static synchronized CallSignalingClient getInstance() {
        if (instance == null) {
            instance = new CallSignalingClient();
        }
        return instance;
    }

    public void listenForIncomingCalls(String myUid, IncomingCallHandler handler) {
        if (myUid == null || myUid.trim().isEmpty() || handler == null) return;

        stopListeningForIncomingCalls();

        incomingCallRef = rootRef.child("userCalls").child(myUid).child("currentCall");
        incomingCallListener = new ValueEventListener() {
            private String lastActiveCallId = null;

            /** Callback apabila data Firebase Realtime Database berubah. */
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot == null || !snapshot.exists()) {
                    if (lastActiveCallId != null && !lastActiveCallId.isEmpty()) {
                        String id = lastActiveCallId;
                        lastActiveCallId = null;
                        handler.onCallCancelled(id);
                    }
                    return;
                }

                String status = getString(snapshot, "status");
                String callId = getString(snapshot, "callId");

                if ("ringing".equals(status) && !callId.isEmpty()) {
                    lastActiveCallId = callId;
                    String callerName = getString(snapshot, "callerName");
                    String callerPhotoUrl = getString(snapshot, "callerPhotoUrl");
                    String callType = getString(snapshot, "callType");
                    handler.onIncomingCall(callId, callerName, callerPhotoUrl, callType.isEmpty() ? "video" : callType);
                } else if ("cancelled".equals(status) || "ended".equals(status)) {
                    lastActiveCallId = null;
                    handler.onCallCancelled(callId);
                }
            }

            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        incomingCallRef.addValueEventListener(incomingCallListener);
    }

    public void stopListeningForIncomingCalls() {
        if (incomingCallRef != null && incomingCallListener != null) {
            incomingCallRef.removeEventListener(incomingCallListener);
        }
        incomingCallListener = null;
        incomingCallRef = null;
    }

    public void acceptCall(String myUid, String callId) {
        if (myUid == null || myUid.isEmpty() || callId == null || callId.isEmpty()) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("acceptedAt", com.google.firebase.database.ServerValue.TIMESTAMP);

        rootRef.child("userCalls").child(myUid).child("currentCall").updateChildren(updates);
    }

    public void rejectCall(String myUid, String callId) {
        if (myUid == null || myUid.isEmpty() || callId == null || callId.isEmpty()) return;

        DatabaseReference callRef = rootRef.child("userCalls").child(myUid).child("currentCall");
        callRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    if ("accepted".equalsIgnoreCase(status) || "ended".equalsIgnoreCase(status)) {
                        return;
                    }
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", "rejected");
                updates.put("rejectedAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                callRef.updateChildren(updates);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        rootRef.child("userCalls").child(myUid).removeValue();
                        rootRef.child("calls").child(callId).removeValue();
                    } catch (Exception ignored) {}
                }, 2500);
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    public void declineWithChat(String myUid, String callId) {
        if (myUid == null || myUid.isEmpty()) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "chat_instead");
        updates.put("chatInstead", true);
        updates.put("rejectedAt", com.google.firebase.database.ServerValue.TIMESTAMP);

        rootRef.child("userCalls").child(myUid).child("currentCall").updateChildren(updates);

        if (callId != null && !callId.isEmpty()) {
            rootRef.child("calls").child(callId).updateChildren(updates);
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                rootRef.child("userCalls").child(myUid).removeValue();
                if (callId != null && !callId.isEmpty()) {
                    rootRef.child("calls").child(callId).removeValue();
                }
            } catch (Exception ignored) {}
        }, 2500);
    }

    public void endCall(String myUid, String callId) {
        if (myUid == null || myUid.isEmpty()) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "ended");
        updates.put("endedAt", com.google.firebase.database.ServerValue.TIMESTAMP);

        rootRef.child("userCalls").child(myUid).child("currentCall").updateChildren(updates);

        if (callId != null && !callId.isEmpty()) {
            rootRef.child("calls").child(callId).updateChildren(updates);
        }

        // Semak dan kemas kini adminCalls/incoming sekiranya berkaitan panggilan SOS
        rootRef.child("adminCalls").child("incoming").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String callerUid = snapshot.child("callerUid").getValue(String.class);
                    if (myUid.equals(callerUid)) {
                        Map<String, Object> adminUpdates = new HashMap<>();
                        adminUpdates.put("status", "ended");
                        adminUpdates.put("endedAt", com.google.firebase.database.ServerValue.TIMESTAMP);
                        rootRef.child("adminCalls").child("incoming").updateChildren(adminUpdates);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // Bersihkan nod sementara RTDB selepas acara penamatan panggilan
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                rootRef.child("userCalls").child(myUid).removeValue();
                if (callId != null && !callId.isEmpty()) {
                    rootRef.child("calls").child(callId).removeValue();
                }
                rootRef.child("adminCalls").child("incoming").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String callerUid = snapshot.child("callerUid").getValue(String.class);
                            if (myUid.equals(callerUid)) {
                                rootRef.child("adminCalls").child("incoming").removeValue();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {}
                });
            } catch (Exception ignored) {}
        }, 2500);
    }

    /** Simpan atau hantar data Answer. */
    public void sendAnswer(String callId, String sdp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "answer");
        payload.put("sdp", sdp);
        rootRef.child("calls").child(callId).child("answer").setValue(payload);
    }

    /** Simpan atau hantar data IceCandidate. */
    public void sendIceCandidate(String callId, String role, String sdpMid, int sdpMLineIndex, String sdp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sdpMid", sdpMid);
        payload.put("sdpMLineIndex", sdpMLineIndex);
        payload.put("candidate", sdp);

        rootRef.child("calls").child(callId).child(role).push().setValue(payload);
    }

    /** Dengar tawaran SDP Offer dari pemanggil melalui RTDB. */
    public void listenForOffer(String callId, ValueEventListener listener) {
        rootRef.child("calls").child(callId).child("offer").addValueEventListener(listener);
    }

    /** Dengar maklumat ICE Candidates untuk bina sambungan P2P. */
    public void listenForIceCandidates(String callId, String role, ChildEventListener listener) {
        rootRef.child("calls").child(callId).child(role).addChildEventListener(listener);
    }

    public void setCameraState(String callId, String role, boolean isEnabled) {
        if (callId == null || callId.isEmpty()) return;
        rootRef.child("calls").child(callId).child("cameraState").child(role).setValue(isEnabled);
    }

    public void listenForCameraState(String callId, String role, ValueEventListener listener) {
        if (callId == null || callId.isEmpty()) return;
        rootRef.child("calls").child(callId).child("cameraState").child(role).addValueEventListener(listener);
    }

    /** Padam atau bersihkan CameraStateListener. */
    public void removeCameraStateListener(String callId, String role, ValueEventListener listener) {
        if (callId == null || listener == null) return;
        rootRef.child("calls").child(callId).child("cameraState").child(role).removeEventListener(listener);
    }

    public void setMicState(String callId, String role, boolean isEnabled) {
        if (callId == null || callId.isEmpty()) return;
        rootRef.child("calls").child(callId).child("micState").child(role).setValue(isEnabled);
    }

    public void listenForMicState(String callId, String role, ValueEventListener listener) {
        if (callId == null || callId.isEmpty()) return;
        rootRef.child("calls").child(callId).child("micState").child(role).addValueEventListener(listener);
    }

    /** Padam atau bersihkan MicStateListener. */
    public void removeMicStateListener(String callId, String role, ValueEventListener listener) {
        if (callId == null || listener == null) return;
        rootRef.child("calls").child(callId).child("micState").child(role).removeEventListener(listener);
    }

    /** Padam atau bersihkan OfferListener. */
    public void removeOfferListener(String callId, ValueEventListener listener) {
        if (callId == null || listener == null) return;
        rootRef.child("calls").child(callId).child("offer").removeEventListener(listener);
    }

    /** Padam atau bersihkan IceCandidateListener. */
    public void removeIceCandidateListener(String callId, String role, ChildEventListener listener) {
        if (callId == null || listener == null) return;
        rootRef.child("calls").child(callId).child(role).removeEventListener(listener);
    }

    private String getString(DataSnapshot snapshot, String key) {
        if (snapshot.hasChild(key)) {
            Object val = snapshot.child(key).getValue();
            return val != null ? String.valueOf(val) : "";
        }
        return "";
    }
}

