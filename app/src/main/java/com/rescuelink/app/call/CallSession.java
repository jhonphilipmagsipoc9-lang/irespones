package com.rescuelink.app.call;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import com.rescuelink.app.net.Cloud;
import com.rescuelink.app.net.Config;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * One voice call over the internet (WebRTC), like a Messenger or WhatsApp call. It needs no phone load.
 * The two phones agree on how to connect by passing small messages through the online service
 * (calls/{id} in the database); the voice itself travels directly between the phones.
 * Only one call can be active at a time. All methods are for the main thread.
 */
public final class CallSession {
    public enum State { CALLING, RINGING, CONNECTING, CONNECTED, ENDED }

    public interface Listener {
        void onState(State state, String message);
    }

    private static final long POLL_MS = 800;
    private static final long NO_ANSWER_MS = 45000;
    private static boolean factoryReady;
    private static CallSession current;

    /** The call on screen, or null. */
    public static CallSession current() { return current; }

    public static boolean busy() { return current != null && current.state != State.ENDED; }

    // ---------------------------------------------------------------- who
    public final String callId;
    public final boolean outgoing;
    public final String peerUser;
    public final String peerName;
    private final String myUser;
    private final String myName;
    private double lat = Double.NaN, lon = Double.NaN;       // caller's position, if shared
    private String offerSdp;                                  // incoming calls: the caller's offer

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String base;
    private final String me;                                  // "a" for the caller, "b" for the one called
    private final String other;
    private Listener listener;
    private State state;
    private String message = "";
    private long startedAt = System.currentTimeMillis();
    private long connectedAt;
    private boolean muted, speaker, begun, remoteSet, signalReady, endingSent;
    private int missing;

    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule adm;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private PeerConnection pc;
    private final Set<String> seenIce = new HashSet<>();
    private final List<IceCandidate> pendingRemoteIce = new ArrayList<>();
    private final List<String> pendingLocalIce = new ArrayList<>();
    private int savedAudioMode = AudioManager.MODE_NORMAL;

    private CallSession(Context c, String id, boolean outgoing, String peerUser, String peerName,
                        String myUser, String myName) {
        this.app = c.getApplicationContext();
        this.callId = id;
        this.outgoing = outgoing;
        this.peerUser = peerUser;
        this.peerName = peerName;
        this.myUser = myUser;
        this.myName = myName;
        this.base = Config.cloudUrl(app);
        this.me = outgoing ? "a" : "b";
        this.other = outgoing ? "b" : "a";
        this.state = outgoing ? State.CALLING : State.RINGING;
        this.message = outgoing ? "Calling…" : "Incoming call";
    }

    /** Prepares a call to a person. The call starts when {@link #begin()} is called (after the microphone permission). */
    public static CallSession startOutgoing(Context c, String toUser, String toName, String myUser, String myName,
                                            double lat, double lon) {
        if (busy()) return current;
        String id = "c" + System.currentTimeMillis() + Integer.toString((int) (Math.random() * 46656), 36);
        CallSession s = new CallSession(c, id, true, toUser, toName, myUser, myName);
        s.lat = lat;
        s.lon = lon;
        current = s;
        return s;
    }

    /** Prepares a call that someone else started. The phone rings until the person answers or declines. */
    static CallSession prepareIncoming(Context c, String id, String fromUser, String fromName, String offer,
                                       double lat, double lon, String myUser, String myName) {
        CallSession s = new CallSession(c, id, false, fromUser, fromName, myUser, myName);
        s.offerSdp = offer;
        s.lat = lat;
        s.lon = lon;
        current = s;
        s.schedulePoll();                  // notice if the caller gives up while it is still ringing
        return s;
    }

    public State state() { return state; }
    public String message() { return message; }
    public boolean hasCallerPosition() { return !Double.isNaN(lat) && !Double.isNaN(lon); }
    public double callerLat() { return lat; }
    public double callerLon() { return lon; }
    public boolean isMuted() { return muted; }
    public boolean isSpeaker() { return speaker; }
    public long connectedAt() { return connectedAt; }

    public void setListener(Listener l) {
        listener = l;
        if (l != null) l.onState(state, message);
    }

    private void set(State s, String msg) {
        state = s;
        message = msg;
        if (listener != null) listener.onState(s, msg);
    }

