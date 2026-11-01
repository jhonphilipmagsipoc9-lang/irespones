package com.rescuelink.app.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.IncidentManageActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.Incident;

/**
 * Plays the help alarm in a loop, with vibration, until the person taps Stop alarm (in the app or in the
 * notification). It never stops on its own. It runs as a foreground service so it keeps ringing with the app closed.
 */
public class AlarmService extends Service {
    private static final String CHANNEL = "help_alarm";
    private static final int NOTIFICATION_ID = 77;
    private static final String ACTION_STOP = "com.rescuelink.app.STOP_ALARM";
    private static volatile String ringingRef;

    private MediaPlayer player;
    private Vibrator vibrator;

    public static boolean isRinging() { return ringingRef != null; }
    public static String currentRef() { return ringingRef; }

    public static void start(Context c, String ref) {
        ringingRef = ref;
        try {
            Intent i = new Intent(c, AlarmService.class).putExtra("ref", ref);
            ContextCompat.startForegroundService(c, i);
        } catch (RuntimeException e) {
            ringingRef = null;      // the system refused to start it from the background
        }
    }

    public static void stop(Context c) {
        ringingRef = null;
        c.stopService(new Intent(c, AlarmService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            AlarmCenter.acknowledge(this, intent.getStringExtra("ref"));
            ringingRef = null;
            stopSelf();
            AlarmCenter.check(getApplicationContext());     // another waiting report rings next
            return START_NOT_STICKY;
        }
        String ref = intent == null ? null : intent.getStringExtra("ref");
        if (ref == null) { stopSelf(); return START_NOT_STICKY; }
        ringingRef = ref;
        createChannel();
        Incident inc = DataRepository.findIncident(ref);
        String what = inc == null ? "Someone needs help" : inc.displayTitle();

        Intent open = new Intent(this, IncidentManageActivity.class).putExtra("ref", ref);
        PendingIntent view = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, AlarmService.class).setAction(ACTION_STOP).putExtra("ref", ref);
        PendingIntent stop = PendingIntent.getService(this, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_my_location)
                .setContentTitle("HELP NEEDED")
                .setContentText(what)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(view)
                .addAction(0, "Stop alarm", stop)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            ringingRef = null;
            stopSelf();
            return START_NOT_STICKY;
        }
        play();
        return START_NOT_STICKY;
    }

    private void play() {
        if (player != null) return;
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
        } catch (Exception e) {
            if (player != null) { try { player.release(); } catch (Exception ignored) { } player = null; }
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 800, 600};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else vibrator.vibrate(pattern, 0);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Help alarm", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);        // the service plays the alarm itself
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        ringingRef = null;
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (vibrator != null) vibrator.cancel();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
