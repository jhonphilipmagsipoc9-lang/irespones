package com.rescuelink.app.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

/** One-shot access to the device's last known position. */
public final class LocationHelper {
    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    private LocationHelper() { }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Updates {@link GeoUtils} from the freshest cached location. Returns true if a position is known. */
    @SuppressLint("MissingPermission")
    public static boolean refresh(Context c) {
        if (!hasPermission(c)) return GeoUtils.hasFix();
        LocationManager lm = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return GeoUtils.hasFix();
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException ignored) { }
        if (best != null) {
            GeoUtils.setMyLocation(best.getLatitude(), best.getLongitude());
            return true;
        }
        return GeoUtils.hasFix();
    }
}
