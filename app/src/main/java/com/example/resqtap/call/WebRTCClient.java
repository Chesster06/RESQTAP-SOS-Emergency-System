package com.example.resqtap.call;

import android.content.Context;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;


/**
 * WebRTCClient
 * WebRTC Engine: urus PeerConnection, video/audio stream, STUN/TURN server, dan connection P2P.
 */
public class WebRTCClient {

    private final Context context;
    private final EglBase rootEglBase;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private VideoSource localVideoSource;
    private AudioSource localAudioSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;

    private final WebRTCListener listener;

    public interface WebRTCListener {
        void onLocalStream(VideoTrack videoTrack);
        void onAddRemoteStream(VideoTrack videoTrack);
        void onRemoveRemoteStream();
        void onIceCandidate(IceCandidate candidate);
        void onConnectionStateChange(PeerConnection.PeerConnectionState state);
    }

    public WebRTCClient(Context context, EglBase rootEglBase, WebRTCListener listener) {
        this.context = context.getApplicationContext();
        this.rootEglBase = rootEglBase;
        this.listener = listener;
        initPeerConnectionFactory();
    }

    // 1. SETUP WEBRTC FACTORY & CODEC
    /** Sediakan PeerConnectionFactory, codec perkakasan video, dan modul audio. */
    private void initPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                rootEglBase.getEglBaseContext());

        org.webrtc.audio.AudioDeviceModule audioDeviceModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(org.webrtc.audio.JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
                .setUseHardwareNoiseSuppressor(org.webrtc.audio.JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
                .createAudioDeviceModule();

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        audioDeviceModule.release();
    }

    // 2. LOCAL MEDIA STREAM (KAMERA & MIC)
    /** Mulakan tangkapan video kamera tempatan dan paparkan pada SurfaceViewRenderer. */
    public void startLocalVideo(SurfaceViewRenderer localView) {
        try {
            videoCapturer = createVideoCapturer();
            if (videoCapturer != null) {
                SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
                localVideoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
                videoCapturer.initialize(surfaceTextureHelper, context, localVideoSource.getCapturerObserver());
                videoCapturer.startCapture(1024, 720, 30);

                localVideoTrack = peerConnectionFactory.createVideoTrack("100", localVideoSource);
                if (localView != null) {
                    localVideoTrack.addSink(localView);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("SOS_DEBUG", "Error initializing local video capturer", e);
        }

        try {
            MediaConstraints audioConstraints = new MediaConstraints();
            localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            localAudioTrack = peerConnectionFactory.createAudioTrack("101", localAudioSource);
        } catch (Exception e) {
            android.util.Log.e("SOS_DEBUG", "Error initializing local audio track", e);
        }

        if (listener != null && localVideoTrack != null) {
            listener.onLocalStream(localVideoTrack);
        }
    }

    /** Mulakan penstriman audio mikrofon tempatan tanpa video. */
    public void startLocalAudio() {
        try {
            MediaConstraints audioConstraints = new MediaConstraints();
            localAudioSource = peerConnectionFactory.createAudioSource(audioConstraints);
            localAudioTrack = peerConnectionFactory.createAudioTrack("101", localAudioSource);
        } catch (Exception e) {
            android.util.Log.e("SOS_DEBUG", "Error initializing local audio track", e);
        }
    }

    /** Cipta penangkap video (pilih kamera depan terlebih dahulu, kemudian belakang). */
    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer = null;
        if (Camera2Enumerator.isSupported(context)) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
        }
        if (videoCapturer == null) {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        if (enumerator == null) return null;
        final String[] deviceNames = enumerator.getDeviceNames();
        if (deviceNames == null || deviceNames.length == 0) return null;

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                try {
                    VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                    if (videoCapturer != null) return videoCapturer;
                } catch (Exception ignored) {}
            }
        }

        for (String deviceName : deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                try {
                    VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                    if (videoCapturer != null) return videoCapturer;
                } catch (Exception ignored) {}
            }
        }

        for (String deviceName : deviceNames) {
            try {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) return videoCapturer;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // 3. SETUP PEERCONNECTION & CALL
    /** Bina PeerConnection dengan senarai pelayan STUN/TURN dan pasang trek media tempatan. */
    public void call() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            /** Pantau perubahan fasa pensinyalan WebRTC. */
    @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            /** Pantau status sambungan rangkaian ICE. */
    @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}

    @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                if (listener != null) listener.onConnectionStateChange(newState);
            }

    @Override
            public void onIceConnectionReceivingChange(boolean b) {}

    @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

            /** Hantar calon ICE tempatan ke Firebase RTDB untuk bina laluan P2P. */
    @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                if (listener != null) listener.onIceCandidate(iceCandidate);
            }

    @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

    @Override
            public void onAddStream(MediaStream mediaStream) {
                if (mediaStream.videoTracks.size() > 0) {
                    if (listener != null) listener.onAddRemoteStream(mediaStream.videoTracks.get(0));
                }
            }

    @Override
            public void onRemoveStream(MediaStream mediaStream) {
                if (listener != null) listener.onRemoveRemoteStream();
            }

    @Override
            public void onDataChannel(DataChannel dataChannel) {}

    @Override
            public void onRenegotiationNeeded() {}

    @Override
            public void onAddTrack(org.webrtc.RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                if (rtpReceiver.track() instanceof VideoTrack) {
                    if (listener != null) listener.onAddRemoteStream((VideoTrack) rtpReceiver.track());
                }
            }
        });

        if (localVideoTrack != null && peerConnection != null) {
            peerConnection.addTrack(localVideoTrack);
        }
        if (localAudioTrack != null && peerConnection != null) {
            peerConnection.addTrack(localAudioTrack);
        }
    }

    /** Tetapkan SessionDescription jauh (Offer atau Answer daripada pihak lawan). */
    public void setRemoteDescription(SessionDescription sdp) {
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver(), sdp);
        }
    }

    public void createAnswer(org.webrtc.SdpObserver observer) {
        if (peerConnection != null) {
            peerConnection.createAnswer(new SimpleSdpObserver() {
                /** Callback apabila SDP Offer/Answer berjaya dicipta. */
    @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                    observer.onCreateSuccess(sessionDescription);
                }
            }, new MediaConstraints());
        }
    }

    /** Masukkan ICE candidate daripada pihak lawan untuk pembinaan laluan rangkaian P2P. */
    public void addIceCandidate(IceCandidate candidate) {
        if (peerConnection != null) {
            peerConnection.addIceCandidate(candidate);
        }
    }

    /** Senyapkan atau aktifkan mikrofon tempatan (Mute/Unmute Audio). */
    public void toggleAudio(boolean enable) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(enable);
        }
    }

    /** Matikan atau hidupkan penstriman video kamera tempatan. */
    public void toggleVideo(boolean enable) {
        if (localVideoTrack != null) {
            localVideoTrack.setEnabled(enable);
        }
    }

    // 6. TEARDOWN & CLEANUP MEMORY
    /** Tutup sambungan PeerConnection, lepaskan kamera, dan bersihkan semua sumber media. */
    public void close() {
        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (localVideoSource != null) {
            localVideoSource.dispose();
            localVideoSource = null;
        }

        if (localAudioSource != null) {
            localAudioSource.dispose();
            localAudioSource = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection.dispose();
            peerConnection = null;
        }

        if (peerConnectionFactory != null) {
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }
    }

    public static class SimpleSdpObserver implements org.webrtc.SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String s) {}
        @Override public void onSetFailure(String s) {}
    }
}

