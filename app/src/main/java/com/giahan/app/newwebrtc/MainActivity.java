package com.giahan.app.newwebrtc;

import android.Manifest;
import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;
import io.socket.client.IO;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnection.SignalingState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;

public class MainActivity extends AppCompatActivity implements RtcListener{

    public final static int CAMERA_PERMISSION_ID = 10001;

    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private io.socket.client.Socket mSocket;
    private PeerConnectionFactory factory;
    private LinkedList<IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
    private PeerConnection mPeerConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermission();
    }



    private void checkPermission() {
        if (checkStorePermission(this, CAMERA_PERMISSION_ID)) {
            implementData();
        } else {
            showRequestPermission(this, CAMERA_PERMISSION_ID);
        }
    }

    private void implementData() {
        setupSocket();
        setupVideoLayout();
        receiverInviteCall();
        setupServer();
        onReceiverOffer();
        onReceiverCandidate();


    }

    private void setupPeer() {
        mPeerConnection = factory
                .createPeerConnection(iceServers, pcConstraints, new CustomPeerConnectionObserver("setupPeer"){
                    @Override
                    public void onIceCandidate(final IceCandidate iceCandidate) {
                        super.onIceCandidate(iceCandidate);
                        emitCandidate(iceCandidate);
                    }

                    @Override
                    public void onAddStream(final MediaStream mediaStream) {
                        super.onAddStream(mediaStream);
                        mListener.onAddRemoteStream(mediaStream);
                    }
                });
        mPeerConnection.addStream(localMS);
        mListener.onStatusChanged("CONNECTING");
    }

    private void onReceiverOffer() {
        mSocket.on(Constant.TAG_RTC_OFFER_SOCKET, args -> runOnUiThread(() -> {
            JSONObject data = (JSONObject) args[0];
            SessionDescription sdp = null;
            try {
                sdp = new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp"));
                mPeerConnection.setRemoteDescription(new CustomSdpObserver("OFFER"), sdp);
                mPeerConnection.createAnswer(new CustomSdpObserver("CREATE ANSWER"){
                    @Override
                    public void onCreateSuccess(final SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        mPeerConnection.setLocalDescription(new CustomSdpObserver("CREATE ANSWER OK"),sessionDescription);
                        emitAnswer(sessionDescription);
                    }
                }, pcConstraints);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }));
    }

    private void emitAnswer(final SessionDescription sessionDescription) {
        try {
            Log.e("SignallingClient", "emitAnswer() called with: message = [" + sessionDescription + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", sessionDescription.type.canonicalForm());
            obj.put("sdp", sessionDescription.description);
            Log.d("emitMessage", obj.toString());
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("sdp", obj);
            jsonObject.put("toUserId", Constant.DOCTOR_ID);
            mSocket.emit(Constant.TAG_RTC_ANSWER_SOCKET, jsonObject);
            Log.e("SignallingClient", "emitAnswer:  -----> json: "+jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void emitCandidate(final IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
//            object.put("type", "candidate");
            object.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            object.put("sdpMid", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("toUserId", Constant.DOCTOR_ID);
            jsonObject.put("candidate", object);
            mSocket.emit(Constant.TAG_RTC_CANDIDATE_SOCKET, jsonObject);
            Log.e("SignallingClient", "emitIceCandidate:  -----> "+jsonObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onReceiverCandidate(){
        mSocket.on(Constant.TAG_RTC_CANDIDATE_SOCKET, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            try {
                JSONObject candidate = jsonObject.getJSONObject("candidate");
                if (mPeerConnection != null) {
                    Log.e("VideoCallActivity", "onIceCandidateReceived: 111 -----> "+jsonObject.toString());
                    IceCandidate iceCandidate = new IceCandidate(candidate.getString("sdpMid"), candidate.getInt("sdpMLineIndex"),
                            candidate.getString("candidate"));
                    mPeerConnection.addIceCandidate(iceCandidate);
                    Log.e("VideoCallActivity", "receiverCandidate: TT -----> "+iceCandidate);
                }else {
                    Log.e("VideoCallActivity", "onIceCandidateReceived:  -----> NULL ");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }));
    }

    private void setupVideoLayout() {
        vsv = findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, () -> init());

        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
//            callerId = segments.get(0);
        }
    }

    private void setupServer(){
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("stun:stun1.l.google.com:19302"));
        iceServers.add(new PeerConnection.IceServer("turn:103.221.222.146:3478?transport=udp", "vietskin", "tombeo99"));
        iceServers.add(new PeerConnection.IceServer("turn:103.221.222.146:3478?transport=tcp", "vietskin", "tombeo99"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        pcParams = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);
        PeerConnectionFactory.initializeAndroidGlobals(mListener, true, true,
                pcParams.videoCodecHwAcceleration, VideoRendererGui.getEGLContext());
        factory = new PeerConnectionFactory();
        setupPeer();
        setCamera();
    }

    private void setupSocket() {
        if (mSocket == null) {
            JSONObject jsonObject = new JSONObject();
            try {
                mSocket = IO.socket(Constant.URL_SOCKET);
                mSocket.connect();
                mSocket.on("connect", args -> runOnUiThread(() -> {
                    Log.e("CONNECT", "setupSocket:  -----> ");
                }));
                jsonObject.put("access_token", Constant.TOKEN);
                mSocket.emit(Constant.TAG_LOGIN_SOCKET, jsonObject);
                Log.e("MainActivity", "setupSocket:  -----> login socket ok");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            } catch (JSONException ignored) {
                Toast.makeText(this, "Socket error!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void receiverInviteCall(){
        mSocket.on(Constant.TAG_VIDEO_INVITE, args -> runOnUiThread(() -> {
            JSONObject jsonObject = (JSONObject) args[0];
            String doctor_id;
            String doctor_name;
            if (jsonObject != null) {
                Log.e("VideoCall2Activity", "receiverInviteCall:  -----> NHAN INVITE OK");
                try {
                    doctor_id = jsonObject.getString("fromUserId");
                    doctor_name = jsonObject.getString("fromUserName");
                    Log.e("VideoDemoActivity", "receiverInviteCall:  -----> doctor name: "+doctor_name);
                    toAcceptCall();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }));
    }

    private void toAcceptCall(){
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(Constant.TAG_TO_USER_ID, Constant.DOCTOR_ID);
            mSocket.emit(Constant.TAG_VIDEO_ACCEPT, jsonObject);
            Log.e("VideoCall2Activity", "toInviteCall:  -----> TO ACCEPT OK");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setCamera() {
        localMS = factory.createLocalMediaStream("ARDAMS");
        if (pcParams.videoCallEnabled) {
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }

    private static void showRequestPermission(Activity activity, int requestCode) {
        String[] permissions;

        permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                permission.RECORD_AUDIO,
                permission.CAPTURE_VIDEO_OUTPUT,
                permission.MODIFY_AUDIO_SETTINGS
        };

        PermissionsUtil.requestPermissions(activity, requestCode, permissions);
    }

    private boolean checkStorePermission(Context context, int permission) {
        if (permission == CAMERA_PERMISSION_ID) {
            String[] permissions = new String[]{
                    Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            return PermissionsUtil.checkPermissions(context, permissions);
        } else {
            return true;
        }
    }

//
//--------------

    @Override
    public void onCallReady(final String callId) {

    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onLocalStream(final MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType, false);
    }

    @Override
    public void onAddRemoteStream(final MediaStream remoteStream) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType, false);
    }

    @Override
    public void onRemoveRemoteStream(final int endPoint) {

    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
            @NonNull final int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_ID) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                implementData();
            }
        }
    }
}
