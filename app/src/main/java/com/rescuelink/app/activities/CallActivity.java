package com.rescuelink.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.call.CallSession;
import com.rescuelink.app.utils.GeoUtils;

import java.util.Locale;

/** The call screen: ringing, in a call, and the end of a call. Works for both the caller and the person called. */
public class CallActivity extends AppCompatActivity implements CallSession.Listener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CallSession session;
    private TextView tvStatus, tvSub;
    private View rowTools, rowIncoming, btnEnd;
    private Ringtone ringtone;
    private boolean permissionAsked;
    private boolean closing;

    private final ActivityResultLauncher<String> micPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            new androidx.activity.result.ActivityResultCallback<Boolean>() {
                @Override public void onActivityResult(Boolean granted) {
                    if (session == null) return;
                    if (Boolean.TRUE.equals(granted)) proceed();
                    else {
                        Toast.makeText(CallActivity.this, "The microphone is needed for a call.", Toast.LENGTH_LONG).show();
                        if (session.outgoing) session.hangUp(); else session.decline();
                    }
                }
            });

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            if (session != null && session.state() == CallSession.State.CONNECTED && session.connectedAt() > 0) {
                long s = (System.currentTimeMillis() - session.connectedAt()) / 1000;
                String t = String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
                String m = session.message();
                tvStatus.setText("Connected".equals(m) ? "Connected \u00B7 " + t : m + " \u00B7 " + t);
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = CallSession.current();
        if (session == null) { finish(); return; }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_call);

        tvStatus = findViewById(R.id.tv_call_status);
        tvSub = findViewById(R.id.tv_call_sub);
        rowTools = findViewById(R.id.row_call_tools);
        rowIncoming = findViewById(R.id.row_call_incoming);
        btnEnd = findViewById(R.id.btn_call_end);

        String name = session.peerName == null || session.peerName.isEmpty() ? "Unknown" : session.peerName;
        ((TextView) findViewById(R.id.tv_call_name)).setText(name);
        ((TextView) findViewById(R.id.tv_call_avatar)).setText(name.substring(0, 1).toUpperCase(Locale.ROOT));
        if (!session.outgoing && session.hasCallerPosition()) {
            double km = GeoUtils.distanceFromMeKm(session.callerLat(), session.callerLon());
            tvSub.setVisibility(View.VISIBLE);
            tvSub.setText("Caller location: " + GeoUtils.formatCoords(session.callerLat(), session.callerLon())
                    + (GeoUtils.hasFix() ? " \u00B7 " + GeoUtils.formatDistance(km) + " away" : ""));
        }

        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (session != null) session.hangUp(); }
        });
        findViewById(R.id.btn_call_decline).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopRinging(); if (session != null) session.decline(); }
        });
        findViewById(R.id.btn_call_answer).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopRinging(); proceed(); }
        });
        final com.google.android.material.button.MaterialButton mute = findViewById(R.id.btn_call_mute);
        mute.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (session == null) return;
                session.setMuted(!session.isMuted());
                mute.setText(session.isMuted() ? "Muted" : "Mute");
            }
        });
        final com.google.android.material.button.MaterialButton speaker = findViewById(R.id.btn_call_speaker);
        speaker.setText(session.isSpeaker() ? "Speaker on" : "Speaker");      // calls start on the loud speaker
        speaker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (session == null) return;
                session.setSpeaker(!session.isSpeaker());
                speaker.setText(session.isSpeaker() ? "Speaker on" : "Speaker");
            }
        });

        session.setListener(this);
        if (session.outgoing) proceed();            // the caller needs no answer button; start (asking for the microphone first)
        handler.post(clock);
    }

    /** Makes sure the microphone is allowed, then starts or answers the call. */
    private void proceed() {
        if (session == null) return;
        if (hasMic()) {
            if (session.outgoing) session.begin(); else session.answer();
        } else if (!permissionAsked) {
            permissionAsked = true;
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private boolean hasMic() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onState(CallSession.State state, String message) {
        if (closing) return;
        tvStatus.setText(message);
        switch (state) {
            case RINGING:
                rowIncoming.setVisibility(View.VISIBLE);
                rowTools.setVisibility(View.GONE);
                btnEnd.setVisibility(View.GONE);
                startRinging();
                break;
            case CALLING:
            case CONNECTING:
                stopRinging();
                rowIncoming.setVisibility(View.GONE);
                rowTools.setVisibility(View.VISIBLE);
                btnEnd.setVisibility(View.VISIBLE);
                break;
            case CONNECTED:
                stopRinging();
                rowIncoming.setVisibility(View.GONE);
                rowTools.setVisibility(View.VISIBLE);
                btnEnd.setVisibility(View.VISIBLE);
                break;
            case ENDED:
                stopRinging();
                closing = true;
                rowIncoming.setVisibility(View.GONE);
                rowTools.setVisibility(View.GONE);
                btnEnd.setVisibility(View.GONE);
                handler.postDelayed(new Runnable() {
                    @Override public void run() { finish(); }
                }, 1500);
                break;
            default:
                break;
        }
    }

    private void startRinging() {
        if (ringtone != null) return;
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) {
                ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                ringtone.play();
            }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(new long[]{0, 700, 500}, 0);
        } catch (RuntimeException ignored) { }
    }

    private void stopRinging() {
        try {
            if (ringtone != null) { ringtone.stop(); ringtone = null; }
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.cancel();
        } catch (RuntimeException ignored) { }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopRinging();
        if (session != null) session.setListener(null);
        super.onDestroy();
    }

    /** Back does not hang up by accident: a call keeps running, use End call. */
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
