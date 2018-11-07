package com.giahan.app.newwebrtc;

import android.util.Log;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.SignalingState;

public class CustomPeerConnectionObserver implements PeerConnection.Observer {

    private String logTag;

    public CustomPeerConnectionObserver(String logTag) {
        this.logTag = this.getClass().getCanonicalName();
        this.logTag = this.logTag+" "+logTag;
    }

    @Override
    public void onSignalingChange(final SignalingState signalingState) {
        Log.e(logTag, "onSignalingChange:  -----> ");
    }

    @Override
    public void onIceConnectionChange(final IceConnectionState iceConnectionState) {
        Log.e(logTag, "onIceConnectionChange:  -----> ");
    }

    @Override
    public void onIceGatheringChange(final IceGatheringState iceGatheringState) {
        Log.e(logTag, "onIceGatheringChange:  -----> ");
    }

    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {
        Log.e(logTag, "onIceCandidate:  -----> ");
    }

    @Override
    public void onAddStream(final MediaStream mediaStream) {
        Log.e(logTag, "onAddStream:  -----> ");
    }

    @Override
    public void onRemoveStream(final MediaStream mediaStream) {
        Log.e(logTag, "onRemoveStream:  -----> ");
    }

    @Override
    public void onDataChannel(final DataChannel dataChannel) {
        Log.e(logTag, "onDataChannel:  -----> ");
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.e(logTag, "onRenegotiationNeeded:  -----> ");
    }
}
