package com.rescuelink.app.utils;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.rescuelink.app.R;
import com.rescuelink.app.activities.LoginActivity;
import com.rescuelink.app.data.DataRepository;
import com.rescuelink.app.models.User;

/**
 * Keeps a signed-in responder's position up to date wherever they go, even with the screen off or the app in
 * the background. Admins of their service, the Command Center and fellow responders see the pin move on the map.
 * It shows a permanent notification while it runs and stops when the responder signs out.
 */
public class LocationShareService extends Service implements LocationListener {
    private static final String CHANNEL = "live_location";
    private static final int NOTIFICATION_ID = 41;

    private LocationManager manager;

    /** Starts sharing for the signed-in responder. Does nothing for other roles or without the location permission. */
    public static void startIfResponder(Context c) {
        User me = SessionManager.getCurrentUser();
        if (me == null || !User.ROLE_RESPONDER.equals(me.getRole()) || !me.isActive()) return;
        if (!LocationHelper.hasPermission(c)) return;
        try {
            ContextCompat.startForegroundService(c, new Intent(c, LocationShareService.class));
        } catch (RuntimeException ignored) { }   // the system may refuse to start it from the background
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, LocationShareService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Intent open = new Intent(this, LoginActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_my_location)
                .setContentTitle("Sharing your live location")
                .setContentText("Your team can see where you are while you are signed in.")
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        beginUpdates();
        return START_NOT_STICKY;
    }

    @SuppressLint("MissingPermission")
    private void beginUpdates() {
        if (manager != null) return;
        if (!LocationHelper.hasPermission(this)) { stopSelf(); return; }
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) { stopSelf(); return; }
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (manager.getAllProviders().contains(p)) {
                    manager.requestLocationUpdates(p, 3000, 5f, this, Looper.getMainLooper());
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            stopSelf();
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        User me = SessionManager.getCurrentUser();
        if (me == null || !User.ROLE_RESPONDER.equals(me.getRole()) || !me.isActive()) {   // signed out
            stopSelf();
            return;
        }
        GeoUtils.setMyLocation(l.getLatitude(), l.getLongitude());
        DataRepository.updatePosition(me, l.getLatitude(), l.getLongitude());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Live location sharing",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public void onDestroy() {
        if (manager != null) {
            try { manager.removeUpdates(this); } catch (SecurityException ignored) { }
            manager = null;
        }
        DataRepository.save();
        super.onDestroy();
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @SuppressWarnings("deprecation")
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
