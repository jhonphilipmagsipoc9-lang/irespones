package com.rescuelink.app.net;

import android.content.Context;
import android.content.SharedPreferences;

/** Who was signed in last time, and how the person likes to call for help. Stored only on this phone. */
public final class Config {
    private static final String PREFS = "rescuelink_net";
    /** Dialled when a station has no saved number. 911 is the national emergency hotline in the Philippines. */
    public static final String FALLBACK_NUMBER = "911";

    public static final String MODE_AUTO = "auto";
    public static final String MODE_PHONE = "phone";
    public static final String MODE_SMS = "sms";
    /** Free voice call over Wi-Fi or mobile data to the nearest responder who is online. */
    public static final String MODE_APP = "app";

    /**
     * Address of the online service (a Firebase Realtime Database). Leave empty to run fully offline, or set it
     * here, or paste it in Settings > Online service. See FIREBASE_SETUP.md.
     */
    public static final String DEFAULT_CLOUD_URL = "";

    /**
     * MapTiler API key for the colourful OpenStreetMap style (free at cloud.maptiler.com). Paste it here, or in
     * Settings > Appearance > Map key. Leave empty to keep the plain OpenStreetMap tiles; nothing breaks either way.
     */
    public static final String DEFAULT_MAPTILER_KEY = "";

    private Config() { }

    public static String mapKey(Context c) {
        String k = p(c).getString("maptiler_key", DEFAULT_MAPTILER_KEY);
        return k == null ? "" : k.trim();
    }

    public static void setMapKey(Context c, String key) {
        p(c).edit().putString("maptiler_key", key == null ? "" : key.trim()).apply();
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String user(Context c) { return p(c).getString("user", ""); }
    public static boolean hasSession(Context c) { return !user(c).isEmpty(); }

    public static void saveSession(Context c, String username) {
        p(c).edit().putString("user", username).apply();
    }

    public static void clearSession(Context c) {
        p(c).edit().remove("user").apply();
    }

    /** Auto, Internet call, Phone or SMS. */
    public static String mode(Context c) {
        String m = p(c).getString("mode", MODE_AUTO);
        return MODE_PHONE.equals(m) || MODE_SMS.equals(m) || MODE_APP.equals(m) ? m : MODE_AUTO;
    }

    /** The online service address, without a trailing slash. Empty when the app runs offline only. */
    public static String cloudUrl(Context c) {
        String u = p(c).getString("cloud_url", DEFAULT_CLOUD_URL);
        u = u == null ? "" : u.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static void setCloudUrl(Context c, String url) {
        p(c).edit().putString("cloud_url", url == null ? "" : url.trim()).apply();
    }

    public static boolean cloudEnabled(Context c) {
        String u = cloudUrl(c);
        return u.startsWith("https://") || u.startsWith("http://");
    }

    public static void setMode(Context c, String mode) { p(c).edit().putString("mode", mode).apply(); }
}