    // ---------------------------------------------------------------- caller
    /** Caller: set up the audio, create the offer and send it. */
    public void begin() {
        if (begun || !outgoing || state == State.ENDED) return;
        begun = true;
        startAudio();
        try {
            createPeer();
        } catch (RuntimeException e) {
            end("Could not start the call on this phone.");
            return;
        }
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pc.createOffer(new Sdp() {
            @Override public void onCreateSuccess(final SessionDescription d) {
                if (pc == null) return;
                pc.setLocalDescription(new Sdp() {
                    @Override public void onSetSuccess() {
                        main.post(new Runnable() {
                            @Override public void run() { if (state != State.ENDED) publishOffer(d.description); }
                        });
                    }
                    @Override public void onSetFailure(String e) { fail("Could not start the call."); }
                }, d);
            }
            @Override public void onCreateFailure(String e) { fail("Could not start the call."); }
        }, mc);
    }

    private void publishOffer(final String sdp) {
        final JSONObject call = new JSONObject();
        final JSONObject inbox = new JSONObject();
        try {
            call.put("from", myUser);
            call.put("fromName", myName);
            call.put("to", peerUser);
            call.put("toName", peerName);
            call.put("state", "ringing");
            call.put("offer", sdp);
            call.put("t", new JSONObject().put(".sv", "timestamp"));
            if (hasCallerPosition()) { call.put("lat", lat); call.put("lon", lon); }
            inbox.put("id", callId);
            inbox.put("from", myUser);
            inbox.put("fromName", myName);
            inbox.put("t", new JSONObject().put(".sv", "timestamp"));
        } catch (JSONException e) {
            fail("Could not start the call.");
            return;
        }
        Cloud.run(new Runnable() {
            @Override public void run() {
                final boolean ok = Cloud.put(base, "calls/" + callId, call.toString())
                        && Cloud.put(base, "inbox/" + Cloud.key(peerUser), inbox.toString());
                main.post(new Runnable() {
                    @Override public void run() {
                        if (state == State.ENDED) return;
                        if (!ok) { fail("Could not reach the online service. Check your internet."); return; }
                        signalReady = true;
                        flushLocalIce();
                        set(State.CALLING, "Calling…");
                        schedulePoll();
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------------- the one called
    /** Called person: accept, set up the audio and send the answer. */
    public void answer() {
        if (outgoing || begun || state == State.ENDED || offerSdp == null) return;
        begun = true;
        set(State.CONNECTING, "Connecting…");
        startAudio();
        try {
            createPeer();
        } catch (RuntimeException e) {
            end("Could not start the call on this phone.");
            return;
        }
        pc.setRemoteDescription(new Sdp() {
            @Override public void onSetSuccess() {
                main.post(new Runnable() {
                    @Override public void run() {
                        remoteSet = true;
                        drainRemoteIce();
                        makeAnswer();
                    }
                });
            }
            @Override public void onSetFailure(String e) { fail("Could not connect the call."); }
        }, new SessionDescription(SessionDescription.Type.OFFER, offerSdp));
    }

    private void makeAnswer() {
        if (pc == null) return;
        MediaConstraints mc = new MediaConstraints();
        mc.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pc.createAnswer(new Sdp() {
            @Override public void onCreateSuccess(final SessionDescription d) {
                if (pc == null) return;
                pc.setLocalDescription(new Sdp() {
                    @Override public void onSetSuccess() {
                        main.post(new Runnable() {
                            @Override public void run() { if (state != State.ENDED) publishAnswer(d.description); }
                        });
                    }
                    @Override public void onSetFailure(String e) { fail("Could not connect the call."); }
                }, d);
            }
            @Override public void onCreateFailure(String e) { fail("Could not connect the call."); }
        }, mc);
    }

    private void publishAnswer(final String sdp) {
        Cloud.run(new Runnable() {
            @Override public void run() {
                final boolean ok = Cloud.put(base, "calls/" + callId + "/answer", JSONObject.quote(sdp))
                        && Cloud.put(base, "calls/" + callId + "/state", "\"answered\"");
                Cloud.delete(base, "inbox/" + Cloud.key(myUser));
                main.post(new Runnable() {
                    @Override public void run() {
                        if (state == State.ENDED) return;
                        if (!ok) { fail("Could not reach the online service. Check your internet."); return; }
                        signalReady = true;
                        flushLocalIce();
                        schedulePoll();
                    }
                });
            }
        });
    }

    /** Called person: say no. */
    public void decline() {
        if (state == State.ENDED) return;
        sendState("declined");
        finish("Call declined");
    }

    /** Either side: hang up or cancel. */
    public void hangUp() {
        if (state == State.ENDED) return;
        sendState("ended");
        finish("Call ended");
    }

    // ---------------------------------------------------------------- controls
    public void setMuted(boolean m) {
        muted = m;
        if (audioTrack != null) audioTrack.setEnabled(!m);
    }

    public void setSpeaker(boolean on) {
        speaker = on;
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.setSpeakerphoneOn(on);
    }

    // ---------------------------------------------------------------- WebRTC
    private void startAudio() {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            savedAudioMode = am.getMode();
            am.setMode(AudioManager.MODE_IN_COMMUNICATION);
            am.setSpeakerphoneOn(speaker);
        }
        CallService.start(app, peerName);
    }

    private void createPeer() {
        if (!factoryReady) {
            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(app).createInitializationOptions());
            factoryReady = true;
        }
        adm = JavaAudioDeviceModule.builder(app).createAudioDeviceModule();
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(adm).createPeerConnectionFactory();
        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("audio0", audioSource);
        audioTrack.setEnabled(!muted);
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(CallConfig.iceServers());
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        pc = factory.createPeerConnection(cfg, observer);
        if (pc == null) throw new IllegalStateException("no peer connection");
        pc.addTrack(audioTrack, Collections.singletonList("stream0"));
    }

    private final PeerConnection.Observer observer = new PeerConnection.Observer() {
        public void onSignalingChange(PeerConnection.SignalingState s) { }
        public void onIceConnectionReceivingChange(boolean b) { }
        public void onIceGatheringChange(PeerConnection.IceGatheringState s) { }
        public void onIceCandidatesRemoved(IceCandidate[] c) { }
        public void onAddStream(MediaStream s) { }
        public void onRemoveStream(MediaStream s) { }
        public void onDataChannel(DataChannel d) { }
        public void onRenegotiationNeeded() { }

        public void onIceCandidate(IceCandidate c) {
            try {
                JSONObject o = new JSONObject();
                o.put("c", c.sdp);
                o.put("m", c.sdpMid == null ? "" : c.sdpMid);
                o.put("i", c.sdpMLineIndex);
                final String json = o.toString();
                main.post(new Runnable() {
                    @Override public void run() { sendIce(json); }
                });
            } catch (JSONException ignored) { }
        }

        public void onIceConnectionChange(final PeerConnection.IceConnectionState s) {
            main.post(new Runnable() {
                @Override public void run() {
                    if (state == State.ENDED) return;
                    if (s == PeerConnection.IceConnectionState.CONNECTED
                            || s == PeerConnection.IceConnectionState.COMPLETED) {
                        if (connectedAt == 0) connectedAt = System.currentTimeMillis();
                        set(State.CONNECTED, "Connected");
                    } else if (s == PeerConnection.IceConnectionState.FAILED) {
                        fail("Could not connect. Both phones need a working internet connection.");
                    } else if (s == PeerConnection.IceConnectionState.DISCONNECTED && state == State.CONNECTED) {
                        set(State.CONNECTED, "Connection is weak…");
                    }
                }
            });
        }
    };

    /** Small adapter so each call only writes the methods it cares about. */
    private abstract static class Sdp implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription d) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String e) { }
        @Override public void onSetFailure(String e) { }
    }

    // ---------------------------------------------------------------- ICE
    private void sendIce(String json) {
        if (state == State.ENDED) return;
        if (!signalReady) { pendingLocalIce.add(json); return; }
        postIce(json);
    }

    private void flushLocalIce() {
        for (String j : pendingLocalIce) postIce(j);
        pendingLocalIce.clear();
    }

    private void postIce(final String json) {
        Cloud.run(new Runnable() {
            @Override public void run() { Cloud.post(base, "calls/" + callId + "/ice_" + me, json); }
        });
    }

    private void drainRemoteIce() {
        if (pc == null) return;
        for (IceCandidate c : pendingRemoteIce) pc.addIceCandidate(c);
        pendingRemoteIce.clear();
    }

    private void addRemoteIce(JSONObject o) {
        IceCandidate c = new IceCandidate(o.optString("m", ""), o.optInt("i", 0), o.optString("c", ""));
        if (pc != null && remoteSet) pc.addIceCandidate(c);
        else pendingRemoteIce.add(c);
    }

    // ---------------------------------------------------------------- polling the other side
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            if (state == State.ENDED) return;
            Cloud.run(new Runnable() {
                @Override public void run() {
                    final String body = Cloud.get(base, "calls/" + callId);
                    main.post(new Runnable() {
                        @Override public void run() {
                            if (state == State.ENDED) return;
                            handleRemote(body);
                            if (state != State.ENDED) main.postDelayed(pollTask, POLL_MS);
                        }
                    });
                }
            });
        }
    };

