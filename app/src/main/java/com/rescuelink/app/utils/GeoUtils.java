package com.rescuelink.app.utils;

import java.util.Locale;

/** Distance helpers and the last known position of the device. */
public final class GeoUtils {
    /** Map start point used until a real location fix is available. */
    public static final double DEFAULT_LAT = 9.3654;
    public static final double DEFAULT_LON = 122.8051;

    private static double myLat = DEFAULT_LAT;
    private static double myLon = DEFAULT_LON;
    private static boolean hasFix;

    private GeoUtils() { }

    public static synchronized void setMyLocation(double lat, double lon) {
        myLat = lat;
        myLon = lon;
        hasFix = true;
    }

    public static synchronized double myLat() { return myLat; }
    public static synchronized double myLon() { return myLon; }
    public static synchronized boolean hasFix() { return hasFix; }

    /** Great-circle distance in kilometres. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double distanceFromMeKm(double lat, double lon) {
        return distanceKm(myLat(), myLon(), lat, lon);
    }

    public static String formatDistance(double km) {
        if (km < 1) return String.format(Locale.US, "%d m", Math.round(km * 100) * 10);
        return String.format(Locale.US, "%.1f km", km);
    }

    public static String formatCoords(double lat, double lon) {
        return String.format(Locale.US, "%.5f, %.5f", lat, lon);
    }

    /** Rough travel time at an average urban speed of 30 km/h. */
    public static String formatEta(double km) {
        int min = (int) Math.max(1, Math.round(km / 30.0 * 60.0));
        return min + " min";
    }
}
