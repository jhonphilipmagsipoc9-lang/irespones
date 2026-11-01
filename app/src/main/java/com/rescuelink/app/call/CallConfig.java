package com.rescuelink.app.call;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

/**
 * Servers that help two phones find a path to each other. STUN is enough when both phones are on ordinary
 * home or office Wi-Fi. Some mobile networks and strict firewalls also need a TURN relay: put your own TURN
 * server below for dependable calls (the free public relay listed here is for testing only and may be slow or gone).
 */
final class CallConfig {
    private CallConfig() { }

    // Your own TURN server (recommended for production). Leave TURN_URL empty to skip it.
    private static final String TURN_URL = "";
    private static final String TURN_USER = "";
    private static final String TURN_PASSWORD = "";

    static List<PeerConnection.IceServer> iceServers() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        if (!TURN_URL.isEmpty()) {
            list.add(PeerConnection.IceServer.builder(TURN_URL)
                    .setUsername(TURN_USER).setPassword(TURN_PASSWORD).createIceServer());
        } else {
            // Public relay for testing only.
            list.add(PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                    .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer());
        }
        return list;
    }
}
