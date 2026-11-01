package com.rescuelink.app.call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.CallActivity;

/** Keeps the microphone and the call alive while the person uses another app. Shows an "ongoing call" notice. */
public class CallService extends Service {
    private static final String CHANNEL = "call_ongoing";
    private static final int NOTIFICATION_ID = 43;
    private static final String EXTRA_NAME = "name";

    static void start(Context c, String peerName) {
        Intent i = new Intent(c, CallService.class);
        i.putExtra(EXTRA_NAME, peerName == null ? "" : peerName);
        try {
            ContextCompat.startForegroundService(c, i);
        } catch (RuntimeException ignored) { }      // the call itself still works for as long as the app is open
    }

    static void stop(Context c) {
        try { c.stopService(new Intent(c, CallService.class)); } catch (RuntimeException ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Calls in progress",
                    NotificationManager.IMPORTANCE_LOW));
        }
        String name = intent == null ? "" : intent.getStringExtra(EXTRA_NAME);
        Intent open = new Intent(this, CallActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentTitle("iResponse call")
                .setContentText(name == null || name.isEmpty() ? "Call in progress" : "In a call with " + name)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
