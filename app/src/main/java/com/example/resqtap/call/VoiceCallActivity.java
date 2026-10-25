package com.example.resqtap.call;
import com.example.resqtap.R;

import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.room.FirebaseRoomClient;
import com.example.resqtap.utils.AvatarUtils;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;

/**
 * VoiceCallActivity
 * Panggilan suara WebRTC 1-on-1: penstriman audio mikrofon, speakerphone, dan kawalan bisu (mute).
 */
public class VoiceCallActivity extends BaseActivity implements WebRTCClient.WebRTCListener {

    private static final int REQUEST_AUDIO = 42;

    private String callId;
    private String callerName;
    private String callerPhotoUrl;
    private WebRTCClient webRTCClient;
    private EglBase rootEglBase;
    private AudioManager audioManager;

    private boolean isMicEnabled = true;
    private boolean isSpeakerEnabled = false;
    private boolean remoteEnded = false;

    private ValueEventListener offerListener;
    private ChildEventListener iceCandidateListener;
    private ValueEventListener callStatusListener;
    private ValueEventListener remoteMicStateListener;

    private TextView statusView;
    private TextView remoteMuteView;

    @Override
    protected boolean shouldAnimateContentIn() {
        return false;
    }

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_voice_call);

        callId = getIntent().getStringExtra("callId");
        callerName = getIntent().getStringExtra("callerName");
        callerPhotoUrl = getIntent().getStringExtra("callerPhotoUrl");

        if (callId == null || callId.isEmpty()) {
            finish();
            return;
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false);

        bindViews();

        if (hasAudioPermission()) {
            startWebRTC();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
        }
    }

    // 1. BIND VIEWS & BUTTON CONTROLS
    /** Sambungkan elemen paparan UI dan tetapkan fungsi klik butang panggilan. */
    private void bindViews() {
        TextView nameView = findViewById(R.id.voice_caller_name);
        statusView = findViewById(R.id.voice_call_status);
        remoteMuteView = findViewById(R.id.voice_remote_mute_status);
        ShapeableImageView photoView = findViewById(R.id.voice_caller_photo);

        if (callerName != null && !callerName.trim().isEmpty()) {
            nameView.setText(callerName);
        }
        if (callerPhotoUrl != null && !callerPhotoUrl.trim().isEmpty()) {
            AvatarUtils.applyAvatar(photoView, "", callerPhotoUrl, R.drawable.resqtap);
        }

        FloatingActionButton micButton = findViewById(R.id.btn_voice_toggle_mic);
        FloatingActionButton speakerButton = findViewById(R.id.btn_voice_toggle_speaker);
        FloatingActionButton endButton = findViewById(R.id.btn_voice_end_call);

        micButton.setOnClickListener(v -> {
            isMicEnabled = !isMicEnabled;
            if (webRTCClient != null) webRTCClient.toggleAudio(isMicEnabled);
            micButton.setImageResource(isMicEnabled ? R.drawable.ic_mic_24 : R.drawable.ic_mic_off_24);
            CallSignalingClient.getInstance().setMicState(callId, "callee", isMicEnabled);
        });

        speakerButton.setOnClickListener(v -> {
            isSpeakerEnabled = !isSpeakerEnabled;
            if (audioManager != null) audioManager.setSpeakerphoneOn(isSpeakerEnabled);
            speakerButton.setImageResource(isSpeakerEnabled ? R.drawable.ic_volume_24 : R.drawable.ic_phone);
        });

        endButton.setOnClickListener(v -> {
            remoteEnded = false;
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                CallSignalingClient.getInstance().endCall(FirebaseAuth.getInstance().getCurrentUser().getUid(), callId);
            }
            finish();
        });
    }

    /** Semak kebenaran audio mikrofon. */
    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    /** Handle callback kebenaran sistem Android daripada pengguna. */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO && hasAudioPermission()) {
            startWebRTC();
        } else {
            finish();
        }
    }

    // 2. START WEBRTC & FOREGROUND SERVICE
    /** Inisialisasi klien WebRTC tempatan, mulakan audio, dan lancarkan CallService. */
    private void startWebRTC() {
        rootEglBase = EglBase.create();
        webRTCClient = new WebRTCClient(this, rootEglBase, this);
        webRTCClient.startLocalAudio();
        webRTCClient.call();

        listenForOffer();
        listenForIceCandidates();
        listenForCallEnd();
        listenForRemoteMicState();
    }

    // 3. RTDB SIGNALING (SDP / ICE / CALL STATUS)
    /** Dengar tawaran SDP Offer dari pemanggil dan balas dengan SDP Answer. */
    private void listenForOffer() {
        offerListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String sdp = snapshot.child("sdp").getValue(String.class);
                if (sdp != null && webRTCClient != null) {
                    SessionDescription description = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                    webRTCClient.setRemoteDescriptionAndCreateAnswer(description, new WebRTCClient.AnswerCallback() {
                        @Override
                        public void onAnswerCreated(SessionDescription answer) {
                            CallSignalingClient.getInstance().sendAnswer(callId, answer.description);
                        }

                        @Override
                        public void onError(String error) {
                            android.util.Log.e("VoiceCallActivity", "Error setting offer and creating voice answer: " + error);
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        CallSignalingClient.getInstance().listenForOffer(callId, offerListener);
    }

    /** Dengar maklumat ICE candidates dari pemanggil untuk disambungkan ke WebRTCClient. */
    private void listenForIceCandidates() {
        iceCandidateListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, String previousChildName) {
                if (!snapshot.exists() || webRTCClient == null) return;
                String sdpMid = snapshot.child("sdpMid").getValue(String.class);
                Integer sdpMLineIndex = snapshot.child("sdpMLineIndex").getValue(Integer.class);
                String candidate = snapshot.child("candidate").getValue(String.class);
                if (sdpMid != null && sdpMLineIndex != null && candidate != null && !candidate.trim().isEmpty()) {
                    webRTCClient.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        CallSignalingClient.getInstance().listenForIceCandidates(callId, "callerCandidates", iceCandidateListener);
    }

    /** Pantau status panggilan dalam RTDB (/userCalls) sekiranya ditamatkan dari jauh. */
    private void listenForCallEnd() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        com.google.firebase.database.DatabaseReference statusRef = com.google.firebase.database.FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL)
                .getReference("userCalls").child(uid).child("currentCall").child("status");

        callStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("ended".equals(status)) {
                    remoteEnded = true;
                    runOnUiThread(() -> finish());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        statusRef.addValueEventListener(callStatusListener);
    }

    /** Dengar status bisu mikrofon pihak pemanggil. */
    private void listenForRemoteMicState() {
        remoteMicStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Boolean micOn = snapshot.getValue(Boolean.class);
                if (remoteMuteView != null) {
                    runOnUiThread(() -> {
                        if (micOn != null && !micOn) {
                            remoteMuteView.setVisibility(View.VISIBLE);
                        } else {
                            remoteMuteView.setVisibility(View.GONE);
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        CallSignalingClient.getInstance().listenForMicState(callId, "caller", remoteMicStateListener);
    }

    /** Terima stream media tempatan yang baru dimulakan. */
    @Override
    public void onLocalStream(VideoTrack videoTrack) {}

    /** Terima stream video/audio dari pihak pemanggil/penerima. */
    @Override
    public void onAddRemoteStream(VideoTrack videoTrack) {}

    /** Tamatkan paparan bila pihak lawan putuskan penstriman audio. */
    @Override
    public void onRemoveRemoteStream() {
        runOnUiThread(() -> {
            remoteEnded = true;
            finish();
        });
    }

    /** Hantar calon ICE tempatan ke Firebase RTDB untuk bina laluan P2P. */
    @Override
    public void onIceCandidate(IceCandidate candidate) {
        if (candidate != null && candidate.sdp != null && !candidate.sdp.trim().isEmpty()) {
            CallSignalingClient.getInstance().sendIceCandidate(callId, "calleeCandidates", candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
        }
    }

    /** Pantau status sambungan WebRTC (Connected, Failed, Closed). */
    @Override
    public void onConnectionStateChange(PeerConnection.PeerConnectionState state) {
        if (state == PeerConnection.PeerConnectionState.CONNECTED) {
            runOnUiThread(() -> {
                if (statusView != null) statusView.setText(R.string.call_connected);
            });
        } else if (state == PeerConnection.PeerConnectionState.DISCONNECTED ||
                state == PeerConnection.PeerConnectionState.FAILED ||
                state == PeerConnection.PeerConnectionState.CLOSED) {
            runOnUiThread(() -> {
                remoteEnded = true;
                finish();
            });
        }
    }

    // 4. CLEANUP & TEARDOWN
    /** Buang semua listener pangkalan data, lepaskan WebRTC, dan hentikan audio. */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        CallSignalingClient.getInstance().removeOfferListener(callId, offerListener);
        CallSignalingClient.getInstance().removeIceCandidateListener(callId, "callerCandidates", iceCandidateListener);
        CallSignalingClient.getInstance().removeMicStateListener(callId, "caller", remoteMicStateListener);

        if (callStatusListener != null && callId != null && FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            com.google.firebase.database.FirebaseDatabase.getInstance(FirebaseRoomClient.DATABASE_URL).getReference("userCalls")
                    .child(auth.getCurrentUser().getUid())
                    .child("currentCall").child("status")
                    .removeEventListener(callStatusListener);
        }

        if (webRTCClient != null) {
            webRTCClient.close();
            webRTCClient = null;
        }

        if (rootEglBase != null) {
            rootEglBase.release();
            rootEglBase = null;
        }

        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        }

        if (!remoteEnded && FirebaseAuth.getInstance().getCurrentUser() != null) {
            CallSignalingClient.getInstance().endCall(FirebaseAuth.getInstance().getCurrentUser().getUid(), callId);
        }
    }
}
