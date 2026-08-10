package com.example.resqtap.call;
import com.example.resqtap.R;

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
import com.example.resqtap.app.BaseActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;


/**
 * VideoCallActivity
 * Skrin Video Call WebRTC: video calling 1-on-1, preview kamera depan/belakang, toggle mic & video.
 */
public class VideoCallActivity extends BaseActivity implements WebRTCClient.WebRTCListener {

    private String callId;
    private String callerName;
    private WebRTCClient webRTCClient;
    private EglBase rootEglBase;

    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;

    private boolean isMicEnabled = true;
    private boolean isCamEnabled = true;
    private boolean isSpeakerEnabled = false;
    private boolean remoteEnded = false;

    private AudioManager audioManager;

    private ValueEventListener offerListener;
    private ChildEventListener iceCandidateListener;
    private ValueEventListener callStatusListener;
    private ValueEventListener cameraStateListener;

    private TextView localCameraDisabledOverlay;
    private TextView remoteCameraDisabledOverlay;

    private static final String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    // SEKSYEN: ONCREATE
    /** Inisialisasi aktiviti, bind view UI, dan setup listener. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_video_call);

        callId = getIntent().getStringExtra("callId");
        callerName = getIntent().getStringExtra("callerName");

        if (callId == null || callId.isEmpty()) {
            finish();
            return;
        }

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false);

        localVideoView = findViewById(R.id.local_video_view);
        remoteVideoView = findViewById(R.id.remote_video_view);
        localCameraDisabledOverlay = findViewById(R.id.local_camera_disabled_overlay);
        remoteCameraDisabledOverlay = findViewById(R.id.remote_camera_disabled_overlay);

        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setZOrderMediaOverlay(true);

        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.setEnableHardwareScaler(true);

        setupButtons();

        if (hasPermissions()) {
            startWebRTC();
        } else {
            ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
        }

    }

    /** Semak dan sahkan Permissions. */
    private boolean hasPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Handle callback kebenaran sistem Android daripada pengguna. */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && hasPermissions()) {
            startWebRTC();
        } else {
            finish();
        }
    }

    /** Setup dan konfigurasi Buttons. */
    private void setupButtons() {
        FloatingActionButton btnMic = findViewById(R.id.btn_toggle_mic);
        FloatingActionButton btnCam = findViewById(R.id.btn_toggle_cam);
        FloatingActionButton btnSpeaker = findViewById(R.id.btn_toggle_speaker);
        FloatingActionButton btnEnd = findViewById(R.id.btn_end_call);

        btnMic.setOnClickListener(v -> {
            isMicEnabled = !isMicEnabled;
            if (webRTCClient != null) webRTCClient.toggleAudio(isMicEnabled);
            btnMic.setImageResource(isMicEnabled ? R.drawable.ic_mic_24 : R.drawable.ic_mic_off_24);
            CallSignalingClient.getInstance().setMicState(callId, "callee", isMicEnabled);
        });

        btnCam.setOnClickListener(v -> {
            isCamEnabled = !isCamEnabled;
            if (webRTCClient != null) webRTCClient.toggleVideo(isCamEnabled);
            btnCam.setImageResource(isCamEnabled ? R.drawable.ic_livechat_camera : R.drawable.ic_close_24);
            localCameraDisabledOverlay.setVisibility(isCamEnabled ? View.GONE : View.VISIBLE);
            CallSignalingClient.getInstance().setCameraState(callId, "callee", isCamEnabled);
        });

        btnSpeaker.setOnClickListener(v -> {
            isSpeakerEnabled = !isSpeakerEnabled;
            audioManager.setSpeakerphoneOn(isSpeakerEnabled);
            btnSpeaker.setImageResource(isSpeakerEnabled ? R.drawable.ic_volume_24 : R.drawable.ic_phone);
        });

        btnEnd.setOnClickListener(v -> {
            remoteEnded = false;
            endCallAndFinish();
        });
    }

    /** Tamatkan panggilan di pangkalan data dan tutup aktiviti. */
    private void endCallAndFinish() {
        if (!remoteEnded && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            CallSignalingClient.getInstance().endCall(uid, callId);
        }
        finish();
    }

    // SEKSYEN: STARTWEBRTC
    /** Inisialisasi klien WebRTC dan mulakan sambungan panggilan. */
    private void startWebRTC() {
        webRTCClient = new WebRTCClient(this, rootEglBase, this);
        webRTCClient.startLocalVideo(localVideoView);
        webRTCClient.call();

        listenForOffer();
        listenForIceCandidates();
        listenForCallEnd();
    }

    /** Pantau status panggilan sekiranya ditamatkan dari jauh. */
    private void listenForCallEnd() {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        com.google.firebase.database.DatabaseReference statusRef = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("userCalls").child(uid).child("currentCall").child("status");

        callStatusListener = new ValueEventListener() {
            /** Callback apabila data Firebase Realtime Database berubah. */
    @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("ended".equals(status)) {
                    remoteEnded = true;
                    runOnUiThread(() -> finish());
                }
            }
            /** Callback sekiranya operasi Firebase dibatalkan atau gagal. */
    @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        statusRef.addValueEventListener(callStatusListener);
    }

    /** Dengar tawaran SDP Offer dari pemanggil melalui RTDB. */
}
