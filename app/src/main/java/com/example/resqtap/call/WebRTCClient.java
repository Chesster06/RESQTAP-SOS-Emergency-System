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

    public interface AnswerCallback {
        void onAnswerCreated(SessionDescription answer);
        void onError(String error);
    }

    private final List<IceCandidate> queuedCandidates = new ArrayList<>();
    private boolean isRemoteDescriptionSet = false;

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
        iceServers.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.services.mozilla.com").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer());

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
                        iceConnectionState == PeerConnection.IceConnectionState.COMPLETED) {
                    if (listener != null) listener.onConnectionStateChange(PeerConnection.PeerConnectionState.CONNECTED);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                    if (listener != null) listener.onConnectionStateChange(PeerConnection.PeerConnectionState.FAILED);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                    if (listener != null) listener.onConnectionStateChange(PeerConnection.PeerConnectionState.DISCONNECTED);
                } else if (iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                    if (listener != null) listener.onConnectionStateChange(PeerConnection.PeerConnectionState.CLOSED);
                }
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                if (listener != null) listener.onConnectionStateChange(newState);
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {}

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

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

        java.util.List<String> streamIds = java.util.Collections.singletonList("ARDAMS");
        if (localVideoTrack != null && peerConnection != null) {
            peerConnection.addTrack(localVideoTrack, streamIds);
        }
        if (localAudioTrack != null && peerConnection != null) {
            peerConnection.addTrack(localAudioTrack, streamIds);
        }
    }

    /**
     * Memproses SDP Offer daripada pemanggil secara bersiri:
     * 1. setRemoteDescription
     * 2. drain ICE candidates yang tertunggak
     * 3. createAnswer
     * 4. setLocalDescription dengan answer
     * 5. hantar answer kepada pemanggil
     */
    public void setRemoteDescriptionAndCreateAnswer(SessionDescription sdp, AnswerCallback callback) {
        if (peerConnection == null) {
            if (callback != null) callback.onError("peerConnection is null");
            return;
        }

        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                android.util.Log.d("WebRTCClient", "setRemoteDescription SUCCESS. Draining candidates and creating answer...");
                drainQueuedCandidates();

                MediaConstraints constraints = new MediaConstraints();
                peerConnection.createAnswer(new SimpleSdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription answer) {
                        android.util.Log.d("WebRTCClient", "createAnswer SUCCESS. Setting local description...");
                        peerConnection.setLocalDescription(new SimpleSdpObserver() {
                            @Override
                            public void onSetSuccess() {
                                android.util.Log.d("WebRTCClient", "setLocalDescription SUCCESS. Dispatching answer to signaling...");
                                if (callback != null) {
                                    callback.onAnswerCreated(answer);
                                }
                            }

                            @Override
                            public void onSetFailure(String error) {
                                android.util.Log.e("WebRTCClient", "setLocalDescription FAILURE: " + error);
                                if (callback != null) callback.onError("setLocalDescription failed: " + error);
                            }
                        }, answer);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                        android.util.Log.e("WebRTCClient", "createAnswer FAILURE: " + error);
                        if (callback != null) callback.onError("createAnswer failed: " + error);
                    }
                }, constraints);
            }

            @Override
            public void onSetFailure(String error) {
                android.util.Log.e("WebRTCClient", "setRemoteDescription FAILURE: " + error);
                if (callback != null) callback.onError("setRemoteDescription failed: " + error);
            }
        }, sdp);
    }

    public synchronized void addIceCandidate(IceCandidate candidate) {
        if (candidate == null) return;
        if (peerConnection != null && isRemoteDescriptionSet) {
            peerConnection.addIceCandidate(candidate);
        } else {
            queuedCandidates.add(candidate);
        }
    }

    private synchronized void drainQueuedCandidates() {
        isRemoteDescriptionSet = true;
        if (peerConnection != null) {
            for (IceCandidate c : queuedCandidates) {
                peerConnection.addIceCandidate(c);
            }
            queuedCandidates.clear();
        }
    }

    public void setRemoteDescription(SessionDescription sdp) {
        if (peerConnection != null) {
            peerConnection.setRemoteDescription(new SimpleSdpObserver(), sdp);
        }
    }

    public void createAnswer(org.webrtc.SdpObserver observer) {
        if (peerConnection != null) {
            peerConnection.createAnswer(new SimpleSdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                    observer.onCreateSuccess(sessionDescription);
                }
            }, new MediaConstraints());
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
        synchronized (this) {
            queuedCandidates.clear();
            isRemoteDescriptionSet = false;
        }

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