    private void schedulePoll() {
        main.removeCallbacks(pollTask);
        main.postDelayed(pollTask, POLL_MS);
    }

    private void handleRemote(String body) {
        if (body == null) return;                                 // network hiccup: try again
        if (body.isEmpty() || "null".equals(body)) {
            if (++missing >= 3) finish("Call ended");             // the other side cleaned it up
            return;
        }
        missing = 0;
        JSONObject o;
        try { o = new JSONObject(body); } catch (JSONException e) { return; }
        String st = o.optString("state", "");
        if ("declined".equals(st)) { finish(outgoing ? peerName + " declined the call" : "Call ended"); return; }
        if ("ended".equals(st) || "missed".equals(st)) { finish("Call ended"); return; }

        if (outgoing && !remoteSet && "answered".equals(st) && o.has("answer") && pc != null) {
            set(State.CONNECTING, "Connecting…");
            pc.setRemoteDescription(new Sdp() {
                @Override public void onSetSuccess() {
                    main.post(new Runnable() {
                        @Override public void run() { remoteSet = true; drainRemoteIce(); }
                    });
                }
                @Override public void onSetFailure(String e) { fail("Could not connect the call."); }
            }, new SessionDescription(SessionDescription.Type.ANSWER, o.optString("answer", "")));
        }
        JSONObject ice = o.optJSONObject("ice_" + other);
        if (ice != null && begun) {
            Iterator<String> keys = ice.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject c = ice.optJSONObject(k);
                if (c != null && seenIce.add(k)) addRemoteIce(c);
            }
        }
        if (outgoing && (state == State.CALLING || state == State.RINGING)
                && System.currentTimeMillis() - startedAt > NO_ANSWER_MS) {
            sendState("missed");
            finish("No answer");
        }
    }

    // ---------------------------------------------------------------- ending
    private void sendState(final String value) {
        if (endingSent) return;
        endingSent = true;
        Cloud.run(new Runnable() {
            @Override public void run() {
                Cloud.put(base, "calls/" + callId + "/state", "\"" + value + "\"");
                Cloud.delete(base, "inbox/" + Cloud.key(outgoing ? peerUser : myUser));
            }
        });
    }

    /** Safe to call from any thread. */
    private void fail(final String msg) {
        main.post(new Runnable() {
            @Override public void run() {
                sendState("ended");
                finish(msg);
            }
        });
    }

    /** Ends the call because of something on this phone. */
    private void end(String msg) { fail(msg); }

    private void finish(String msg) {
        if (state == State.ENDED) return;
        main.removeCallbacks(pollTask);
        set(State.ENDED, msg);
        if (current == this) current = null;
        IncomingCalls.cancelNotification(app);
        CallService.stop(app);
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.setSpeakerphoneOn(false);
            am.setMode(savedAudioMode);
        }
        final PeerConnection p = pc;
        final AudioTrack t = audioTrack;
        final AudioSource src = audioSource;
        final PeerConnectionFactory f = factory;
        final JavaAudioDeviceModule d = adm;
        pc = null; audioTrack = null; audioSource = null; factory = null; adm = null;
        Thread cleanup = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (p != null) { p.close(); p.dispose(); }
                    if (t != null) t.dispose();
                    if (src != null) src.dispose();
                    if (f != null) f.dispose();
                    if (d != null) d.release();
                } catch (RuntimeException ignored) { }
                if (outgoing) {
                    try { Thread.sleep(8000); } catch (InterruptedException ignored) { }
                    Cloud.delete(base, "calls/" + callId);       // tidy up once the other side has seen it end
                }
            }
        }, "call-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }
}
