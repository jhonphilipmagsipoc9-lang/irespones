package com.rescuelink.app.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import com.rescuelink.app.models.User;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps {@link GeoUtils} up to date with the phone's real position while any screen is listening.
 * Uses the platform GPS and network providers only (no Google services). Responders' own pin on the
 * shared map follows them too.
 */
public final class LiveLocation {
    public interface Listener {
        void onLocation(double lat, double lon);
    }

    private static final long MIN_INTERVAL_MS = 2000;
    private static final float MIN_DISTANCE_M = 3f;

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static LocationManager manager;
    private static boolean running;

    private static final LocationListener PLATFORM = new LocationListener() {
        @Override
        public void onLocationChanged(Location l) {
            GeoUtils.setMyLocation(l.getLatitude(), l.getLongitude());
            User me = SessionManager.getCurrentUser();
            if (me != null && User.ROLE_RESPONDER.equals(me.getRole())) {
                com.rescuelink.app.data.DataRepository.updatePosition(me, l.getLatitude(), l.getLongitude());
            }
            for (Listener x : LISTENERS) x.onLocation(l.getLatitude(), l.getLongitude());
        }

        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { }
        @SuppressWarnings("deprecation")
        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    };

    private LiveLocation() { }

    /** True when at least one location provider (GPS or network) is switched on in the phone's settings. */
    public static boolean isEnabled(Context c) {
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    /** Starts live updates for this listener. Needs the location permission; does nothing without it. */
    @SuppressLint("MissingPermission")
    public static synchronized void start(Context c, Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
        if (!LocationHelper.hasPermission(c)) return;
        LocationHelper.refresh(c); // show the last known spot straight away
        if (running) return;
        manager = (LocationManager) c.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) return;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (manager.getAllProviders().contains(provider)) {
                    manager.requestLocationUpdates(provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, PLATFORM,
                            Looper.getMainLooper());
                    running = true;
                }
            }
        } catch (SecurityException | IllegalArgumentException ignored) { }
    }

    /** Stops this listener. Updates end when the last one leaves, which saves battery. */
    public static synchronized void stop(Listener l) {
        if (l != null) LISTENERS.remove(l);
        if (!LISTENERS.isEmpty() || !running) return;
        try {
            if (manager != null) manager.removeUpdates(PLATFORM);
        } catch (SecurityException ignored) { }
        running = false;
    }
}
