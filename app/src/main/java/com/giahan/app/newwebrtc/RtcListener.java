package com.giahan.app.newwebrtc;

import org.webrtc.MediaStream;

public interface RtcListener {
    void onCallReady(String callId);

    void onStatusChanged(String newStatus);

    void onLocalStream(MediaStream localStream);

    void onAddRemoteStream(MediaStream remoteStream);

    void onRemoveRemoteStream(int endPoint);
}
